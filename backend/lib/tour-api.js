import { requiredEnv } from "./env.js";
import { fetchWithTimeout, NETWORK_TIMEOUT_MS } from "./fetch-policy.js";
import {
  createProviderResponseError,
  ProviderResponseError,
  trackProviderCall
} from "./provider-usage.js";

const endpoint = "https://apis.data.go.kr/B551011/KorService2/areaBasedList2";
const serviceBaseUrl = "https://apis.data.go.kr/B551011/KorService2";

const typeDefaults = {
  12: { category: "ATTRACTION", stayMinutes: 60 },
  14: { category: "CULTURE", stayMinutes: 90 },
  15: { category: "FESTIVAL", stayMinutes: 60 },
  28: { category: "LEISURE", stayMinutes: 60 },
  38: { category: "SHOPPING", stayMinutes: 40 },
  39: { category: "RESTAURANT", stayMinutes: 40 }
};
const CAFE_CAT3_CODES = new Set(["A05020900"]);

function numeric(value, fallback = 0) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

async function fetchTourPayload(
  url,
  operation,
  { signal, fetchImpl = fetch, usageTracker = trackProviderCall, now } = {}
) {
  return usageTracker({
    provider: "TOUR_API",
    operation,
    budgetLimit: null,
    signal,
    now,
    call: async () => {
      const response = await fetchWithTimeout(url, {
        headers: { accept: "application/json" }
      }, {
        provider: "TOUR_API",
        timeoutMs: NETWORK_TIMEOUT_MS.TOUR_API,
        signal,
        fetchImpl
      });
      if (!response.ok) {
        throw await createProviderResponseError(response, "TOUR_API", { now });
      }
      const payload = await response.json();
      const resultCode = payload?.response?.header?.resultCode;
      if (resultCode != null && String(resultCode) !== "0000") {
        throw new ProviderResponseError("TOUR_API", 200, String(resultCode));
      }
      return payload;
    }
  });
}

export function mapTourItem(item, syncedAt = new Date().toISOString()) {
  const contentTypeId = numeric(item.contenttypeid);
  const defaults = typeDefaults[contentTypeId];
  if (!defaults || !item.contentid || !item.title) return null;

  const latitude = numeric(item.mapy, Number.NaN);
  const longitude = numeric(item.mapx, Number.NaN);
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null;

  const cat1 = String(item.cat1 ?? "").trim() || null;
  const cat2 = String(item.cat2 ?? "").trim() || null;
  const cat3 = String(item.cat3 ?? "").trim() || null;
  const category =
    contentTypeId === 39 && CAFE_CAT3_CODES.has(cat3) ? "CAFE" : defaults.category;

  return {
    content_id: String(item.contentid),
    source: "TOUR_API",
    name: String(item.title).trim(),
    category,
    content_type_id: contentTypeId,
    area_code: numeric(item.areacode, 32),
    sigungu_code: item.sigungucode ? numeric(item.sigungucode) : null,
    latitude,
    longitude,
    address: [item.addr1, item.addr2].filter(Boolean).join(" ") || null,
    image_url: item.firstimage || item.firstimage2 || null,
    tel: item.tel || null,
    default_stay_minutes: defaults.stayMinutes,
    cat1,
    cat2,
    cat3,
    is_active: true,
    source_modified_at: item.modifiedtime || null,
    synced_at: syncedAt,
    raw: item
  };
}

