import { createHash } from "node:crypto";
import { requiredEnv } from "./env.js";
import { fetchWithTimeout, NETWORK_TIMEOUT_MS } from "./fetch-policy.js";
import {
  createProviderResponseError,
  trackProviderCall
} from "./provider-usage.js";

const keywordSearchUrl = "https://dapi.kakao.com/v2/local/search/keyword.json";
const addressSearchUrl = "https://dapi.kakao.com/v2/local/search/address.json";
const regionSearchUrl = "https://dapi.kakao.com/v2/local/geo/coord2regioncode.json";

function normalizedAddress(value) {
  return typeof value === "string" ? value.normalize("NFC").trim().replace(/\s+/g, " ") : "";
}

function isAddressQuery(query) {
  const normalized = normalizedAddress(query);
  // A building/lot number must end the query. Numeric POI names and a trailing
  // place name (e.g. "용지로 176 주차장") retain the existing keyword search.
  return /(?:^|\s)[가-힣0-9·.]+(?:대로|로|길)\s*\d+(?:-\d+)?$/.test(normalized) ||
    /(?:^|\s)[가-힣][가-힣0-9·]*(?:동|리|가)\s+(?:산\s*)?\d+(?:-\d+)?$/.test(normalized);
}

function addressCoordinate(value, min, max) {
  if (typeof value !== "number" && typeof value !== "string") return null;
  if (typeof value === "string" && !/^-?\d+(?:\.\d+)?$/.test(value.trim())) return null;
  const coordinate = Number(value);
  return Number.isFinite(coordinate) && coordinate >= min && coordinate <= max ? coordinate : null;
}

export function parseKakaoAddresses(payload) {
  if (!Array.isArray(payload?.documents)) return [];
  const addresses = new Map();
  for (const document of payload.documents) {
    // A road/district center is not a precise departure point.
    if (!["ROAD_ADDR", "REGION_ADDR"].includes(document?.address_type)) continue;
    const latitude = addressCoordinate(document.y, -90, 90);
    const longitude = addressCoordinate(document.x, -180, 180);
    if (latitude === null || longitude === null) continue;
    const roadAddress = normalizedAddress(document.road_address?.address_name);
    const lotAddress = normalizedAddress(document.address?.address_name);
    const address = roadAddress || lotAddress || normalizedAddress(document.address_name);
    if (!address) continue;
    const id = `address:${createHash("sha256").update(address).digest("hex").slice(0, 24)}`;
    if (addresses.has(id)) continue;
    addresses.set(id, {
      id,
      name: address,
      address,
      category: "주소",
      latitude,
      longitude,
      kakaoMapUrl: "",
      type: "ADDRESS",
      secondaryAddress: lotAddress && lotAddress !== address ? lotAddress : ""
    });
  }
  return [...addresses.values()].slice(0, 10);
}

async function fetchKakaoLocalPayload(
  url,
  apiKey,
  operation,
  { signal, fetchImpl = fetch, usageTracker = trackProviderCall, now } = {}
) {
  return usageTracker({
    provider: "KAKAO_LOCAL",
    operation,
    budgetLimit: null,
    signal,
    now,
    call: async () => {
      const response = await fetchWithTimeout(url, {
        headers: { authorization: `KakaoAK ${apiKey}` }
      }, {
        provider: "KAKAO_LOCAL",
        timeoutMs: NETWORK_TIMEOUT_MS.KAKAO_LOCAL,
        signal,
        fetchImpl
      });
      if (!response.ok) {
        throw await createProviderResponseError(response, "KAKAO_LOCAL", { now });
      }
      return response.json();
    }
  });
}

export function parseKakaoPlaces(payload) {
  if (!Array.isArray(payload?.documents)) return [];

  return payload.documents
    .map((place) => ({
      id: String(place.id ?? ""),
      name: String(place.place_name ?? ""),
      address: String(place.road_address_name || place.address_name || ""),
      category: String(place.category_name ?? ""),
      latitude: Number.parseFloat(place.y),
      longitude: Number.parseFloat(place.x),
      kakaoMapUrl: String(place.place_url ?? "")
    }))
    .filter(
      (place) =>
        place.id &&
        place.name &&
        Number.isFinite(place.latitude) &&
        Number.isFinite(place.longitude)
    );
}

export async function searchKakaoPlaces(
  query,
  {
    latitude,
    longitude,
    apiKey = requiredEnv("KAKAO_REST_API_KEY"),
    signal,
    fetchImpl = fetch,
    usageTracker = trackProviderCall,
    now
  } = {}
) {
  if (isAddressQuery(query)) {
    const addressParameters = new URLSearchParams({ query, size: "10", analyze_type: "exact" });
    const addressPayload = await fetchKakaoLocalPayload(
      `${addressSearchUrl}?${addressParameters}`,
      apiKey,
      "ADDRESS_SEARCH",
      { signal, fetchImpl, usageTracker, now }
    );
    const addresses = parseKakaoAddresses(addressPayload);
    if (addresses.length > 0) return addresses;
    // Only an empty successful response falls back. Provider failures remain
    // visible instead of turning an address lookup into unrelated POI results.
  }
  const parameters = new URLSearchParams({
    query,
    size: "10",
    sort: Number.isFinite(latitude) && Number.isFinite(longitude)
      ? "distance"
      : "accuracy"
  });
  if (Number.isFinite(latitude) && Number.isFinite(longitude)) {
    parameters.set("x", String(longitude));
    parameters.set("y", String(latitude));
    parameters.set("radius", "20000");
  }

  const payload = await fetchKakaoLocalPayload(
    `${keywordSearchUrl}?${parameters}`,
    apiKey,
    "KEYWORD_SEARCH",
    { signal, fetchImpl, usageTracker, now }
  );
  return parseKakaoPlaces(payload);
}

export function parseKakaoRegion(payload) {
  const region = payload?.documents?.find((item) => item.region_type === "H") ??
    payload?.documents?.[0];
  if (!region) return null;

  const province = String(region.region_1depth_name ?? "");
  return {
    province,
    address: String(region.address_name ?? ""),
    isGangwon: province.startsWith("강원")
  };
}

export async function lookupKakaoRegion(
  latitude,
  longitude,
  {
    apiKey = requiredEnv("KAKAO_REST_API_KEY"),
    signal,
    fetchImpl = fetch,
    usageTracker = trackProviderCall,
    now
  } = {}
) {
  const parameters = new URLSearchParams({
    x: String(longitude),
    y: String(latitude)
  });
  const payload = await fetchKakaoLocalPayload(
    `${regionSearchUrl}?${parameters}`,
    apiKey,
    "REGION",
    { signal, fetchImpl, usageTracker, now }
  );
  return parseKakaoRegion(payload);
}
