import { estimateRoute } from "./routing.js";
import { evaluateOperatingWindow } from "./operating-hours.js";
import { isFestivalVisitEligible } from "./festival-eligibility.js";
import { isRecommendationStopSuitable } from "./recommendation-stop-suitability.js";

export const MINIMUM_STAY_MINUTES = 15;
const STAY_ROUNDING_MINUTES = 5;
const ARRIVAL_DEADLINE_TIME_MODEL = "ARRIVAL_DEADLINE_V1";
const CATEGORY_DIVERSITY_DETOUR_MINUTES = 5;

export function safetyLevel(marginMinutes) {
  if (marginMinutes >= 20) return "COMFORTABLE";
  if (marginMinutes >= 10) return "AVAILABLE";
  return "TIGHT";
}

function matchesCategories(criteria, place) {
  return (
    criteria.categories.length === 0 || criteria.categories.includes(place.category)
  );
}

function floorStayMinutes(value) {
  return Math.floor(value / STAY_ROUNDING_MINUTES) * STAY_ROUNDING_MINUTES;
}

function placeInformationScore(place) {
  return Number(place.operating_info_status === "VERIFIED") +
    Number(Boolean(place.image_url?.trim())) +
    Number(Boolean(place.overview?.trim()));
}

function maximumStayWithinOperatingWindow(place, arrival, routeMaximumStayMinutes) {
  const minimumWindow = evaluateOperatingWindow(place, {
    arrival,
    departure: new Date(arrival.getTime() + MINIMUM_STAY_MINUTES * 60_000),
    timeZone: "Asia/Seoul"
  });
  if (minimumWindow.status === "CLOSED") {
    return { maximumStayMinutes: 0, operationStatus: "CLOSED" };
  }

  for (
    let stayMinutes = routeMaximumStayMinutes;
    stayMinutes >= MINIMUM_STAY_MINUTES;
    stayMinutes -= STAY_ROUNDING_MINUTES
  ) {
    const window = evaluateOperatingWindow(place, {
      arrival,
      departure: new Date(arrival.getTime() + stayMinutes * 60_000),
      timeZone: "Asia/Seoul"
    });
    if (window.status !== "CLOSED") {
      const closesAtEpochMillis = window.closesAtEpochMillis;
      return {
        maximumStayMinutes: Number.isFinite(closesAtEpochMillis)
          ? Math.min(stayMinutes, floorStayMinutes((closesAtEpochMillis - arrival.getTime()) / 60_000))
          : stayMinutes,
        operationStatus: window.status,
        closesAtEpochMillis
      };
    }
  }

  return { maximumStayMinutes: 0, operationStatus: "CLOSED" };
}

export function operationStatus(place, arrival = new Date()) {
  const instant = arrival instanceof Date ? arrival : new Date(arrival);
  return evaluateOperatingWindow(place, {
    arrival: instant,
    departure: new Date(instant.getTime() + 1),
    timeZone: "Asia/Seoul"
  }).status;
}

export function selectRouteCandidates(
  criteria,
  places,
  limit = 20,
  now = new Date()
) {
  const isArrivalDeadline = criteria.timeModel === ARRIVAL_DEADLINE_TIME_MODEL;
  const candidates = places
    .filter((place) => matchesCategories(criteria, place))
    .filter(isRecommendationStopSuitable)
    .map((place) => {
      const route = estimateRoute(
        criteria.start,
        criteria.destination,
        place,
        criteria.transport
      );
      return {
        place,
        eligible: isFestivalVisitEligible(
          place,
          new Date(now.getTime() + route.firstLegMinutes * 60_000)
        ),
        estimatedTotalMinutes:
          route.firstLegMinutes +
          (criteria.timeModel === ARRIVAL_DEADLINE_TIME_MODEL
            ? MINIMUM_STAY_MINUTES
            : place.default_stay_minutes) +
          route.secondLegMinutes,
        estimatedDetourMinutes: route.detourMinutes
      };
    })
    .filter((item) => item.eligible)
    .sort(
      (left, right) =>
        left.estimatedDetourMinutes - right.estimatedDetourMinutes ||
        left.estimatedTotalMinutes - right.estimatedTotalMinutes ||
        (isArrivalDeadline
          ? placeInformationScore(right.place) - placeInformationScore(left.place)
          : 0)
    );
  const candidateLimit = Math.max(1, limit);
  if (!isArrivalDeadline || candidates.length <= candidateLimit) {
    return candidates.slice(0, candidateLimit).map((item) => item.place);
  }

  // A dense restaurant catalog must not consume every exact-route slot. Keep
  // the closest option and one option per category only within a small detour
  // band, then fill remaining slots by the same distance order. Actual travel
  // time and opening-window checks still decide which places can be recommended.
  const nearbyDetourLimit = candidates[0].estimatedDetourMinutes +
    CATEGORY_DIVERSITY_DETOUR_MINUTES;
  const selected = new Set();
  const categories = new Set();
  for (const candidate of candidates) {
    if (selected.size >= candidateLimit ||
      candidate.estimatedDetourMinutes > nearbyDetourLimit) break;
    if (categories.has(candidate.place.category)) continue;
    selected.add(candidate);
    categories.add(candidate.place.category);
  }
  for (const candidate of candidates) {
    if (selected.size >= candidateLimit) break;
    selected.add(candidate);
  }
  return [...selected].map((item) => item.place);
}

