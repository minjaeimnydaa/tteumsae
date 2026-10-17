import { requiredEnv } from "./env.js";
import { fetchWithTimeout, NETWORK_TIMEOUT_MS } from "./fetch-policy.js";
import { supabaseApiHeaders } from "./supabase-auth.js";
import { haversineKm } from "./routing.js";

const basePublicColumns = [
  "content_id",
  "source",
  "name",
  "category",
  "content_type_id",
  "area_code",
  "sigungu_code",
  "latitude",
  "longitude",
  "address",
  "image_url",
  "tel",
  "default_stay_minutes"
];

const enrichmentPublicColumns = [
  "cat1",
  "cat2",
  "cat3",
  "opening_hours",
  "closed_days",
  "event_start_date",
  "event_end_date",
  "overview",
  "homepage_url",
  "image_urls",
  "tags"
];

const publicColumns = [...basePublicColumns, ...enrichmentPublicColumns].join(",");
const effectivePublicColumns = [
  "effective_opening_hours",
  "effective_closed_days",
  "effective_last_admission",
  "effective_parking_info",
  "data_provenance",
  "operating_info_status",
  "admission_info_status",
  "parking_info_status",
  "reviewed_at"
];
const effectiveColumns = [
  ...basePublicColumns,
  ...enrichmentPublicColumns,
  ...effectivePublicColumns
].join(",");

const legacyPublicColumns = [...basePublicColumns, "raw"].join(",");

function toPublicPlace(row) {
  const legacy = row.raw?._tteumsae ?? {};
  const { raw: _raw, enrichment_raw: enrichmentRaw, ...place } = row;
  const hasEffectiveHours = Object.hasOwn(place, "effective_opening_hours");
  const hasEffectiveClosedDays = Object.hasOwn(place, "effective_closed_days");
  const imageUrls = Array.isArray(place.image_urls)
    ? place.image_urls
    : Array.isArray(legacy.imageUrls)
      ? legacy.imageUrls
      : [];
  const tags = Array.isArray(place.tags)
    ? place.tags
    : Array.isArray(legacy.tags)
      ? legacy.tags
      : [];
  const result = {
    ...place,
    cat1: place.cat1 ?? row.raw?.cat1 ?? null,
    cat2: place.cat2 ?? row.raw?.cat2 ?? null,
    cat3: place.cat3 ?? row.raw?.cat3 ?? null,
    image_url: place.image_url || imageUrls[0] || null,
    image_urls: imageUrls,
    tags,
    opening_hours: hasEffectiveHours
      ? place.effective_opening_hours
      : place.opening_hours ?? legacy.openingHours ?? null,
    closed_days: hasEffectiveClosedDays
      ? place.effective_closed_days
      : place.closed_days ?? legacy.closedDays ?? null,
    last_admission: place.effective_last_admission ?? null,
    parking_info: place.effective_parking_info ?? null
  };
  if (enrichmentRaw && typeof enrichmentRaw === "object") {
    result.detail_items = publicDetailItems(enrichmentRaw.infoItems);
    result.accessibility_items = publicDetailItems(enrichmentRaw.accessibilityItems);
    result.image_attributions = publicImageAttributions(enrichmentRaw);
  }
  for (const column of effectivePublicColumns.filter((name) => name.startsWith("effective_"))) {
    delete result[column];
  }
  return result;
}

function boundedText(value, maxLength) {
  const text = typeof value === "string" ? value.trim() : "";
  return text ? text.slice(0, maxLength) : null;
}

function publicDetailItems(items) {
  if (!Array.isArray(items)) return [];
  return items.slice(0, 20).flatMap((item) => {
    const title = boundedText(item?.title, 200);
    const description = boundedText(item?.description, 4_000);
    return title && description ? [{ title, description }] : [];
  });
}

function publicHttpUrl(value) {
  const candidate = boundedText(value, 2_000);
  if (!candidate) return null;
  try {
    const url = new URL(candidate);
    return ["http:", "https:"].includes(url.protocol) ? candidate : null;
  } catch {
    return null;
  }
}

