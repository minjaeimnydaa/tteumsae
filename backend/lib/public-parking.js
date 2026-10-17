import { requiredEnv } from "./env.js";
import { fetchWithTimeout, NETWORK_TIMEOUT_MS } from "./fetch-policy.js";
import {
  createProviderResponseError,
  ProviderResponseError,
  trackProviderCall
} from "./provider-usage.js";

const endpoint = "https://api.data.go.kr/openapi/tn_pubr_prkplce_info_api";

function text(value) {
  const normalized = value == null ? "" : String(value).trim();
  return normalized || null;
}

function integer(value) {
  const normalized = text(value);
  if (!normalized || !/^\d+$/.test(normalized)) return null;
  const parsed = Number.parseInt(normalized, 10);
  return Number.isSafeInteger(parsed) ? parsed : null;
}

function coordinate(value, minimum, maximum) {
  const normalized = text(value);
  if (!normalized) return null;
  const parsed = Number(normalized);
  return Number.isFinite(parsed) && parsed >= minimum && parsed <= maximum
    ? parsed
    : null;
}

function isoDate(value) {
  const normalized = text(value);
  if (!normalized || !/^\d{4}-\d{2}-\d{2}$/.test(normalized)) return null;
  const date = new Date(`${normalized}T00:00:00.000Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().startsWith(normalized)
    ? normalized
    : null;
}

function yesNo(value) {
  const normalized = text(value)?.toUpperCase();
  if (["Y", "YES", "예", "유", "있음"].includes(normalized)) return true;
  if (["N", "NO", "아니오", "무", "없음"].includes(normalized)) return false;
  return null;
}

function isGangwonAddress(value) {
  return /^(강원특별자치도|강원도)(\s|$)/.test(text(value) ?? "");
}

export function mapPublicParkingLot(item, syncedAt = new Date().toISOString()) {
  const parkingNo = text(item?.prkplceNo);
  const name = text(item?.prkplceNm);
  const institutionCode = text(item?.insttCode) ??
    text(item?.instt_code) ??
    text(item?.institutionNm);
  const address = text(item?.rdnmadr) ?? text(item?.lnmadr);
  const latitude = coordinate(item?.latitude, -90, 90);
  const longitude = coordinate(item?.longitude, -180, 180);
  if (
    !parkingNo ||
    !name ||
    !institutionCode ||
    text(item?.prkplceSe) !== "공영" ||
    !isGangwonAddress(address) ||
    latitude == null ||
    longitude == null
  ) {
    return null;
  }

  return {
    source_id: `${institutionCode}:${parkingNo}`,
    parking_no: parkingNo,
    name,
    parking_type: text(item.prkplceType),
    address,
    capacity: integer(item.prkcmprt),
    operation_days: text(item.operDay),
    weekday_open: text(item.weekdayOperOpenHhmm),
    weekday_close: text(item.weekdayOperColseHhmm),
    saturday_open: text(item.satOperOperOpenHhmm),
    saturday_close: text(item.satOperCloseHhmm),
    holiday_open: text(item.holidayOperOpenHhmm),
    holiday_close: text(item.holidayCloseOpenHhmm),
    fee_info: text(item.parkingchrgeInfo),
    basic_minutes: integer(item.basicTime),
    basic_fee_won: integer(item.basicCharge),
    additional_minutes: integer(item.addUnitTime),
    additional_fee_won: integer(item.addUnitCharge),
    daily_fee_won: integer(item.dayCmmtkt),
    payment_method: text(item.metpay),
    notes: text(item.spcmnt),
    operator_name: text(item.institutionNm),
    phone: text(item.phoneNumber),
    latitude,
    longitude,
    accessible_parking: yesNo(item.pwdbsPpkZoneYn),
    reference_date: isoDate(item.referenceDate),
    synced_at: syncedAt,
    raw: item
  };
}

function parseItems(body) {
  const raw = body?.items?.item ?? body?.items ?? [];
  return Array.isArray(raw) ? raw : raw ? [raw] : [];
}

export async function fetchPublicParkingPage(
  pageNo,
  numOfRows = 100,
  { signal, fetchImpl = fetch, usageTracker = trackProviderCall, now } = {}
) {
  const query = new URLSearchParams({
    serviceKey: requiredEnv("PUBLIC_PARKING_API_SERVICE_KEY"),
    pageNo: String(pageNo),
    numOfRows: String(numOfRows),
    type: "json",
    prkplceSe: "공영",
    instt_nm: "강원특별자치도 강릉시"
  });
  const payload = await usageTracker({
    provider: "PUBLIC_DATA",
    operation: "parking/list",
    budgetLimit: 500,
    signal,
    now,
    call: async () => {
      const response = await fetchWithTimeout(
        `${endpoint}?${query}`,
        { headers: { accept: "application/json" } },
        {
          provider: "PUBLIC_DATA",
          timeoutMs: NETWORK_TIMEOUT_MS.TOUR_API,
          signal,
          fetchImpl
        }
      );
      if (!response.ok) {
        throw await createProviderResponseError(response, "PUBLIC_DATA", { now });
      }
      const result = await response.json();
      const root = result?.response ?? result;
      const resultCode = String(root?.header?.resultCode ?? "");
      if (!["00", "0000"].includes(resultCode)) {
        throw new ProviderResponseError("PUBLIC_DATA", 200, resultCode || "UNKNOWN");
      }
      return root;
    }
  });
  const body = payload.body ?? {};
  const items = parseItems(body);
  const syncedAt = new Date().toISOString();
  return {
    pageNo: Number(body.pageNo) || pageNo,
    numOfRows: Number(body.numOfRows) || numOfRows,
    totalCount: Number(body.totalCount) || 0,
    rawCount: items.length,
    rows: items.map((item) => mapPublicParkingLot(item, syncedAt)).filter(Boolean)
  };
}

function money(value) {
  return Number.isInteger(value) && value >= 0
    ? new Intl.NumberFormat("ko-KR").format(value)
    : null;
}

function feeSummary(row) {
  const feeInfo = text(row.fee_info);
  if (feeInfo === "무료") return "무료";
  const basicFee = money(row.basic_fee_won);
  const basic = row.basic_minutes != null && basicFee != null
    ? `기본 ${row.basic_minutes}분 ${basicFee}원`
    : null;
  return [feeInfo, basic].filter(Boolean).join(" · ") || null;
}

function operationSummary(row) {
  const weekday = row.weekday_open && row.weekday_close
    ? `평일 ${row.weekday_open}~${row.weekday_close}`
    : null;
  return [text(row.operation_days), weekday].filter(Boolean).join(" · ") || null;
}

export function publicNearbyParkingLot(row) {
  return {
    parking_id: row.source_id,
    name: row.name,
    parking_type: row.parking_type ?? null,
    address: row.address ?? null,
    capacity: row.capacity ?? null,
    distance_meters: Math.max(0, Math.round(Number(row.distance_meters) || 0)),
    distance_basis: "STRAIGHT_LINE",
    fee_summary: feeSummary(row),
    operation_summary: operationSummary(row),
    accessible_parking: row.accessible_parking ?? null,
    phone: row.phone ?? null,
    reference_date: row.reference_date ?? null,
    source: "전국주차장정보표준데이터"
  };
}
