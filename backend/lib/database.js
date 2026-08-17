import { requiredEnv } from "./env.js";

const publicColumns = [
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
  "default_stay_minutes",
  "raw"
].join(",");

function toPublicPlace(row) {
  const enrichment = row.raw?._tteumsae ?? {};
  const { raw: _raw, ...place } = row;
  return {
    ...place,
    image_url: place.image_url || enrichment.imageUrls?.[0] || null,
    image_urls: enrichment.imageUrls ?? [],
    tags: enrichment.tags ?? [],
    opening_hours: enrichment.openingHours ?? null,
    closed_days: enrichment.closedDays ?? null
  };
}

async function databaseRequest(path, { method = "GET", body, prefer } = {}) {
  const baseUrl = requiredEnv("SUPABASE_URL").replace(/\/$/, "");
  const serviceKey = requiredEnv("SUPABASE_SERVICE_ROLE_KEY");
  const headers = {
    apikey: serviceKey,
    authorization: `Bearer ${serviceKey}`,
    "content-type": "application/json"
  };
  if (prefer) headers.prefer = prefer;

  const response = await fetch(`${baseUrl}/rest/v1/${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  if (!response.ok) {
    const detail = await response.text();
    throw new Error(`Database request failed (${response.status}): ${detail.slice(0, 300)}`);
  }
  if (response.status === 204) return null;
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

export async function listPlaces({
  limit = 30,
  offset = 0,
  category,
  minLatitude,
  maxLatitude,
  minLongitude,
  maxLongitude
} = {}) {
  const query = new URLSearchParams({
    select: publicColumns,
    is_active: "eq.true",
    order: "name.asc",
    limit: String(Math.min(Math.max(limit, 1), 500)),
    offset: String(Math.max(offset, 0))
  });
  if (category) query.set("category", `eq.${category}`);
  if (Number.isFinite(minLatitude)) query.set("latitude", `gte.${minLatitude}`);
  if (Number.isFinite(maxLatitude)) query.append("latitude", `lte.${maxLatitude}`);
  if (Number.isFinite(minLongitude)) query.set("longitude", `gte.${minLongitude}`);
  if (Number.isFinite(maxLongitude)) query.append("longitude", `lte.${maxLongitude}`);

  const rows = await databaseRequest(`places?${query}`);
  return rows.map(toPublicPlace);
}

export async function getPlace(contentId) {
  const query = new URLSearchParams({
    select: publicColumns,
    content_id: `eq.${contentId}`,
    is_active: "eq.true",
    limit: "1"
  });
  const rows = await databaseRequest(`places?${query}`);
  return rows?.[0] ? toPublicPlace(rows[0]) : null;
}

export async function upsertPlaces(rows) {
  if (rows.length === 0) return;
  const chunkSize = 100;
  for (let index = 0; index < rows.length; index += chunkSize) {
    const chunk = rows.slice(index, index + chunkSize);
    await databaseRequest("places?on_conflict=content_id", {
      method: "POST",
      body: chunk,
      prefer: "resolution=merge-duplicates,return=minimal"
    });
  }
}

export async function listPlacesForEnrichment({ limit = 5, offset = 0 } = {}) {
  const query = new URLSearchParams({
    select: "content_id,content_type_id,image_url,raw",
    is_active: "eq.true",
    order: "content_id.asc",
    limit: String(Math.min(Math.max(limit, 1), 20)),
    offset: String(Math.max(offset, 0))
  });
  return databaseRequest(`places?${query}`);
}

export async function updatePlaceEnrichment(place, enrichment) {
  await databaseRequest(`places?content_id=eq.${encodeURIComponent(place.content_id)}`, {
    method: "PATCH",
    body: {
      image_url: place.image_url || enrichment.imageUrls[0] || null,
      raw: {
        ...(place.raw ?? {}),
        _tteumsae: enrichment
      }
    },
    prefer: "return=minimal"
  });
}

export async function getSyncState(id = "tour_api") {
  const query = new URLSearchParams({
    select: "*",
    id: `eq.${id}`,
    limit: "1"
  });
  const rows = await databaseRequest(`sync_state?${query}`);
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

export async function saveSyncState(state) {
  await databaseRequest("sync_state?on_conflict=id", {
    method: "POST",
    body: {
      id: state.id ?? "tour_api",
      ...state,
      updated_at: new Date().toISOString()
    },
    prefer: "resolution=merge-duplicates,return=minimal"
  });
}