function publicImageAttributions(enrichmentRaw) {
  const stored = Array.isArray(enrichmentRaw.imageAttributions)
    ? enrichmentRaw.imageAttributions
    : Array.isArray(enrichmentRaw.images)
      ? enrichmentRaw.images.map((image) => ({
          image_url: image?.originimgurl,
          thumbnail_url: image?.smallimageurl,
          name: image?.imgname,
          copyright_type: image?.cpyrhtDivCd,
          copyright_label: {
            Type1: "공공누리 제1유형",
            Type3: "공공누리 제3유형"
          }[image?.cpyrhtDivCd] ?? null
        }))
      : [];
  return stored.slice(0, 30).flatMap((item) => {
    const imageUrl = publicHttpUrl(item?.image_url);
    const thumbnailUrl = publicHttpUrl(item?.thumbnail_url);
    if (!imageUrl && !thumbnailUrl) return [];
    return [{
      image_url: imageUrl,
      thumbnail_url: thumbnailUrl,
      name: boundedText(item?.name, 300),
      copyright_type: boundedText(item?.copyright_type, 80),
      copyright_label: boundedText(item?.copyright_label, 120)
    }];
  });
}

async function databaseRequest(path, { method = "GET", body, prefer, signal } = {}) {
  const baseUrl = requiredEnv("SUPABASE_URL").replace(/\/$/, "");
  const serviceKey = requiredEnv("SUPABASE_SERVICE_ROLE_KEY");
  const headers = supabaseApiHeaders(serviceKey, serviceKey, {
    "content-type": "application/json"
  });
  if (prefer) headers.prefer = prefer;

  const result = await fetchWithTimeout(`${baseUrl}/rest/v1/${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  }, {
    provider: "SUPABASE",
    timeoutMs: NETWORK_TIMEOUT_MS.SUPABASE,
    signal,
    consume: async (response) => ({
      status: response.status,
      ok: response.ok,
      text: response.status === 204 ? "" : await response.text()
    })
  });
  if (!result.ok) {
    const detail = result.text;
    const error = new Error(`Database request failed (${result.status}): ${detail.slice(0, 300)}`);
    error.status = result.status;
    try {
      const databaseError = JSON.parse(detail);
      error.code = databaseError.code;
      error.databaseMessage = databaseError.message;
    } catch {
      error.code = null;
      error.databaseMessage = null;
    }
    throw error;
  }
  if (result.status === 204) return null;
  return result.text ? JSON.parse(result.text) : null;
}

function isMissingEffectiveView(error) {
  return ["42P01", "PGRST205"].includes(error.code) ||
    /effective_places/i.test(error.databaseMessage ?? "");
}

async function requestPublicPlaceRows(query, signal, { includeSupplemental = false } = {}) {
  const supplemental = includeSupplemental ? ",enrichment_raw" : "";
  query.set("select", `${effectiveColumns}${supplemental}`);
  try {
    return await databaseRequest(`effective_places?${query}`, { signal });
  } catch (error) {
    const missingEnrichmentColumn =
      error.code === "42703" &&
      enrichmentPublicColumns.some((column) => error.databaseMessage?.includes(`places.${column}`));
    if (missingEnrichmentColumn) {
      query.set("select", legacyPublicColumns);
      return databaseRequest(`places?${query}`, { signal });
    }
    if (!isMissingEffectiveView(error)) throw error;
    query.set("select", `${publicColumns}${supplemental}`);
    try {
      return await databaseRequest(`places?${query}`, { signal });
    } catch (fallbackError) {
      const missingFallbackColumn =
        fallbackError.code === "42703" &&
        (
          enrichmentPublicColumns.some((column) =>
            fallbackError.databaseMessage?.includes(`places.${column}`)
          ) || fallbackError.databaseMessage?.includes("places.enrichment_raw")
        );
      if (!missingFallbackColumn) throw fallbackError;
    }
    query.set("select", legacyPublicColumns);
    return databaseRequest(`places?${query}`, { signal });
  }
}

export async function listPlaces({
  limit = 30,
  offset = 0,
  category,
  sigunguCode,
  minLatitude,
  maxLatitude,
  minLongitude,
  maxLongitude,
  signal
} = {}) {
  const query = new URLSearchParams({
    is_active: "eq.true",
    order: "name.asc",
    limit: String(Math.min(Math.max(limit, 1), 500)),
    offset: String(Math.max(offset, 0))
  });
  if (category) query.set("category", `eq.${category}`);
  if (Number.isInteger(sigunguCode)) query.set("sigungu_code", `eq.${sigunguCode}`);
  if (Number.isFinite(minLatitude)) query.set("latitude", `gte.${minLatitude}`);
  if (Number.isFinite(maxLatitude)) query.append("latitude", `lte.${maxLatitude}`);
  if (Number.isFinite(minLongitude)) query.set("longitude", `gte.${minLongitude}`);
  if (Number.isFinite(maxLongitude)) query.append("longitude", `lte.${maxLongitude}`);

  const rows = await requestPublicPlaceRows(query, signal);
  return rows.map(toPublicPlace);
}

export async function getPlace(contentId, { signal } = {}) {
  const query = new URLSearchParams({
    content_id: `eq.${contentId}`,
    is_active: "eq.true",
    limit: "1"
  });
  const rows = await requestPublicPlaceRows(query, signal, { includeSupplemental: true });
  return rows?.[0] ? toPublicPlace(rows[0]) : null;
}

export async function upsertPlaces(rows, { signal } = {}) {
  if (rows.length === 0) return;
  const chunkSize = 100;
  for (let index = 0; index < rows.length; index += chunkSize) {
    const chunk = rows.slice(index, index + chunkSize);
    await databaseRequest("places?on_conflict=content_id", {
      method: "POST",
      body: chunk,
      prefer: "resolution=merge-duplicates,return=minimal",
      signal
    });
  }
}

export async function listPlacesForEnrichment({ limit = 5, offset = 0, signal } = {}) {
  const query = new URLSearchParams({
    select: "content_id,content_type_id,image_url,raw",
    is_active: "eq.true",
    order: "content_id.asc",
    limit: String(Math.min(Math.max(limit, 1), 20)),
    offset: String(Math.max(offset, 0))
  });
  return databaseRequest(`places?${query}`, { signal });
}

export async function updatePlaceEnrichment(place, enrichment, { signal } = {}) {
  await databaseRequest(`places?content_id=eq.${encodeURIComponent(place.content_id)}`, {
    method: "PATCH",
    body: {
      image_url: place.image_url || enrichment.imageUrls[0] || null,
      raw: {
        ...(place.raw ?? {}),
        _tteumsae: enrichment
      }
    },
    prefer: "return=minimal",
    signal
  });
}

const introOwnedTags = new Set(["주차 가능", "아이 동반"]);
const mediaOwnedTags = new Set(["반려동물 동반", "실내 활동"]);

function replaceOwnedTags(existingTags, ownedTags, nextTags) {
  const retainedTags = (Array.isArray(existingTags) ? existingTags : [])
    .filter((tag) => !ownedTags.has(tag));
  const replacements = (Array.isArray(nextTags) ? nextTags : [])
    .filter((tag) => ownedTags.has(tag));
  return [...new Set([...retainedTags, ...replacements])];
}

export async function listPlacesForIntroSync({ limit = 20, now = new Date(), signal } = {}) {
  const dueAt = now instanceof Date ? now : new Date(now);
  if (Number.isNaN(dueAt.getTime())) throw new Error("Invalid intro sync reference time");
  const query = new URLSearchParams({
    select: "content_id,content_type_id,tags,enrichment_raw,enrichment_attempts",
    is_active: "eq.true",
    intro_synced_at: "is.null",
    or: `(next_enrichment_at.is.null,next_enrichment_at.lte.${dueAt.toISOString()})`,
    order: "next_enrichment_at.asc.nullsfirst,content_id.asc",
    limit: String(Math.min(Math.max(Number.parseInt(limit, 10) || 1, 1), 40))
  });
  return databaseRequest(`places?${query}`, { signal });
}

export async function savePlaceIntro(place, enrichment, { signal } = {}) {
  const body = {
    enrichment_raw: {
      ...(place.enrichment_raw ?? {}),
      intro: enrichment.intro
    },
    intro_synced_at: enrichment.syncedAt,
    enrichment_attempts: 0,
    enrichment_last_error: null,
    next_enrichment_at: null
  };

  if (enrichment.intro !== null) {
    if (enrichment.openingHours != null) body.opening_hours = enrichment.openingHours;
    if (enrichment.closedDays != null) body.closed_days = enrichment.closedDays;
    if (enrichment.eventStartDate != null) body.event_start_date = enrichment.eventStartDate;
    if (enrichment.eventEndDate != null) body.event_end_date = enrichment.eventEndDate;
    body.tags = replaceOwnedTags(place.tags, introOwnedTags, enrichment.tags);
  }

  await databaseRequest(`places?content_id=eq.${encodeURIComponent(place.content_id)}`, {
    method: "PATCH",
    body,
    prefer: "return=minimal",
    signal
  });
}

export async function listPlacesForPresentationSync({
  limit = 5,
  now = new Date(),
  signal
} = {}) {
  const dueAt = now instanceof Date ? now : new Date(now);
  if (Number.isNaN(dueAt.getTime())) {
    throw new Error("Invalid presentation sync reference time");
  }
  const query = new URLSearchParams({
    select: [
      "content_id",
      "content_type_id",
      "image_url",
      "tags",
      "enrichment_raw",
      "common_synced_at",
      "media_synced_at",
      "info_synced_at",
      "enrichment_attempts"
    ].join(","),
    is_active: "eq.true",
    intro_synced_at: "not.is.null",
    and: [
      "(or(common_synced_at.is.null,media_synced_at.is.null,info_synced_at.is.null)",
      `or(next_enrichment_at.is.null,next_enrichment_at.lte.${dueAt.toISOString()}))`
    ].join(","),
    order: "next_enrichment_at.asc.nullsfirst,content_id.asc",
    limit: String(Math.min(Math.max(Number.parseInt(limit, 10) || 1, 1), 10))
  });
  return databaseRequest(`places?${query}`, { signal });
}

export async function savePlaceCommon(place, enrichment, { signal } = {}) {
  const body = {
    enrichment_raw: {
      ...(place.enrichment_raw ?? {}),
      common: enrichment.common
    },
    common_synced_at: enrichment.syncedAt,
    enrichment_attempts: 0,
    enrichment_last_error: null,
    next_enrichment_at: null
  };

  if (enrichment.common !== null) {
    if (enrichment.overview != null) body.overview = enrichment.overview;
    if (enrichment.homepageUrl != null) body.homepage_url = enrichment.homepageUrl;
  }

  await databaseRequest(`places?content_id=eq.${encodeURIComponent(place.content_id)}`, {
    method: "PATCH",
    body,
    prefer: "return=minimal",
    signal
  });
}

export async function savePlaceMedia(place, enrichment, { signal } = {}) {
  const imageUrls = Array.isArray(enrichment.imageUrls) ? enrichment.imageUrls : [];
  const body = {
    tags: replaceOwnedTags(place.tags, mediaOwnedTags, enrichment.tags),
    enrichment_raw: {
      ...(place.enrichment_raw ?? {}),
      images: Array.isArray(enrichment.images) ? enrichment.images : [],
      imageAttributions: Array.isArray(enrichment.imageAttributions)
        ? enrichment.imageAttributions
        : [],
      pet: enrichment.pet ?? null
    },
    media_synced_at: enrichment.syncedAt,
    enrichment_attempts: 0,
    enrichment_last_error: null,
    next_enrichment_at: null
  };

  if (imageUrls.length > 0) {
    body.image_urls = imageUrls;
    if (!place.image_url) body.image_url = imageUrls[0];
  }

  await databaseRequest(`places?content_id=eq.${encodeURIComponent(place.content_id)}`, {
    method: "PATCH",
    body,
    prefer: "return=minimal",
    signal
  });
}

function isMissingCongestionTable(error) {
  return ["42P01", "PGRST205"].includes(error.code) ||
    /tour_congestion_forecasts/i.test(error.databaseMessage ?? "");
}

export async function getPlaceCongestionForecast(
  contentId,
  forecastDate,
  { signal } = {}
) {
  const query = new URLSearchParams({
    select: "forecast_date,concentration_rate,level,fetched_at",
    content_id: `eq.${contentId}`,
    forecast_date: `eq.${forecastDate}`,
    limit: "1"
  });
  try {
    const rows = await databaseRequest(`tour_congestion_forecasts?${query}`, { signal });
    return rows?.[0] ?? null;
  } catch (error) {
    if (isMissingCongestionTable(error)) return null;
    throw error;
  }
}

export async function listPlacesForCongestionMatch({ sigunguCode = 1, signal } = {}) {
  const query = new URLSearchParams({
    select: "content_id,name,category",
    is_active: "eq.true",
    sigungu_code: `eq.${sigunguCode}`,
    category: "not.in.(RESTAURANT,CAFE)",
    order: "name.asc",
    limit: "500"
  });
  return databaseRequest(`places?${query}`, { signal });
}

export async function upsertCongestionForecasts(rows, { signal } = {}) {
  const chunkSize = 500;
  for (let index = 0; index < rows.length; index += chunkSize) {
    await databaseRequest(
      "tour_congestion_forecasts?on_conflict=area_code,sigungu_code,source_name,forecast_date",
      {
        method: "POST",
        body: rows.slice(index, index + chunkSize),
        prefer: "resolution=merge-duplicates,return=minimal",
        signal
      }
    );
  }
}

export async function getWeatherForecastCache({
  nx,
  ny,
  forecastAt,
  minimumIssuedAt,
  signal
}) {
  const query = new URLSearchParams({
    select: "forecast_at,issued_at,condition_label,temperature_c,precipitation_probability,wind_speed_mps,fetched_at",
    nx: `eq.${nx}`,
    ny: `eq.${ny}`,
    forecast_at: `eq.${forecastAt}`,
    issued_at: `gte.${minimumIssuedAt}`,
    limit: "1"
  });
  try {
    const rows = await databaseRequest(`weather_forecast_cache?${query}`, { signal });
    return rows?.[0] ?? null;
  } catch (error) {
    if (["42P01", "PGRST205"].includes(error.code) ||
      /weather_forecast_cache/i.test(error.databaseMessage ?? "")) return null;
    throw error;
  }
}

export async function upsertWeatherForecastCache(row, { signal } = {}) {
  await databaseRequest(
    "weather_forecast_cache?on_conflict=nx,ny,forecast_at",
    {
      method: "POST",
      body: row,
      prefer: "resolution=merge-duplicates,return=minimal",
      signal
    }
  );
}

export async function savePlaceInfo(place, enrichment, { signal } = {}) {
  await databaseRequest(`places?content_id=eq.${encodeURIComponent(place.content_id)}`, {
    method: "PATCH",
    body: {
      enrichment_raw: {
        ...(place.enrichment_raw ?? {}),
        info: Array.isArray(enrichment.rawItems) ? enrichment.rawItems : [],
        infoItems: Array.isArray(enrichment.infoItems) ? enrichment.infoItems : []
      },
      info_synced_at: enrichment.syncedAt,
      enrichment_attempts: 0,
      enrichment_last_error: null,
      next_enrichment_at: null
    },
    prefer: "return=minimal",
    signal
  });
}

export async function savePlaceAccessibility(contentId, enrichment, { signal } = {}) {
  return Boolean(await databaseRequest("rpc/save_place_accessibility", {
    method: "POST",
    body: {
      p_content_id: String(contentId),
      p_accessibility_raw: enrichment.raw ?? null,
      p_accessibility_items: Array.isArray(enrichment.items) ? enrichment.items : [],
      p_synced_at: enrichment.syncedAt
    },
    signal
  }));
}

export async function upsertPublicParkingLots(rows, { signal } = {}) {
  const chunkSize = 200;
  for (let index = 0; index < rows.length; index += chunkSize) {
    await databaseRequest("public_parking_lots?on_conflict=source_id", {
      method: "POST",
      body: rows.slice(index, index + chunkSize),
      prefer: "resolution=merge-duplicates,return=minimal",
      signal
    });
  }
}

export async function listNearbyPublicParkingLots(
  { latitude, longitude, radiusMeters = 1_000, limit = 3 },
  { signal } = {}
) {
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return [];
  const latitudeDelta = radiusMeters / 111_320;
  const longitudeDelta = radiusMeters / (
    111_320 * Math.max(Math.cos(latitude * Math.PI / 180), 0.1)
  );
  const query = new URLSearchParams({
    select: [
      "source_id", "name", "parking_type", "address", "capacity",
      "operation_days", "weekday_open", "weekday_close", "fee_info",
      "basic_minutes", "basic_fee_won", "accessible_parking", "phone",
      "reference_date", "latitude", "longitude"
    ].join(","),
    latitude: `gte.${latitude - latitudeDelta}`,
    longitude: `gte.${longitude - longitudeDelta}`,
    limit: "50"
  });
  query.append("latitude", `lte.${latitude + latitudeDelta}`);
  query.append("longitude", `lte.${longitude + longitudeDelta}`);
  try {
    const rows = await databaseRequest(`public_parking_lots?${query}`, { signal });
    return rows
      .map((row) => ({
        ...row,
        distance_meters: haversineKm(
          { latitude, longitude },
          { latitude: Number(row.latitude), longitude: Number(row.longitude) }
        ) * 1_000
      }))
      .filter((row) => row.distance_meters <= radiusMeters)
      .sort((left, right) => left.distance_meters - right.distance_meters)
      .slice(0, Math.min(Math.max(limit, 1), 5));
  } catch (error) {
    if (["42P01", "PGRST205"].includes(error.code) ||
      /public_parking_lots/i.test(error.databaseMessage ?? "")) return [];
    throw error;
  }
}

export async function recordPlaceEnrichmentFailure(
  place,
  error,
  now = new Date(),
  { signal } = {}
) {
  const failedAt = now instanceof Date ? now : new Date(now);
  if (Number.isNaN(failedAt.getTime())) throw new Error("Invalid enrichment failure time");
  const attempts = Math.max(Number.parseInt(place.enrichment_attempts, 10) || 0, 0) + 1;
  const exponent = Math.min(attempts - 1, 16);
  const delayMinutes = Math.min(24 * 60, 15 * (2 ** exponent));
  const nextAttemptAt = new Date(failedAt.getTime() + delayMinutes * 60_000);
  const message = (error instanceof Error ? error.message : String(error)).slice(0, 300);

  await databaseRequest(`places?content_id=eq.${encodeURIComponent(place.content_id)}`, {
    method: "PATCH",
    body: {
      enrichment_attempts: attempts,
      enrichment_last_error: message,
      next_enrichment_at: nextAttemptAt.toISOString()
    },
    prefer: "return=minimal",
    signal
  });
}

export async function resetPlaceEnrichment(contentId, { signal } = {}) {
  await databaseRequest(`places?content_id=eq.${encodeURIComponent(contentId)}`, {
    method: "PATCH",
    body: {
      intro_synced_at: null,
      common_synced_at: null,
      media_synced_at: null,
      info_synced_at: null,
      enrichment_attempts: 0,
      enrichment_last_error: null,
      next_enrichment_at: null
    },
    prefer: "return=minimal",
    signal
  });
}

export async function setPlaceActive(contentId, active, { signal } = {}) {
  await databaseRequest(`places?content_id=eq.${encodeURIComponent(contentId)}`, {
    method: "PATCH",
    body: { is_active: Boolean(active) },
    prefer: "return=minimal",
    signal
  });
}

export async function getSyncState(id = "tour_api", { signal } = {}) {
  const query = new URLSearchParams({
    select: "*",
    id: `eq.${id}`,
    limit: "1"
  });
  const rows = await databaseRequest(`sync_state?${query}`, { signal });
  return (
    rows?.[0] ?? {
      id,
      next_page: 1,
      total_count: 0,
      last_processed_page: 0,
      last_item_count: 0
    }
  );
}

export async function saveSyncState(state, { signal } = {}) {
  await databaseRequest("sync_state?on_conflict=id", {
    method: "POST",
    body: {
      id: state.id ?? "tour_api",
      ...state,
      updated_at: new Date().toISOString()
    },
    prefer: "resolution=merge-duplicates,return=minimal",
    signal
  });
}

export async function claimSyncJob({ jobId, token, now, leaseSeconds = 90, signal }) {
  return Boolean(await databaseRequest("rpc/claim_sync_job", {
    method: "POST",
    body: {
      p_id: jobId,
      p_token: token,
      p_now: now,
      p_lease_seconds: leaseSeconds
    },
    signal
  }));
}

export async function finishSyncJob({
  jobId,
  token,
  status,
  summary,
  finishedAt,
  signal
}) {
  return Boolean(await databaseRequest("rpc/finish_sync_job", {
    method: "POST",
    body: {
      p_id: jobId,
      p_token: token,
      p_status: status,
      p_summary: summary,
      p_finished_at: finishedAt
    },
    signal
  }));
}

export async function reserveProviderUsage({
  provider,
  operation,
  usageDate,
  budgetLimit = null,
  units = 1,
  signal
}) {
  const result = await databaseRequest("rpc/reserve_provider_usage", {
    method: "POST",
    body: {
      p_provider: provider,
      p_operation: operation,
      p_usage_date: usageDate,
      p_budget_limit: budgetLimit,
      p_units: units
    },
    signal
  });
  const row = Array.isArray(result) ? result[0] : result;
  if (!row || typeof row.allowed !== "boolean") {
    throw new Error("Provider usage reservation returned an invalid response");
  }
  return {
    allowed: row.allowed,
    reservedCount: Number(row.reserved_count) || 0,
    remainingCount: row.remaining_count == null
      ? null
      : Math.max(0, Number(row.remaining_count) || 0)
  };
}

export async function recordProviderUsageResult({
  provider,
  operation,
  usageDate,
  resultKind,
  units = 1,
  signal
}) {
  await databaseRequest("rpc/record_provider_usage_result", {
    method: "POST",
    body: {
      p_provider: provider,
      p_operation: operation,
      p_usage_date: usageDate,
      p_result_kind: resultKind,
      p_units: units
    },
    signal
  });
}

export async function getGate1bOpsStatus({
  usageDate,
  sigunguCode = 1,
  curationTarget = 100,
  signal
}) {
  return databaseRequest("rpc/get_gate_1b_ops_status", {
    method: "POST",
    body: {
      p_usage_date: usageDate,
      p_sigungu_code: sigunguCode,
      p_curation_target: curationTarget
    },
    signal
  });
}

export async function listGangneungCurationCandidates({ signal } = {}) {
  const query = new URLSearchParams({
    select: [
      "content_id",
      "name",
      "category",
      "image_url",
      "overview",
      "intro_synced_at",
      "opening_hours",
      "closed_days",
      "event_start_date",
      "event_end_date"
    ].join(","),
    is_active: "eq.true",
    sigungu_code: "eq.1",
    order: "name.asc",
    limit: "5000"
  });
  try {
    return await databaseRequest(`effective_places?${query}`, { signal });
  } catch (error) {
    if (!isMissingEffectiveView(error)) throw error;
    return databaseRequest(`places?${query}`, { signal });
  }
}

export async function upsertPlaceCurations(rows, { signal } = {}) {
  const chunkSize = 50;
  for (let index = 0; index < rows.length; index += chunkSize) {
    await databaseRequest("place_curations?on_conflict=content_id", {
      method: "POST",
      body: rows.slice(index, index + chunkSize),
      prefer: "resolution=merge-duplicates,return=minimal",
      signal
    });
  }
}