export function recommendPlaces(
  criteria,
  places,
  routeProvider = estimateRoute,
  now = new Date()
) {
  return places
    .filter((place) => matchesCategories(criteria, place))
    .filter(isRecommendationStopSuitable)
    .map((place) => {
      const route = routeProvider(
        criteria.start,
        criteria.destination,
        place,
        criteria.transport
      );
      if (!route) return null;
      const arrival = new Date(now.getTime() + route.firstLegMinutes * 60_000);
      if (!isFestivalVisitEligible(place, arrival)) return null;

      if (criteria.timeModel === ARRIVAL_DEADLINE_TIME_MODEL) {
        const routeMaximumStayMinutes = floorStayMinutes(
          criteria.deadlineMinutes -
            route.firstLegMinutes -
            route.secondLegMinutes -
            criteria.safetyBufferMinutes
        );
        if (routeMaximumStayMinutes < MINIMUM_STAY_MINUTES) return null;

        const operatingWindow = maximumStayWithinOperatingWindow(
          place,
          arrival,
          routeMaximumStayMinutes
        );
        if (operatingWindow.maximumStayMinutes < MINIMUM_STAY_MINUTES) return null;
        const destinationDepartureEpochMillis = criteria.arrivalDeadlineEpochMillis -
          (route.secondLegMinutes + criteria.safetyBufferMinutes) * 60_000;

        return {
          place,
          route,
          minimumStayMinutes: MINIMUM_STAY_MINUTES,
          maximumStayMinutes: operatingWindow.maximumStayMinutes,
          // Only a known absolute closing time can tighten this deadline. The
          // five-minute rounded display stay must not remove remaining slack.
          latestDepartureEpochMillis: Number.isFinite(operatingWindow.closesAtEpochMillis)
            ? Math.min(destinationDepartureEpochMillis, operatingWindow.closesAtEpochMillis)
            : destinationDepartureEpochMillis,
          operationStatus: operatingWindow.operationStatus
        };
      }

      const departure = new Date(
        arrival.getTime() + Math.max(0, place.default_stay_minutes) * 60_000
      );
      const status = evaluateOperatingWindow(place, {
        arrival,
        departure,
        timeZone: "Asia/Seoul"
      }).status;
      if (status === "CLOSED") return null;
      const totalMinutes =
        route.firstLegMinutes + place.default_stay_minutes + route.secondLegMinutes;
      const marginMinutes = criteria.deadlineMinutes - totalMinutes;

      return {
        place,
        route,
        stayMinutes: place.default_stay_minutes,
        totalMinutes,
        marginMinutes,
        operationStatus: status,
        safetyLevel: safetyLevel(marginMinutes)
      };
    })
    .filter(Boolean)
    .filter((item) =>
      criteria.timeModel === ARRIVAL_DEADLINE_TIME_MODEL ||
      item.marginMinutes >= criteria.safetyBufferMinutes
    )
    .sort(
      (left, right) =>
        (criteria.timeModel === ARRIVAL_DEADLINE_TIME_MODEL
          ? right.maximumStayMinutes - left.maximumStayMinutes
          : right.marginMinutes - left.marginMinutes) ||
        left.route.detourMinutes - right.route.detourMinutes
    );
}
