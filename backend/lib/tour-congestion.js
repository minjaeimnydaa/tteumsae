import { integerEnv, requiredEnv } from "./env.js";
import { fetchWithTimeout, NETWORK_TIMEOUT_MS } from "./fetch-policy.js";
import {
  createProviderResponseError,
  ProviderResponseError,
  trackProviderCall
} from "./provider-usage.js";

const congestionUrl =
  "https://apis.data.go.kr/B551011/TatsCnctrRateService/tatsCnctrRatedList";
const OFFICIAL_DEVELOPMENT_DAILY_QUOTA = 1_000;

function text(value) {
  return String(value ?? "").trim();
}

function isoDate(value) {
  const digits = text(value).replace(/[^0-9]/g, "");
  if (digits.length !== 8) return null;
  const result = `${digits.slice(0, 4)}-${digits.slice(4, 6)}-${digits.slice(6, 8)}`;
  const date = new Date(`${result}T00:00:00.000Z`);
  return Number.isNaN(date.getTime()) || date.toISOString().slice(0, 10) !== result
    ? null
    : result;
}

export function normalizeCongestionPlaceName(value) {
  return text(value).normalize("NFKC").replace(/\s+/g, "").toLocaleLowerCase("ko-KR");
}

export function congestionLevel(rate) {
  if (!Number.isFinite(rate) || rate < 0 || rate > 100) return null;
  if (rate < 40) return { code: "LOW", label: "한산 예상" };
  if (rate < 70) return { code: "MODERATE", label: "보통 예상" };
  return { code: "HIGH", label: "혼잡 예상" };
}

export function normalizeCongestionItem(item, fetchedAt) {
  const sourceName = text(item?.tAtsNm);
  const forecastDate = isoDate(item?.baseYmd);
  const concentrationRate = Number(item?.cnctrRate);
  const level = congestionLevel(concentrationRate);
  if (!sourceName || !forecastDate || !level) return null;
  return {
    source_name: sourceName.slice(0, 300),
    normalized_name: normalizeCongestionPlaceName(sourceName).slice(0, 300),
    area_code: text(item?.areaCd).slice(0, 10),
    area_name: text(item?.areaNm).slice(0, 100) || null,
    sigungu_code: text(item?.signguCd).slice(0, 10),
    sigungu_name: text(item?.signguNm).slice(0, 100) || null,
    forecast_date: forecastDate,
    concentration_rate: concentrationRate,
    level: level.code,
    fetched_at: fetchedAt
  };
}

export function prepareCongestionRows(items, places, fetchedAt) {
  const placesByName = new Map();
  for (const place of places) {
    const name = normalizeCongestionPlaceName(place.name);
    if (!name) continue;
    const matches = placesByName.get(name) ?? [];
    matches.push(place);
    placesByName.set(name, matches);
  }

  return items.flatMap((item) => {
    const row = normalizeCongestionItem(item, fetchedAt);
    if (!row) return [];
    const matches = placesByName.get(row.normalized_name) ?? [];
    return [{
      ...row,
      content_id: matches.length === 1 ? String(matches[0].content_id) : null,
      match_status: matches.length === 1
        ? "MATCHED"
        : matches.length > 1
          ? "AMBIGUOUS"
          : "UNMATCHED"
    }];
  });
}

export async function fetchTourCongestionPage(
  pageNo = 1,
  numOfRows = 1_000,
  {
    areaCode = "51",
    sigunguCode = "51150",
    apiKey = requiredEnv("TOUR_CONGESTION_API_SERVICE_KEY"),
    signal,
    fetchImpl = fetch,
    usageTracker = trackProviderCall,
    now
  } = {}
) {
  const query = new URLSearchParams({
    serviceKey: apiKey,
    MobileOS: "ETC",
    MobileApp: "Tteumsae",
    _type: "json",
    areaCd: String(areaCode),
    signguCd: String(sigunguCode),
    pageNo: String(Math.max(1, pageNo)),
    numOfRows: String(Math.min(Math.max(1, numOfRows), 1_000))
  });
  const budgetLimit = Math.min(
    integerEnv("TOUR_CONGESTION_DAILY_BUDGET", 100),
    OFFICIAL_DEVELOPMENT_DAILY_QUOTA
  );

  return usageTracker({
    provider: "TOUR_API",
    operation: "tatsCnctrRatedList",
    budgetLimit,
    signal,
    now,
    call: async () => {
      const response = await fetchWithTimeout(`${congestionUrl}?${query}`, {
        headers: { accept: "application/json" }
      }, {
        provider: "TOUR_API_CONGESTION",
        timeoutMs: NETWORK_TIMEOUT_MS.TOUR_API,
        signal,
        fetchImpl
      });
      if (!response.ok) {
        throw await createProviderResponseError(response, "TOUR_API_CONGESTION", { now });
      }
      const payload = await response.json();
      const root = payload?.response ?? payload;
      const resultCode = root?.header?.resultCode;
      if (resultCode != null && String(resultCode) !== "0000") {
        throw new ProviderResponseError(
          "TOUR_API_CONGESTION",
          200,
          String(resultCode)
        );
      }
      const body = root?.body ?? {};
      const rawItems = body?.items?.item ?? [];
      const items = Array.isArray(rawItems) ? rawItems : rawItems ? [rawItems] : [];
      return {
        pageNo: Number(body.pageNo) || pageNo,
        numOfRows: Number(body.numOfRows) || numOfRows,
        totalCount: Number(body.totalCount) || 0,
        items
      };
    }
  });
}

export function publicCongestionForecast(row) {
  if (!row) return null;
  const rate = Number(row.concentration_rate);
  const level = congestionLevel(rate);
  if (!level) return null;
  return {
    forecast_date: text(row.forecast_date),
    concentration_rate: rate,
    level: level.code,
    label: level.label,
    fetched_at: text(row.fetched_at),
    source: "한국관광공사 관광지 집중률 예측",
    basis: "해당 관광지의 과거 최고 혼잡 시기 대비 상대 예측값"
  };
}
