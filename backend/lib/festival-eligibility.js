function validIsoDate(value) {
  const match = String(value ?? "").match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!match) return null;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const date = new Date(Date.UTC(year, month - 1, day));
  if (
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() !== month - 1 ||
    date.getUTCDate() !== day
  ) {
    return null;
  }
  return `${match[1]}-${match[2]}-${match[3]}`;
}

function dateInTimeZone(value, timeZone) {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) throw new Error("Invalid festival visit time");
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat("en-CA", {
      timeZone,
      year: "numeric",
      month: "2-digit",
      day: "2-digit"
    }).formatToParts(date).map(({ type, value: part }) => [type, part])
  );
  return `${parts.year}-${parts.month}-${parts.day}`;
}

export function isFestival(place) {
  return Number(place?.content_type_id) === 15 || place?.category === "FESTIVAL";
}

export function isFestivalVisitEligible(
  place,
  arrival,
  timeZone = "Asia/Seoul"
) {
  if (!isFestival(place)) return true;
  const start = validIsoDate(place?.event_start_date);
  const end = validIsoDate(place?.event_end_date);
  if (!start || !end || start > end) return false;
  const visitDate = dateInTimeZone(arrival, timeZone);
  return start <= visitDate && visitDate <= end;
}
