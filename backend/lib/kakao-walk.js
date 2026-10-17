import { requiredEnv } from "./env.js";
import { fetchWithTimeout, NETWORK_TIMEOUT_MS } from "./fetch-policy.js";
import { estimateRoute } from "./routing.js";
import {
  createProviderResponseError,
  ProviderResponseError,
  trackProviderCall,
  walkBudgetPolicy
} from "./provider-usage.js";

const walkDirectionsUrl = "https://dapi.kakao.com/v2/routing/walk";

function minutes(seconds) {
  return Math.max(1, Math.ceil(seconds / 60));
}

function normalizeWaypoints(placeOrWaypoints) {
  if (Array.isArray(placeOrWaypoints)) return placeOrWaypoints;
  return placeOrWaypoints ? [placeOrWaypoints] : [];
}

function samplePath(points, maxPoints = 200) {
  if (points.length <= maxPoints) return points;
  return Array.from({ length: maxPoints }, (_, index) =>
    points[Math.round(index * (points.length - 1) / (maxPoints - 1))]
  );
}

export function walkRoutePath(legs, start, destination, waypoints = []) {
  const routePoints = legs.flatMap((leg) =>
    (leg.steps ?? []).flatMap((step) =>
      (step.path?.points ?? [])
        .filter((point) =>
          Array.isArray(point) &&
          Number.isFinite(point[0]) &&
          Number.isFinite(point[1])
        )
        .map(([longitude, latitude]) => ({ latitude, longitude }))
    )
  );
  const middle = routePoints.length > 0
    ? routePoints
    : waypoints.map(({ latitude, longitude }) => ({ latitude, longitude }));
  return samplePath([start, ...middle, destination]);
}

export function parseKakaoWalkRoute(
  payload,
  start,
  destination,
  placeOrWaypoints = []
) {
  const waypoints = normalizeWaypoints(placeOrWaypoints);
  const routePayload = payload?.status === "OK" ? payload.route : null;
  const properties = routePayload?.properties;
  const legs = routePayload?.legs;
  if (
    !Number.isFinite(properties?.totalTime) ||
    !Number.isFinite(properties?.totalDistance) ||
    !Array.isArray(legs) ||
    legs.length !== waypoints.length + 1 ||
    !legs.every((leg) =>
      Number.isFinite(leg?.properties?.time) &&
      Number.isFinite(leg?.properties?.distance)
    )
  ) {
    return null;
  }

  const durationMinutes = minutes(properties.totalTime);
  const distanceMeters = properties.totalDistance;
  const route = {
    waypointCount: waypoints.length,
    durationMinutes,
    distanceMeters,
    tollFare: 0,
    totalDrivingMinutes: durationMinutes,
    totalDistanceMeters: distanceMeters,
    tollFareWon: 0,
    legs: legs.map((leg) => ({
      // Android 저수준 경로 계약과의 호환을 위해 필드명은 유지한다.
      drivingMinutes: minutes(leg.properties.time),
      distanceMeters: leg.properties.distance
    })),
    path: walkRoutePath(legs, start, destination, waypoints),
    provider: "KAKAO_MAP_WALK"
  };

  if (waypoints.length === 1) {
    const firstLegMinutes = route.legs[0].drivingMinutes;
    const secondLegMinutes = route.legs[1].drivingMinutes;
    const directMinutes = estimateRoute(start, destination, waypoints[0], "WALK").directMinutes;
    Object.assign(route, {
      firstLegMinutes,
      secondLegMinutes,
      directMinutes,
      detourMinutes: Math.max(0, durationMinutes - directMinutes),
      firstLegDistanceMeters: legs[0].properties.distance,
      secondLegDistanceMeters: legs[1].properties.distance
    });
  }

  return route;
}

export async function fetchKakaoWalkRoute(
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
    throw new Error("Kakao walking route accepts at most 5 waypoints");
  }

  const query = new URLSearchParams({
    start_x: String(start.longitude),
    start_y: String(start.latitude),
    end_x: String(destination.longitude),
    end_y: String(destination.latitude),
    input_coord: "WGS84",
    output_coord: "WGS84",
    route_mode: "ACCESSIBLE"
  });
  if (waypoints.length > 0) {
    query.set("via_x", waypoints.map((point) => point.longitude).join(","));
    query.set("via_y", waypoints.map((point) => point.latitude).join(","));
  }

  return usageTracker({
    provider: "KAKAO_LOCAL",
    operation: "WALK_DIRECTIONS",
    budgetLimit: walkBudgetPolicy().budgetLimit,
    signal,
    now,
    call: async () => {
      const response = await fetchWithTimeout(`${walkDirectionsUrl}?${query}`, {
        headers: { authorization: `KakaoAK ${apiKey}` }
      }, {
        provider: "KAKAO_MAP_WALK",
        timeoutMs: NETWORK_TIMEOUT_MS.KAKAO_MOBILITY,
        signal,
        fetchImpl
      });
      if (!response.ok) {
        throw await createProviderResponseError(response, "KAKAO_MAP_WALK", { now });
      }
      const route = parseKakaoWalkRoute(
        await response.json(),
        start,
        destination,
        waypoints
      );
      if (!route) throw new ProviderResponseError("KAKAO_MAP_WALK", 200);
      return route;
    }
  });
}

export async function fetchKakaoWalkRoutes(
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
  let firstError = null;
  let nextIndex = 0;
  const workerCount = Math.min(Math.max(concurrency, 1), places.length);

  async function worker() {
    while (nextIndex < places.length) {
      const place = places[nextIndex];
      nextIndex += 1;
      try {
        const route = await fetchKakaoWalkRoute(start, destination, place, {
          apiKey,
          signal,
          fetchImpl,
          usageTracker,
          now
        });
        if (baseRoute) {
          route.directMinutes = baseRoute.durationMinutes;
          route.detourMinutes = Math.max(
            0,
            route.firstLegMinutes + route.secondLegMinutes - baseRoute.durationMinutes
          );
        }
        routes.set(String(place.content_id), route);
      } catch (error) {
        if (
          error?.code === "UPSTREAM_BUDGET_EXHAUSTED" ||
          error?.code === "UPSTREAM_QUOTA_EXHAUSTED"
        ) {
          throw error;
        }
        failedCount += 1;
        firstError ??= error;
      }
    }
  }

  await Promise.all(Array.from({ length: workerCount }, () => worker()));
  if (places.length > 0 && routes.size === 0) {
    throw firstError ?? new Error("Kakao walking route could not calculate any candidate routes");
  }
  return { routes, failedCount };
}