export async function fetchTourPage(
  pageNo,
  numOfRows = 100,
  { signal, fetchImpl = fetch, usageTracker = trackProviderCall, now } = {}
) {
  const query = new URLSearchParams({
    serviceKey: requiredEnv("TOUR_API_SERVICE_KEY"),
    MobileOS: "ETC",
    MobileApp: "Tteumsae",
    _type: "json",
    arrange: "C",
    areaCode: "32",
    pageNo: String(pageNo),
    numOfRows: String(numOfRows)
  });
  const payload = await fetchTourPayload(
    `${endpoint}?${query}`,
    "areaBasedList2",
    { signal, fetchImpl, usageTracker, now }
  );
  const { body, items } = parseTourListPayload(payload, "areaBasedList2");
  const syncedAt = new Date().toISOString();

  return {
    pageNo: numeric(body.pageNo, pageNo),
    numOfRows: numeric(body.numOfRows, numOfRows),
    totalCount: numeric(body.totalCount),
    rawCount: items.length,
    places: items.map((item) => mapTourItem(item, syncedAt)).filter(Boolean)
  };
}

async function fetchTourDetail(
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
  const payload = await fetchTourPayload(
    `${serviceBaseUrl}/${path}?${query}`,
    path,
    { signal, fetchImpl, usageTracker, now }
  );
  const header = payload?.response?.header;
  if (!header || String(header.resultCode) !== "0000") {
    throw new Error(
      `TourAPI ${path} error: ${header?.resultCode ?? "UNKNOWN"} ${header?.resultMsg ?? ""}`.trim()
    );
  }
  const rawItems = payload.response.body?.items?.item ?? [];
  return Array.isArray(rawItems) ? rawItems : rawItems ? [rawItems] : [];
}

export async function fetchTourIntro(
  contentId,
  contentTypeId,
  { signal, fetchImpl = fetch, usageTracker = trackProviderCall, now } = {}
) {
  const items = await fetchTourDetail("detailIntro2", {
    contentId: String(contentId),
    contentTypeId: String(contentTypeId),
    numOfRows: "10",
    pageNo: "1"
  }, { signal, fetchImpl, usageTracker, now });
  return items[0] ?? null;
}

export async function fetchTourCommon(
  contentId,
  { signal, fetchImpl = fetch, usageTracker = trackProviderCall, now } = {}
) {
  const items = await fetchTourDetail("detailCommon2", {
    contentId: String(contentId),
    numOfRows: "10",
    pageNo: "1"
  }, { signal, fetchImpl, usageTracker, now });
  return items[0] ?? null;
}

export async function fetchTourInfo(
  contentId,
  contentTypeId,
  { signal, fetchImpl = fetch, usageTracker = trackProviderCall, now } = {}
) {
  return fetchTourDetail("detailInfo2", {
    contentId: String(contentId),
    contentTypeId: String(contentTypeId),
    numOfRows: "20",
    pageNo: "1"
  }, { signal, fetchImpl, usageTracker, now });
}

export async function fetchTourImages(
  contentId,
  { signal, fetchImpl = fetch, usageTracker = trackProviderCall, now } = {}
) {
  return fetchTourDetail("detailImage2", {
    contentId: String(contentId),
    imageYN: "Y",
    subImageYN: "Y",
    numOfRows: "20",
    pageNo: "1"
  }, { signal, fetchImpl, usageTracker, now });
}

export async function fetchPetTourDetail(
  contentId,
  { signal, fetchImpl = fetch, usageTracker = trackProviderCall, now } = {}
) {
  const items = await fetchTourDetail("detailPetTour2", {
    contentId: String(contentId),
    numOfRows: "10",
    pageNo: "1"
  }, { signal, fetchImpl, usageTracker, now });
  return items[0] ?? null;
}

const parkingFields = [
  "parking",
  "parkingculture",
  "parkingfood",
  "parkingleports",
  "parkingshopping"
];
const childFields = [
  "kidsfacility",
  "chkbabycarriage",
  "chkbabycarriageculture",
  "chkbabycarriageleports",
  "chkbabycarriageshopping",
  "chkbabycarriagefood"
];
const openingHoursFields = [
  "usetime",
  "usetimeculture",
  "playtime",
  "usetimeleports",
  "opentime",
  "opentimefood"
];
const closedDaysFields = [
  "restdate",
  "restdateculture",
  "restdateleports",
  "restdateshopping",
  "restdatefood"
];
const negativeTerms = ["없음", "불가", "불가능", "미제공", "해당없음"];

