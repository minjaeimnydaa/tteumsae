import { requiredEnv } from "./env.js";
import { fetchWithTimeout, NETWORK_TIMEOUT_MS } from "./fetch-policy.js";
import {
  createProviderResponseError,
  ProviderResponseError,
  trackProviderCall
} from "./provider-usage.js";

const serviceBaseUrl = "https://apis.data.go.kr/B551011/KorWithService2";

const accessibilityFields = [
  ["parking", "장애인 주차"],
  ["route", "접근로"],
  ["publictransport", "대중교통"],
  ["ticketoffice", "매표소"],
  ["promotion", "홍보물"],
  ["wheelchair", "휠체어"],
  ["exit", "출입구"],
  ["elevator", "엘리베이터"],
  ["restroom", "장애인 화장실"],
  ["auditorium", "관람석"],
  ["room", "객실"],
  ["handicapetc", "기타 지체장애 안내"],
  ["braileblock", "점자블록"],
  ["helpdog", "보조견"],
  ["guidehuman", "안내요원"],
  ["audioguide", "음성 안내"],
  ["bigprint", "큰활자 안내"],
  ["brailepromotion", "점자 안내"],
  ["guidesystem", "유도 안내"],
  ["blindhandicapetc", "기타 시각장애 안내"],
  ["signguide", "수어 안내"],
  ["videoguide", "자막·영상 안내"],
  ["hearingroom", "청각장애인 객실"],
  ["hearinghandicapetc", "기타 청각장애 안내"],
  ["stroller", "유모차"],
  ["lactationroom", "수유실"],
  ["babysparechair", "유아용 의자"],
  ["infantsfamilyetc", "기타 영유아 동반 안내"]
];

function numeric(value, fallback = 0) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function cleanText(value) {
  if (value == null) return null;
  const text = String(value)
    .replace(/<br\s*\/?>/gi, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/gi, " ")
    .replace(/&amp;/gi, "&")
    .replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">")
    .replace(/&quot;/gi, "\"")
    .replace(/\s+/g, " ")
    .trim();
  return text || null;
}

function parsePayload(payload, operation) {
  const header = payload?.response?.header;
  if (!header || String(header.resultCode) !== "0000") {
    throw new ProviderResponseError(
      "TOUR_API",
      200,
      String(header?.resultCode ?? "UNKNOWN")
    );
  }
  const body = payload.response.body ?? {};
  const rawItems = body.items?.item ?? [];
  return {
    body,
    items: Array.isArray(rawItems) ? rawItems : rawItems ? [rawItems] : [],
    operation
  };
}

async function fetchAccessibilityPayload(
  path,
  parameters,
  { signal, fetchImpl = fetch, usageTracker = trackProviderCall, now } = {}
) {
  const query = new URLSearchParams({
    serviceKey: requiredEnv("TOUR_API_SERVICE_KEY"),
    MobileOS: "ETC",
    MobileApp: "Tteumsae",
    _type: "json",
    ...parameters
  });
  const operation = `accessibility/${path}`;
  const payload = await usageTracker({
    provider: "TOUR_API",
    operation,
    budgetLimit: null,
    signal,
    now,
    call: async () => {
      const response = await fetchWithTimeout(
        `${serviceBaseUrl}/${path}?${query}`,
        { headers: { accept: "application/json" } },
        {
          provider: "TOUR_API",
          timeoutMs: NETWORK_TIMEOUT_MS.TOUR_API,
          signal,
          fetchImpl
        }
      );
      if (!response.ok) {
        throw await createProviderResponseError(response, "TOUR_API", { now });
      }
      const result = await response.json();
      const resultCode = result?.response?.header?.resultCode;
      if (resultCode != null && String(resultCode) !== "0000") {
        throw new ProviderResponseError("TOUR_API", 200, String(resultCode));
      }
      return result;
    }
  });
  return parsePayload(payload, operation);
}

export async function fetchTourAccessibilityPage(
  pageNo,
  numOfRows = 20,
  options = {}
) {
  const { body, items } = await fetchAccessibilityPayload("areaBasedSyncList2", {
    areaCode: "32",
    showflag: "1",
    arrange: "C",
    pageNo: String(pageNo),
    numOfRows: String(numOfRows)
  }, options);
  const activeItems = items.filter((item) => String(item?.showflag ?? "") === "1");
  return {
    pageNo: numeric(body.pageNo, pageNo),
    numOfRows: numeric(body.numOfRows, numOfRows),
    totalCount: numeric(body.totalCount),
    rawCount: items.length,
    items: activeItems
  };
}

export async function fetchTourAccessibilityDetail(contentId, options = {}) {
  const { items } = await fetchAccessibilityPayload("detailWithTour2", {
    contentId: String(contentId),
    pageNo: "1",
    numOfRows: "10"
  }, options);
  return items[0] ?? null;
}

export function normalizeTourAccessibility({ detail, syncedAt }) {
  const raw = detail ?? null;
  const items = detail == null
    ? []
    : accessibilityFields.flatMap(([field, title]) => {
        const description = cleanText(detail[field]);
        return description ? [{ title, description }] : [];
      });
  return { raw, items, syncedAt };
}
