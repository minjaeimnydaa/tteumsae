import { integerEnv, requiredEnv } from "./env.js";
import { fetchWithTimeout, NETWORK_TIMEOUT_MS } from "./fetch-policy.js";
import {
  createProviderResponseError,
  ProviderResponseError,
  trackProviderCall
} from "./provider-usage.js";

const weatherUrl =
  "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst";
const KST_OFFSET_MS = 9 * 60 * 60 * 1_000;
const ISSUE_HOURS = [2, 5, 8, 11, 14, 17, 20, 23];
const OFFICIAL_DEVELOPMENT_DAILY_QUOTA = 10_000;

function finite(value) {
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function validDate(value) {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) throw new Error("Invalid weather time");
  return date;
}

function compactKstDate(date) {
  return [
    date.getUTCFullYear(),
    String(date.getUTCMonth() + 1).padStart(2, "0"),
    String(date.getUTCDate()).padStart(2, "0")
  ].join("");
}

export function toKmaGrid(latitude, longitude) {
  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90) {
    throw new Error("latitude 값이 올바르지 않습니다.");
  }
  if (!Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
    throw new Error("longitude 값이 올바르지 않습니다.");
  }

  const earthRadiusKm = 6_371.00877;
  const gridKm = 5.0;
  const standardLatitude1 = 30.0 * Math.PI / 180.0;
  const standardLatitude2 = 60.0 * Math.PI / 180.0;
  const originLongitude = 126.0 * Math.PI / 180.0;
  const originLatitude = 38.0 * Math.PI / 180.0;
  const originX = 43;
  const originY = 136;
  const re = earthRadiusKm / gridKm;
  let sn = Math.tan(Math.PI * 0.25 + standardLatitude2 * 0.5) /
    Math.tan(Math.PI * 0.25 + standardLatitude1 * 0.5);
  sn = Math.log(Math.cos(standardLatitude1) / Math.cos(standardLatitude2)) / Math.log(sn);
  let sf = Math.tan(Math.PI * 0.25 + standardLatitude1 * 0.5);
  sf = Math.pow(sf, sn) * Math.cos(standardLatitude1) / sn;
  let ro = Math.tan(Math.PI * 0.25 + originLatitude * 0.5);
  ro = re * sf / Math.pow(ro, sn);

  let ra = Math.tan(Math.PI * 0.25 + latitude * Math.PI / 360.0);
  ra = re * sf / Math.pow(ra, sn);
  let theta = longitude * Math.PI / 180.0 - originLongitude;
  if (theta > Math.PI) theta -= 2.0 * Math.PI;
  if (theta < -Math.PI) theta += 2.0 * Math.PI;
  theta *= sn;
  return {
    nx: Math.floor(ra * Math.sin(theta) + originX + 0.5),
    ny: Math.floor(ro - ra * Math.cos(theta) + originY + 0.5)
  };
}

export function latestShortForecastIssue(value = new Date()) {
  const availableKst = new Date(validDate(value).getTime() + KST_OFFSET_MS - 15 * 60 * 1_000);
  const availableHour = availableKst.getUTCHours();
  let issueHour = [...ISSUE_HOURS].reverse().find((hour) => hour <= availableHour);
  let issueKst = availableKst;
  if (issueHour == null) {
    issueHour = 23;
    issueKst = new Date(availableKst.getTime() - 24 * 60 * 60 * 1_000);
  }
  const issuedAtMs = Date.UTC(
    issueKst.getUTCFullYear(),
    issueKst.getUTCMonth(),
    issueKst.getUTCDate(),
    issueHour
  ) - KST_OFFSET_MS;
  return {
    baseDate: compactKstDate(issueKst),
    baseTime: `${String(issueHour).padStart(2, "0")}00`,
    issuedAt: new Date(issuedAtMs).toISOString()
  };
}

export function weatherForecastTarget(value) {
  const arrivalKst = new Date(validDate(value).getTime() + KST_OFFSET_MS);
  const hasPartialHour = arrivalKst.getUTCMinutes() > 0 ||
    arrivalKst.getUTCSeconds() > 0 || arrivalKst.getUTCMilliseconds() > 0;
  arrivalKst.setUTCMinutes(0, 0, 0);
  if (hasPartialHour) arrivalKst.setUTCHours(arrivalKst.getUTCHours() + 1);
  return {
    forecastDate: compactKstDate(arrivalKst),
    forecastTime: `${String(arrivalKst.getUTCHours()).padStart(2, "0")}00`,
    forecastAt: new Date(arrivalKst.getTime() - KST_OFFSET_MS).toISOString()
  };
}

function conditionLabel(precipitationType, skyCode) {
  const precipitation = new Map([
    [1, "비 예상"],
    [2, "비·눈 예상"],
    [3, "눈 예상"],
    [4, "소나기 예상"],
    [5, "빗방울 예상"],
    [6, "빗방울·눈날림 예상"],
    [7, "눈날림 예상"]
  ]);
  if (precipitation.has(precipitationType)) return precipitation.get(precipitationType);
  return new Map([[1, "맑음"], [3, "구름 많음"], [4, "흐림"]]).get(skyCode) ?? null;
}

