import { requiredEnv } from "./env.js";
import { estimateRoute } from "./routing.js";

const directionsUrl = "https://apis-navi.kakaomobility.com/v1/directions";

function coordinateString(coordinates) {
  return `${coordinates.longitude},${coordinates.latitude}`;
}

function secondsToMinutes(seconds) {
  return Math.max(1, Math.ceil(seconds / 60));
}

export function routePath(sections, start, destination, place, maxPoints = 200) {
  const roadPoints = sections.flatMap((section) =>
    (section.roads ?? []).flatMap((road) => {
      const points = [];
      for (let index = 0; index + 1 < (road.vertexes?.length ?? 0); index += 2) {
        points.push({
          longitude: road.vertexes[index],
          latitude: road.vertexes[index + 1]
        });
      }
      return points;
    })
  );
  const points = roadPoints.length > 0
    ? [start, ...roadPoints, destination]
    : [start, { latitude: place.latitude, longitude: place.longitude }, destination];
  if (points.length <= maxPoints) return points;

  // ponytail: uniform sampling keeps API payloads small; use geometry simplification if map fidelity needs it.
  return Array.from({ length: maxPoints }, (_, index) =>
    points[Math.round(index * (points.length - 1) / (maxPoints - 1))]
  );
}

export function parseKakaoRoute(payload, start, destination, place) {
  const result = payload?.routes?.find((route) => route.result_code === 0);
  const sections = result?.sections;
  if (
    !result?.summary ||
    !Array.isArray(sections) ||
    sections.length !== 2 ||
    !sections.every(
      (section) =>
        Number.isFinite(section.duration) && Number.isFinite(section.distance)
    )
  ) {
    return null;
  }

  const firstLegMinutes = secondsToMinutes(sections[0].duration);
  const secondLegMinutes = secondsToMinutes(sections[1].duration);
  const directMinutes = estimateRoute(start, destination, place, "CAR").directMinutes;

  return {
    firstLegMinutes,
    secondLegMinutes,
    directMinutes,
    detourMinutes: Math.max(
      0,
      firstLegMinutes + secondLegMinutes - directMinutes
    ),
    firstLegDistanceMeters: sections[0].distance,
    secondLegDistanceMeters: sections[1].distance,
    totalDistanceMeters: result.summary.distance,
    path: routePath(sections, start, destination, place),
    provider: "KAKAO_MOBILITY"
  };
}

export async function fetchKakaoRoute(
  start,
  destination,
  place,
  {
    apiKey = requiredEnv("KAKAO_REST_API_KEY"),
    fetchImpl = fetch
  } = {}
) {
  const query = new URLSearchParams({
    origin: coordinateString(start),
    destination: coordinateString(destination),
    waypoints: coordinateString({
      latitude: place.latitude,
      longitude: place.longitude
    }),
    priority: "TIME",
    alternatives: "false",
    road_details: "false",
    summary: "false"
  });
  const response = await fetchImpl(`${directionsUrl}?${query}`, {
    headers: {
      authorization: `KakaoAK ${apiKey}`,
      "content-type": "application/json"
    }
  });

  if (!response.ok) {
    throw new Error(`Kakao Mobility request failed (${response.status})`);
  }

  return parseKakaoRoute(await response.json(), start, destination, place);
}

export async function fetchKakaoRoutes(
  start,
  destination,
  places,
  { concurrency = 5, apiKey, fetchImpl = fetch } = {}
) {
  const routes = new Map();
  let failedCount = 0;
  let nextIndex = 0;
  const workerCount = Math.min(Math.max(concurrency, 1), places.length);

  async function worker() {
    while (nextIndex < places.length) {
      const place = places[nextIndex];
      nextIndex += 1;
      try {
        const route = await fetchKakaoRoute(start, destination, place, {
          apiKey,
          fetchImpl
        });
        if (route) routes.set(String(place.content_id), route);
        else failedCount += 1;
      } catch {
        failedCount += 1;
      }
    }
  }

  await Promise.all(Array.from({ length: workerCount }, () => worker()));

  if (places.length > 0 && routes.size === 0) {
    throw new Error("Kakao Mobility could not calculate any candidate routes");
  }

  return { routes, failedCount };
}
