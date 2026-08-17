import { requiredEnv } from "./env.js";

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

function numeric(value, fallback = 0) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

export function mapTourItem(item, syncedAt = new Date().toISOString()) {
  const contentTypeId = numeric(item.contenttypeid);
  const defaults = typeDefaults[contentTypeId];
  if (!defaults || !item.contentid || !item.title) return null;

  const latitude = numeric(item.mapy, Number.NaN);
  const longitude = numeric(item.mapx, Number.NaN);
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null;

  return {
    content_id: String(item.contentid),
    source: "TOUR_API",
    name: String(item.title).trim(),
    category: defaults.category,
    content_type_id: contentTypeId,
    area_code: numeric(item.areacode, 32),
    sigungu_code: item.sigungucode ? numeric(item.sigungucode) : null,
    latitude,
    longitude,
    address: [item.addr1, item.addr2].filter(Boolean).join(" ") || null,
    image_url: item.firstimage || item.firstimage2 || null,
    tel: item.tel || null,
    default_stay_minutes: defaults.stayMinutes,
    is_active: true,
    source_modified_at: item.modifiedtime || null,
    synced_at: syncedAt,
    raw: item
  };
}

export async function fetchTourPage(pageNo, numOfRows = 100) {
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
  const response = await fetch(`${endpoint}?${query}`, {
    headers: { accept: "application/json" }
  });
  if (!response.ok) {
    throw new Error(`TourAPI request failed (${response.status})`);
  }

  const payload = await response.json();
  const header = payload?.response?.header;
  if (!header || String(header.resultCode) !== "0000") {
    throw new Error(
      `TourAPI error: ${header?.resultCode ?? "UNKNOWN"} ${header?.resultMsg ?? ""}`.trim()
    );
  }

  const body = payload.response.body ?? {};
  const rawItems = body.items?.item ?? [];
  const items = Array.isArray(rawItems) ? rawItems : rawItems ? [rawItems] : [];
  const syncedAt = new Date().toISOString();

  return {
    pageNo: numeric(body.pageNo, pageNo),
    numOfRows: numeric(body.numOfRows, numOfRows),
    totalCount: numeric(body.totalCount),
    rawCount: items.length,
    places: items.map((item) => mapTourItem(item, syncedAt)).filter(Boolean)
  };
}

async function fetchTourDetail(path, parameters) {
  const query = new URLSearchParams({
    serviceKey: requiredEnv("TOUR_API_SERVICE_KEY"),
    MobileOS: "ETC",
    MobileApp: "Tteumsae",
    _type: "json",
    ...parameters
  });
  const response = await fetch(`${serviceBaseUrl}/${path}?${query}`, {
    headers: { accept: "application/json" }
  });
  if (!response.ok) {
    throw new Error(`TourAPI ${path} request failed (${response.status})`);
  }

  const payload = await response.json();
  const header = payload?.response?.header;
  if (!header || String(header.resultCode) !== "0000") {
    throw new Error(
      `TourAPI ${path} error: ${header?.resultCode ?? "UNKNOWN"} ${header?.resultMsg ?? ""}`.trim()
    );
  }
  const rawItems = payload.response.body?.items?.item ?? [];
  return Array.isArray(rawItems) ? rawItems : rawItems ? [rawItems] : [];
}

export async function fetchTourIntro(contentId, contentTypeId) {
  const items = await fetchTourDetail("detailIntro2", {
    contentId: String(contentId),
    contentTypeId: String(contentTypeId),
    numOfRows: "10",
    pageNo: "1"
  });
  return items[0] ?? null;
}

export async function fetchTourImages(contentId) {
  return fetchTourDetail("detailImage2", {
    contentId: String(contentId),
    imageYN: "Y",
    subImageYN: "Y",
    numOfRows: "20",
    pageNo: "1"
  });
}

export async function fetchPetTourDetail(contentId) {
  const items = await fetchTourDetail("detailPetTour2", {
    contentId: String(contentId),
    numOfRows: "10",
    pageNo: "1"
  });
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

function firstText(item, fields) {
  for (const field of fields) {
    const value = String(item?.[field] ?? "")
      .replace(/<br\s*\/?\s*>/gi, " ")
      .replace(/<[^>]+>/g, "")
      .replace(/\s+/g, " ")
      .trim();
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

export function normalizeTourEnrichment({
  contentTypeId,
  intro,
  images = [],
  pet
}) {
  const tags = [];
  if (hasPositiveValue(intro, parkingFields)) tags.push("주차 가능");
  if (hasPositiveValue(intro, childFields)) tags.push("아이 동반");
  if (pet) tags.push("반려동물 동반");
  if (Number(contentTypeId) === 14) tags.push("실내 활동");

  const imageUrls = images
    .flatMap((image) => [image.originimgurl, image.smallimageurl])
    .filter(Boolean)
    .filter((url, index, values) => values.indexOf(url) === index);

  return {
    tags,
    imageUrls,
    openingHours: firstText(intro, openingHoursFields),
    closedDays: firstText(intro, closedDaysFields),
    intro: intro ?? null,
    pet: pet ?? null,
    enrichedAt: new Date().toISOString()
  };
}
