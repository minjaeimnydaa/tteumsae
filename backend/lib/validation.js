const searchModes = new Set(["ON_THE_WAY", "NEARBY"]);
const transportModes = new Set(["WALK", "CAR"]);
export const ARRIVAL_DEADLINE_TIME_MODEL = "ARRIVAL_DEADLINE_V1";
export const ARRIVAL_DEADLINE_SAFETY_BUFFER_MINUTES = 10;
const recommendationTimeModels = new Set([ARRIVAL_DEADLINE_TIME_MODEL]);
export const placeCategories = new Set([
  "ATTRACTION",
  "RESTAURANT",
  "CAFE",
  "CULTURE",
  "FESTIVAL",
  "SHOPPING",
  "LEISURE"
]);

export function isCoordinates(value) {
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

export function parseRouteRequest(value) {
  if (!value || typeof value !== "object") {
    throw new Error("요청 본문이 올바르지 않습니다.");
  }
  if (!isCoordinates(value.start) || !isCoordinates(value.destination)) {
    throw new Error("출발지와 도착지 좌표가 올바르지 않습니다.");
  }

  const waypoints = value.waypoints ?? [];
  if (!Array.isArray(waypoints) || waypoints.length > 5) {
    throw new Error("waypoints는 최대 5개까지 입력할 수 있습니다.");
  }
  if (!waypoints.every(isCoordinates)) {
    throw new Error("경유지 좌표가 올바르지 않습니다.");
  }

  return {
    start: value.start,
    destination: value.destination,
    waypoints
  };
}

export function parseRecommendationRequest(
  value,
  { nowEpochMillis = Date.now() } = {}
) {
  if (!value || typeof value !== "object") {
    throw new Error("요청 본문이 올바르지 않습니다.");
  }
  if (!searchModes.has(value.mode)) {
    throw new Error("mode는 ON_THE_WAY 또는 NEARBY여야 합니다.");
  }
  if (!isCoordinates(value.start) || !isCoordinates(value.destination)) {
    throw new Error("출발지와 도착지 좌표가 올바르지 않습니다.");
  }
  const timeModel = value.timeModel;
  if (timeModel != null && !recommendationTimeModels.has(timeModel)) {
    throw new Error("지원하지 않는 timeModel입니다.");
  }
  if (!transportModes.has(value.transport)) {
    throw new Error("transport는 WALK 또는 CAR여야 합니다.");
  }

  const categories = value.categories ?? [];
  if (!Array.isArray(categories) || !categories.every((item) => placeCategories.has(item))) {
    throw new Error("지원하지 않는 장소 유형이 포함되어 있습니다.");
  }

  if (timeModel === ARRIVAL_DEADLINE_TIME_MODEL) {
    const forbiddenFields = [
      "deadlineMinutes",
      "extraTimeMinutes",
      "safetyBufferMinutes"
    ].filter((field) => Object.hasOwn(value, field));
    if (forbiddenFields.length > 0) {
      throw new Error(
        `ARRIVAL_DEADLINE_V1에는 ${forbiddenFields.join(", ")}를 함께 입력할 수 없습니다.`
      );
    }
    if (!Number.isInteger(value.arrivalDeadlineEpochMillis)) {
      throw new Error("arrivalDeadlineEpochMillis는 정수여야 합니다.");
    }
    if (!Number.isSafeInteger(value.arrivalDeadlineEpochMillis)) {
      throw new Error("arrivalDeadlineEpochMillis는 안전한 정수여야 합니다.");
    }
    const remainingMillis = value.arrivalDeadlineEpochMillis - nowEpochMillis;
    if (remainingMillis < 15 * 60_000) {
      throw new Error("도착 마감은 현재로부터 15분 이상이어야 합니다.");
    }
    if (remainingMillis > 24 * 60 * 60_000) {
      throw new Error("도착 마감은 현재로부터 24시간 이내여야 합니다.");
    }

    return {
      mode: value.mode,
      start: value.start,
      destination: value.destination,
      arrivalDeadlineEpochMillis: value.arrivalDeadlineEpochMillis,
      deadlineMinutes: Math.floor(remainingMillis / 60_000),
      safetyBufferMinutes: ARRIVAL_DEADLINE_SAFETY_BUFFER_MINUTES,
      transport: value.transport,
      categories,
      timeModel
    };
  }

  if (Object.hasOwn(value, "arrivalDeadlineEpochMillis")) {
    throw new Error("arrivalDeadlineEpochMillis는 ARRIVAL_DEADLINE_V1에서만 사용할 수 있습니다.");
  }
  const hasDeadline = Object.hasOwn(value, "deadlineMinutes");
  const hasExtraTime = Object.hasOwn(value, "extraTimeMinutes");
  if (hasDeadline === hasExtraTime) {
    throw new Error(
      "deadlineMinutes와 extraTimeMinutes 중 하나만 입력해야 합니다."
    );
  }
  const timeBudgetMinutes = hasExtraTime
    ? value.extraTimeMinutes
    : value.deadlineMinutes;
  if (
    !Number.isInteger(timeBudgetMinutes) ||
    timeBudgetMinutes < 15 ||
    timeBudgetMinutes > 1440
  ) {
    const field = hasExtraTime ? "extraTimeMinutes" : "deadlineMinutes";
    throw new Error(`${field}는 15~1440 사이의 정수여야 합니다.`);
  }
  if (
    !Number.isInteger(value.safetyBufferMinutes) ||
    value.safetyBufferMinutes < 0 ||
    value.safetyBufferMinutes > 60
  ) {
    throw new Error("safetyBufferMinutes는 0~60 사이의 정수여야 합니다.");
  }
  if (value.safetyBufferMinutes >= timeBudgetMinutes) {
    throw new Error("안전 여유시간은 입력한 시간 예산보다 짧아야 합니다.");
  }

  return {
    mode: value.mode,
    start: value.start,
    destination: value.destination,
    ...(hasExtraTime
      ? { extraTimeMinutes: value.extraTimeMinutes }
      : { deadlineMinutes: value.deadlineMinutes }),
    safetyBufferMinutes: value.safetyBufferMinutes,
    transport: value.transport,
    categories,
    ...(timeModel ? { timeModel } : {})
  };
}