export function normalizeWeatherForecast(items, { nx, ny, target, issuedAt, fetchedAt }) {
  const values = new Map(items
    .filter((item) => String(item?.fcstDate) === target.forecastDate &&
      String(item?.fcstTime).padStart(4, "0") === target.forecastTime)
    .map((item) => [String(item?.category), item?.fcstValue]));
  const temperature = finite(values.get("TMP"));
  const precipitationProbability = finite(values.get("POP"));
  const precipitationType = finite(values.get("PTY"));
  const skyCode = finite(values.get("SKY"));
  const windSpeed = finite(values.get("WSD"));
  const label = conditionLabel(precipitationType, skyCode);
  if (temperature == null || label == null) return null;
  return {
    nx,
    ny,
    forecast_at: target.forecastAt,
    issued_at: issuedAt,
    condition_label: label,
    temperature_c: temperature,
    precipitation_probability: precipitationProbability != null &&
      precipitationProbability >= 0 && precipitationProbability <= 100
      ? precipitationProbability
      : null,
    precipitation_type: precipitationType,
    sky_code: skyCode,
    wind_speed_mps: windSpeed != null && windSpeed >= 0 ? windSpeed : null,
    fetched_at: fetchedAt
  };
}

export function isWeatherRelevantPlace(place) {
  const tags = Array.isArray(place?.tags) ? place.tags : [];
  if (tags.includes("실내 활동") && !tags.includes("실내·외 활동")) return false;
  return tags.includes("야외 활동") || tags.includes("실내·외 활동") || place?.cat1 === "A01";
}

export async function fetchKmaShortForecast(
  { nx, ny },
  {
    apiKey = requiredEnv("KMA_SHORT_FORECAST_SERVICE_KEY"),
    now = new Date(),
    signal,
    fetchImpl = fetch,
    usageTracker = trackProviderCall
  } = {}
) {
  const issue = latestShortForecastIssue(now);
  const query = new URLSearchParams({
    ServiceKey: apiKey,
    pageNo: "1",
    numOfRows: "1000",
    dataType: "JSON",
    base_date: issue.baseDate,
    base_time: issue.baseTime,
    nx: String(nx),
    ny: String(ny)
  });
  const budgetLimit = Math.min(
    integerEnv("KMA_WEATHER_DAILY_BUDGET", 1_000),
    OFFICIAL_DEVELOPMENT_DAILY_QUOTA
  );
  return usageTracker({
    provider: "KMA",
    operation: "VILAGE_FCST",
    budgetLimit,
    signal,
    now,
    call: async () => {
      const response = await fetchWithTimeout(`${weatherUrl}?${query}`, {
        headers: { accept: "application/json" }
      }, {
        provider: "KMA_WEATHER",
        timeoutMs: NETWORK_TIMEOUT_MS.TOUR_API,
        signal,
        fetchImpl
      });
      if (!response.ok) throw await createProviderResponseError(response, "KMA_WEATHER", { now });
      const payload = await response.json();
      const root = payload?.response ?? payload;
      const resultCode = String(root?.header?.resultCode ?? "");
      if (resultCode !== "00" && resultCode !== "0000") {
        throw new ProviderResponseError("KMA_WEATHER", 200, resultCode);
      }
      const rawItems = root?.body?.items?.item ?? [];
      return {
        issue,
        items: Array.isArray(rawItems) ? rawItems : rawItems ? [rawItems] : []
      };
    }
  });
}

export async function resolvePlaceWeather(
  place,
  arrivalAt,
  {
    now = new Date(),
    loadCache,
    saveCache,
    fetchForecast = fetchKmaShortForecast,
    signal
  }
) {
  if (!isWeatherRelevantPlace(place)) return null;
  const latitude = Number(place?.latitude);
  const longitude = Number(place?.longitude);
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null;
  const grid = toKmaGrid(latitude, longitude);
  const target = weatherForecastTarget(arrivalAt);
  const issue = latestShortForecastIssue(now);
  const cached = await loadCache({
    ...grid,
    forecastAt: target.forecastAt,
    minimumIssuedAt: issue.issuedAt,
    signal
  });
  if (cached) return publicWeatherForecast(cached);

  const response = await fetchForecast(grid, { now, signal });
  const row = normalizeWeatherForecast(response.items, {
    ...grid,
    target,
    issuedAt: response.issue.issuedAt,
    fetchedAt: validDate(now).toISOString()
  });
  if (!row) return null;
  await saveCache(row, { signal });
  return publicWeatherForecast(row);
}

export function publicWeatherForecast(row) {
  if (!row?.forecast_at || !row?.condition_label || !Number.isFinite(Number(row.temperature_c))) {
    return null;
  }
  return {
    forecast_at: String(row.forecast_at),
    condition_label: String(row.condition_label),
    temperature_c: Number(row.temperature_c),
    precipitation_probability: row.precipitation_probability == null
      ? null
      : Number(row.precipitation_probability),
    wind_speed_mps: row.wind_speed_mps == null ? null : Number(row.wind_speed_mps),
    issued_at: String(row.issued_at),
    fetched_at: String(row.fetched_at),
    source: "기상청 단기예보",
    basis: "5km 격자의 도착 무렵 예보"
  };
}