function cleanText(value) {
  return String(value ?? "")
    .replace(/<[^>]+>/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function firstText(item, fields) {
  for (const field of fields) {
    const value = cleanText(item?.[field]);
    if (value) return value;
  }
  return null;
}

function hasPositiveValue(item, fields) {
  return fields.some((field) => {
    const value = String(item?.[field] ?? "").trim();
    return value && !negativeTerms.some((term) => value.includes(term));
  });
}

function introTags(intro) {
  const tags = [];
  if (hasPositiveValue(intro, parkingFields)) tags.push("주차 가능");
  if (hasPositiveValue(intro, childFields)) tags.push("아이 동반");
  return tags;
}

function parseTourDate(value) {
  const text = String(value ?? "").trim();
  if (!/^\d{8}$/.test(text)) return null;
  const year = Number(text.slice(0, 4));
  const month = Number(text.slice(4, 6));
  const day = Number(text.slice(6, 8));
  const date = new Date(Date.UTC(year, month - 1, day));
  if (
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() !== month - 1 ||
    date.getUTCDate() !== day
  ) {
    return null;
  }
  return `${text.slice(0, 4)}-${text.slice(4, 6)}-${text.slice(6, 8)}`;
}

function normalizeHomepage(value) {
  const raw = String(value ?? "").trim();
  if (!raw) return null;
  const anchorHref = raw.match(/href\s*=\s*(["'])(.*?)\1/i)?.[2];
  const candidate = (anchorHref ?? cleanText(raw)).replace(/&amp;/gi, "&").trim();
  try {
    const url = new URL(candidate);
    return url.protocol === "http:" || url.protocol === "https:" ? candidate : null;
  } catch {
    return null;
  }
}

export function normalizeTourIntro({ contentTypeId, intro, syncedAt }) {
  return {
    tags: introTags(intro),
    openingHours: firstText(intro, openingHoursFields),
    closedDays: firstText(intro, closedDaysFields),
    eventStartDate: parseTourDate(intro?.eventstartdate),
    eventEndDate: parseTourDate(intro?.eventenddate),
    intro: intro ?? null,
    syncedAt
  };
}

export async function fetchTourSyncPage({
  pageNo = 1,
  numOfRows = 100,
  modifiedTime,
  signal,
  fetchImpl = fetch,
  usageTracker = trackProviderCall,
  now
} = {}) {
  const query = new URLSearchParams({
    serviceKey: requiredEnv("TOUR_API_SERVICE_KEY"),
    MobileOS: "ETC",
    MobileApp: "Tteumsae",
    _type: "json",
    arrange: "C",
    areaCode: "32",
    lDongRegnCd: "51",
    pageNo: String(pageNo),
    numOfRows: String(numOfRows)
  });
  if (modifiedTime) query.set("modifiedtime", String(modifiedTime));

  const payload = await fetchTourPayload(
    `${serviceBaseUrl}/areaBasedSyncList2?${query}`,
    "areaBasedSyncList2",
    { signal, fetchImpl, usageTracker, now }
  );
  const { body, items } = parseTourListPayload(payload, "areaBasedSyncList2");
  const syncedAt = new Date().toISOString();
  return {
    pageNo: numeric(body.pageNo, pageNo),
    numOfRows: numeric(body.numOfRows, numOfRows),
    totalCount: numeric(body.totalCount),
    rawCount: items.length,
    places: items.map((item) => mapTourSyncItem(item, syncedAt)).filter(Boolean)
  };
}

export function mapTourSyncItem(item, syncedAt = new Date().toISOString()) {
  const contentId = String(item?.contentid ?? "").trim();
  const showFlag = String(item?.showflag ?? "").trim();
  if (!contentId || !["0", "1"].includes(showFlag)) return null;
  if (showFlag === "0") {
    return {
      content_id: contentId,
      is_active: false,
      source_modified_at: item.modifiedtime || null,
      raw: item
    };
  }
  return mapTourItem(item, syncedAt);
}

function parseTourListPayload(payload, operation) {
  const header = payload?.response?.header;
  if (!header || String(header.resultCode) !== "0000") {
    throw new Error(
      `TourAPI ${operation} error: ${header?.resultCode ?? "UNKNOWN"} ${header?.resultMsg ?? ""}`.trim()
    );
  }
  const body = payload.response.body ?? {};
  const rawItems = body.items?.item ?? [];
  return {
    body,
    items: Array.isArray(rawItems) ? rawItems : rawItems ? [rawItems] : []
  };
}

export function normalizeTourCommon({ common, syncedAt }) {
  return {
    overview: firstText(common, ["overview"]),
    homepageUrl: normalizeHomepage(common?.homepage),
    common: common ?? null,
    syncedAt
  };
}

export function normalizeTourInfo({ items = [], syncedAt }) {
  const rawItems = Array.isArray(items) ? items : [];
  const seen = new Set();
  const infoItems = [];

  for (const item of rawItems) {
    const title = firstText(item, ["infoname", "subname", "roomtitle"]);
    const description = firstText(item, ["infotext", "subdetailoverview", "roomintro"]);
    if (!title || !description) continue;
    const key = `${title}\u0000${description}`;
    if (seen.has(key)) continue;
    seen.add(key);
    infoItems.push({ title, description });
  }

  return { infoItems, rawItems, syncedAt };
}

const copyrightLabels = {
  Type1: "공공누리 제1유형",
  Type3: "공공누리 제3유형"
};

function normalizeImageAttributions(images) {
  const seen = new Set();
  const attributions = [];
  for (const image of images) {
    const imageUrl = normalizeHomepage(image?.originimgurl);
    const thumbnailUrl = normalizeHomepage(image?.smallimageurl);
    if (!imageUrl && !thumbnailUrl) continue;
    const key = `${imageUrl ?? ""}\u0000${thumbnailUrl ?? ""}`;
    if (seen.has(key)) continue;
    seen.add(key);
    const copyrightType = cleanText(image?.cpyrhtDivCd) || null;
    attributions.push({
      image_url: imageUrl,
      thumbnail_url: thumbnailUrl,
      name: cleanText(image?.imgname) || null,
      copyright_type: copyrightType,
      copyright_label: copyrightLabels[copyrightType] ?? null
    });
  }
  return attributions;
}

export function normalizeTourMedia({
  contentTypeId,
  intro,
  images = [],
  pet,
  syncedAt
}) {
  const tags = introTags(intro);
  if (hasPositiveValue(pet, ["acmpyTypeCd"])) tags.push("반려동물 동반");
  if (Number(contentTypeId) === 14) tags.push("실내 활동");

  const imageItems = Array.isArray(images) ? images : [];
  const imageUrls = imageItems
    .flatMap((image) => [image.originimgurl, image.smallimageurl])
    .filter(Boolean)
    .filter((url, index, values) => values.indexOf(url) === index);

  return {
    tags,
    imageUrls,
    imageAttributions: normalizeImageAttributions(imageItems),
    images: imageItems,
    pet: pet ?? null,
    syncedAt
  };
}

export function normalizeTourEnrichment({
  contentTypeId,
  intro,
  images = [],
  pet
}) {
  const syncedAt = new Date().toISOString();
  const normalizedIntro = normalizeTourIntro({ contentTypeId, intro, syncedAt });
  const normalizedMedia = normalizeTourMedia({ contentTypeId, intro, images, pet, syncedAt });

  return {
    tags: normalizedMedia.tags,
    imageUrls: normalizedMedia.imageUrls,
    imageAttributions: normalizedMedia.imageAttributions,
    openingHours: normalizedIntro.openingHours,
    closedDays: normalizedIntro.closedDays,
    intro: normalizedIntro.intro,
    pet: normalizedMedia.pet,
    enrichedAt: syncedAt
  };
}
