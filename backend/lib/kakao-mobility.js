import { requiredEnv } from "./env.js";
import { fetchWithTimeout, NETWORK_TIMEOUT_MS } from "./fetch-policy.js";
import { estimateRoute } from "./routing.js";
import {
  createProviderResponseError,
  mobilityBudgetPolicy,
  ProviderResponseError,
  trackProviderCall
} from "./provider-usage.js";

const directionsUrl = "https://apis-navi.kakaomobility.com/v1/directions";

function coordinateString(coordinates) {
  return `${coordinates.longitude},${coordinates.latitude}`;
}

function secondsToMinutes(seconds) {
  return Math.max(1, Math.ceil(seconds / 60));
}

function normalizeWaypoints(placeOrWaypoints) {
  if (Array.isArray(placeOrWaypoints)) return placeOrWaypoints;
  return placeOrWaypoints ? [placeOrWaypoints] : [];
}

export function routePath(
  sections,
  start,
  destination,
  placeOrWaypoints = [],
  maxPoints = 200
) {
  const waypoints = normalizeWaypoints(placeOrWaypoints);
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
    : [start, ...waypoints.map(({ latitude, longitude }) => ({ latitude, longitude })), destination];
  if (points.length <= maxPoints) return points;

  // ponytail: uniform sampling keeps API payloads small; use geometry simplification if map fidelity needs it.
  return Array.from({ length: maxPoints }, (_, index) =>
    points[Math.round(index * (points.length - 1) / (maxPoints - 1))]
  );
}

export function parseKakaoRoute(
  payload,
  start,
  destination,
  placeOrWaypoints = []
) {
  const waypoints = normalizeWaypoints(placeOrWaypoints);
  const result = payload?.routes?.find((route) => route.result_code === 0);
  const sections = result?.sections;
  if (
    !result?.summary ||
    !Number.isFinite(result.summary.duration) ||
    !Number.isFinite(result.summary.distance) ||
    !Array.isArray(sections) ||
    sections.length !== waypoints.length + 1 ||
    !sections.every(
      (section) =>
        Number.isFinite(section.duration) && Number.isFinite(section.distance)
    )
  ) {
    return null;
  }

  const durationMinutes = secondsToMinutes(result.summary.duration);
  const distanceMeters = result.summary.distance;
  const tollFare = Number.isFinite(result.summary.fare?.toll)
    ? result.summary.fare.toll
    : 0;
  const route = {
    waypointCount: waypoints.length,
    durationMinutes,
    distanceMeters,
    tollFare,
    totalDrivingMinutes: durationMinutes,
    totalDistanceMeters: distanceMeters,
    tollFareWon: tollFare,
    legs: sections.map((section) => ({
      drivingMinutes: secondsToMinutes(section.duration),
      distanceMeters: section.distance
    })),
    path: routePath(sections, start, destination, waypoints),
    provider: "KAKAO_MOBILITY"
  };

  if (waypoints.length === 1) {
    const firstLegMinutes = route.legs[0].drivingMinutes;
    const secondLegMinutes = route.legs[1].drivingMinutes;
    const directMinutes = estimateRoute(start, destination, waypoints[0], "CAR").directMinutes;
    Object.assign(route, {
      firstLegMinutes,
      secondLegMinutes,
      directMinutes,
      detourMinutes: Math.max(0, durationMinutes - directMinutes),
      firstLegDistanceMeters: sections[0].distance,
      secondLegDistanceMeters: sections[1].distance
    });
  }

  return route;
}

export async function fetchKakaoRoute(
  start,
  destination,
  placeOrWaypoints = [],
  {
    apiKey = requiredEnv("KAKAO_REST_API_KEY"),
    signal,
    fetchImpl = fetch,
    usageTracker = trackProviderCall,
    now
  } = {}
) {
  const waypoints = normalizeWaypoints(placeOrWaypoints);
  if (waypoints.length > 5) {
    throw new Error("Kakao Mobility accepts at most 5 waypoints");
  }
  const query = new URLSearchParams({
    origin: coordinateString(start),
    destination: coordinateString(destination),
    priority: "TIME",
    alternatives: "false",
    road_details: "false",
    summary: "false"
  });
  if (waypoints.length > 0) {
    query.set("waypoints", waypoints.map(coordinateString).join("|"));
  }
  return usageTracker({
    provider: "KAKAO_MOBILITY",
    operation: "DIRECTIONS",
    budgetLimit: mobilityBudgetPolicy().budgetLimit,
    signal,
    now,
    call: async () => {
      const response = await fetchWithTimeout(`${directionsUrl}?${query}`, {
        headers: {
          authorization: `KakaoAK ${apiKey}`,
          "content-type": "application/json"
        }
      }, {
        provider: "KAKAO_MOBILITY",
        timeoutMs: NETWORK_TIMEOUT_MS.KAKAO_MOBILITY,
        signal,
        fetchImpl
      });
      if (!response.ok) {
        throw await createProviderResponseError(response, "KAKAO_MOBILITY", { now });
      }
      const route = parseKakaoRoute(
        await response.json(),
        start,
        destination,
        waypoints
      );
      if (!route) throw new ProviderResponseError("KAKAO_MOBILITY", 200);
      return route;
    }
  });
}

export async function fetchKakaoRoutes(
  start,
  destination,
  places,
  {
    concurrency = 5,
    apiKey,
    signal,
    fetchImpl = fetch,
    baseRoute,
    usageTracker = trackProviderCall,
    now
  } = {}
) {
  const routes = new Map();
  let failedCount = 0;
  let timeoutError = null;
  let nextIndex = 0;
  const workerCount = Math.min(Math.max(concurrency, 1), places.length);

  async function worker() {
    while (nextIndex < places.length) {
      const place = places[nextIndex];
      nextIndex += 1;
      try {
        const route = await fetchKakaoRoute(start, destination, place, {
          apiKey,
          signal,
          fetchImpl,
          usageTracker,
          now
        });
        if (route && baseRoute) {
          route.directMinutes = baseRoute.durationMinutes;
          route.detourMinutes = Math.max(
            0,
            route.firstLegMinutes +
              route.secondLegMinutes -
              baseRoute.durationMinutes
          );
        }
        if (route) routes.set(String(place.content_id), route);
        else failedCount += 1;
      } catch (error) {
        if (
          error?.code === "UPSTREAM_BUDGET_EXHAUSTED" ||
          error?.code === "UPSTREAM_QUOTA_EXHAUSTED"
        ) {
          throw error;
        }
        failedCount += 1;
        if (error?.code === "UPSTREAM_TIMEOUT") timeoutError ??= error;
      }
    }
  }

  await Promise.all(Array.from({ length: workerCount }, () => worker()));

  if (places.length > 0 && routes.size === 0) {
    if (timeoutError) throw timeoutError;
    throw new Error("Kakao Mobility could not calculate any candidate routes");
  }

  return { routes, failedCount };
}
