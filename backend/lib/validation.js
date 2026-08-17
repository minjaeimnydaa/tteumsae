const searchModes = new Set(["ON_THE_WAY", "NEARBY"]);
const transportModes = new Set(["WALK", "CAR"]);
export const placeCategories = new Set([
  "ATTRACTION",
  "RESTAURANT",
  "CAFE",
  "CULTURE",
  "FESTIVAL",
  "SHOPPING",
  "LEISURE"
]);

function isCoordinates(value) {
  return Boolean(
    value &&
      typeof value === "object" &&
      typeof value.latitude === "number" &&
      value.latitude >= -90 &&
      value.latitude <= 90 &&
      typeof value.longitude === "number" &&
      value.longitude >= -180 &&
      value.longitude <= 180
  );
}

export function parseRecommendationRequest(value) {
  if (!value || typeof value !== "object") {
    throw new Error("요청 본문이 올바르지 않습니다.");
  }
  if (!searchModes.has(value.mode)) {
    throw new Error("mode는 ON_THE_WAY 또는 NEARBY여야 합니다.");
  }
  if (!isCoordinates(value.start) || !isCoordinates(value.destination)) {
    throw new Error("출발지와 도착지 좌표가 올바르지 않습니다.");
  }
  if (
    !Number.isInteger(value.deadlineMinutes) ||
    value.deadlineMinutes < 15 ||
    value.deadlineMinutes > 1440
  ) {
    throw new Error("deadlineMinutes는 15~1440 사이의 정수여야 합니다.");
  }
  if (
    !Number.isInteger(value.safetyBufferMinutes) ||
    value.safetyBufferMinutes < 0 ||
    value.safetyBufferMinutes > 60
  ) {
    throw new Error("safetyBufferMinutes는 0~60 사이의 정수여야 합니다.");
  }
  if (value.safetyBufferMinutes >= value.deadlineMinutes) {
    throw new Error("안전 여유시간은 전체 남은 시간보다 짧아야 합니다.");
  }
  if (!transportModes.has(value.transport)) {
    throw new Error("transport는 WALK 또는 CAR여야 합니다.");
  }

  const categories = value.categories ?? [];
  if (!Array.isArray(categories) || !categories.every((item) => placeCategories.has(item))) {
    throw new Error("지원하지 않는 장소 유형이 포함되어 있습니다.");
  }

  return {
    mode: value.mode,
    start: value.start,
    destination: value.destination,
    deadlineMinutes: value.deadlineMinutes,
    safetyBufferMinutes: value.safetyBufferMinutes,
    transport: value.transport,
    categories
  };
}
