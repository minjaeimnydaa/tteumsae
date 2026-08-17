import { estimateRoute } from "./routing.js";

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

export function operationStatus(place, arrival = new Date()) {
  const hours = String(place.opening_hours ?? "").trim();
  const closedDays = String(place.closed_days ?? "").trim();
  if (!hours && !closedDays) return "UNKNOWN";

  const parts = Object.fromEntries(
    new Intl.DateTimeFormat("ko-KR", {
      timeZone: "Asia/Seoul",
      weekday: "short",
      hour: "2-digit",
      minute: "2-digit",
      hourCycle: "h23"
    }).formatToParts(arrival).map(({ type, value }) => [type, value])
  );
  const weekday = parts.weekday?.[0];
  const arrivalMinutes = Number(parts.hour) * 60 + Number(parts.minute);
  const alwaysOpen = /연중무휴|24\s*시간/.test(`${closedDays} ${hours}`);
  const simpleWeeklyClosedDays = /^(?:매주\s*)?[월화수목금토일](?:요일)?(?:\s*[,·/]\s*(?:매주\s*)?[월화수목금토일](?:요일)?)*$/.test(closedDays)
    ? [...closedDays.matchAll(/[월화수목금토일](?=요일|\s*[,·/]|$)/g)].map((match) => match[0])
    : [];
  if (!alwaysOpen && weekday && simpleWeeklyClosedDays.includes(weekday)) {
    return "CLOSED";
  }
  if (alwaysOpen) return "OPEN";

  const ranges = [...hours.matchAll(/(\d{1,2})(?::(\d{2}))?\s*(?:~|-|–|부터)\s*(\d{1,2})(?::(\d{2}))?/g)]
    .map((match) => [
      Number(match[1]) * 60 + Number(match[2] ?? 0),
      Number(match[3]) * 60 + Number(match[4] ?? 0)
    ]);
  if (ranges.length === 0) return "UNKNOWN";

  const isOpen = ranges.some(([start, end]) =>
    end > start
      ? arrivalMinutes >= start && arrivalMinutes < end
      : arrivalMinutes >= start || arrivalMinutes < end
  );
  if (!isOpen) return "CLOSED";

  const understoodClosedDays = !closedDays || /연중무휴/.test(closedDays) ||
    simpleWeeklyClosedDays.length > 0;
  return understoodClosedDays ? "OPEN" : "UNKNOWN";
}

export function selectRouteCandidates(criteria, places, limit = 20) {
  return places
    .filter((place) => matchesCategories(criteria, place))
    .map((place) => {
      const route = estimateRoute(
        criteria.start,
        criteria.destination,
        place,
        criteria.transport
      );
      return {
        place,
        estimatedTotalMinutes:
          route.firstLegMinutes +
          place.default_stay_minutes +
          route.secondLegMinutes,
        estimatedDetourMinutes: route.detourMinutes
      };
    })
    .sort(
      (left, right) =>
        left.estimatedDetourMinutes - right.estimatedDetourMinutes ||
        left.estimatedTotalMinutes - right.estimatedTotalMinutes
    )
    .slice(0, Math.max(1, limit))
    .map((item) => item.place);
}

export function recommendPlaces(
  criteria,
  places,
  routeProvider = estimateRoute,
  now = new Date()
) {
  return places
    .filter((place) => matchesCategories(criteria, place))
    .map((place) => {
      const route = routeProvider(
        criteria.start,
        criteria.destination,
        place,
        criteria.transport
      );
      if (!route) return null;
      const status = operationStatus(
        place,
        new Date(now.getTime() + route.firstLegMinutes * 60_000)
      );
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
    .filter((item) => item.marginMinutes >= criteria.safetyBufferMinutes)
    .sort(
      (left, right) =>
        right.marginMinutes - left.marginMinutes ||
        left.route.detourMinutes - right.route.detourMinutes
    );
}
