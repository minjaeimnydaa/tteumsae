const DAY_INDEX = Object.freeze({ 일: 0, 월: 1, 화: 2, 수: 3, 목: 4, 금: 5, 토: 6 });
const ALL_DAYS = Object.freeze([0, 1, 2, 3, 4, 5, 6]);
const WEEKDAYS = Object.freeze([1, 2, 3, 4, 5]);
const WEEKEND = Object.freeze([0, 6]);

function normalizeText(value) {
  return String(value ?? "")
    .replace(/<br\s*\/?>/gi, " ")
    .replace(/&nbsp;/gi, " ")
    .replace(/[–—]/g, "~")
    .replace(/\s+/g, " ")
    .trim();
}

function localParts(value, timeZone) {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) throw new Error("Invalid operating-hours time");
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat("en-CA", {
      timeZone,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      weekday: "short",
      hour: "2-digit",
      minute: "2-digit",
      hourCycle: "h23"
    }).formatToParts(date).map(({ type, value: part }) => [type, part])
  );
  const weekday = { Sun: 0, Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6 }[parts.weekday];
  const dayOrdinal = Date.UTC(
    Number(parts.year),
    Number(parts.month) - 1,
    Number(parts.day)
  ) / 60_000;
  return {
    weekday,
    dayOrdinal,
    minute: Number(parts.hour) * 60 + Number(parts.minute),
    epochMinute: Math.floor(date.getTime() / 60_000)
  };
}

function parseClock(hour, minute) {
  const h = Number(hour);
  const m = Number(minute ?? 0);
  if (!Number.isInteger(h) || !Number.isInteger(m) || m < 0 || m > 59) return null;
  if (h === 24 && m === 0) return 1_440;
  if (h < 0 || h > 23) return null;
  return h * 60 + m;
}

function dayRange(start, end) {
  const first = DAY_INDEX[start];
  const last = DAY_INDEX[end];
  if (first == null || last == null) return null;
  const days = [first];
  let current = first;
  while (current !== last && days.length <= 7) {
    current = (current + 1) % 7;
    days.push(current);
  }
  return days;
}

function daysForLabel(label) {
  const normalized = normalizeText(label);
  if (!normalized || /^(?:매일|연중무휴)$/.test(normalized)) return ALL_DAYS;
  if (normalized === "평일") return WEEKDAYS;
  if (normalized === "주말") return WEEKEND;
  const range = normalized.match(/^([월화수목금토일])(?:요일)?\s*~\s*([월화수목금토일])(?:요일)?$/);
  if (range) return dayRange(range[1], range[2]);
  const single = normalized.match(/^(?:매주\s*)?([월화수목금토일])(?:요일)?$/);
  return single ? [DAY_INDEX[single[1]]] : null;
}

function parseClosedDays(text) {
  if (!text || /^(?:연중무휴|없음|해당없음)$/.test(text)) {
    return { complete: true, days: new Set() };
  }
  if (/공휴일|명절|임시|변동|별도|문의/.test(text)) {
    return { complete: false, reason: "UNSUPPORTED_HOLIDAY" };
  }
  const stripped = text
    .replace(/매주\s*/g, "")
    .replace(/요일/g, "")
    .replace(/[월화수목금토일]/g, "")
    .replace(/[\s,·/]+/g, "");
  if (stripped) return { complete: false, reason: "AMBIGUOUS_CLOSED_DAYS" };
  const days = new Set(
    [...text.matchAll(/[월화수목금토일](?=요일|\s*[,·/]|\s*$)/g)]
      .map((match) => DAY_INDEX[match[0]])
  );
  return days.size > 0
    ? { complete: true, days }
    : { complete: false, reason: "AMBIGUOUS_CLOSED_DAYS" };
}

function parseAdmission(text) {
  if (!text) return { complete: true, type: "none" };
  const explicit = text.match(/(?:(?:입장|매표)?\s*마감\s*)?(\d{1,2}):(\d{2})/);
  if (explicit) {
    const minute = parseClock(explicit[1], explicit[2]);
    return minute == null
      ? { complete: false, reason: "INVALID_LAST_ADMISSION" }
      : { complete: true, type: "clock", minute };
  }
  const relative = text.match(/(?:운영\s*)?종료\s*(\d{1,3})\s*분\s*전/);
  if (relative) {
    const minutesBefore = Number(relative[1]);
    return minutesBefore > 0 && minutesBefore < 1_440
      ? { complete: true, type: "before_close", minutesBefore }
      : { complete: false, reason: "INVALID_LAST_ADMISSION" };
  }
  return { complete: false, reason: "AMBIGUOUS_LAST_ADMISSION" };
}

function parseSchedule(hours, admissionText) {
  if (!hours) return { complete: false, reason: "MISSING_HOURS" };
  if (/하절기|동절기|여름|겨울|봄철|가을|성수기|비수기|시즌|계절/.test(hours)) {
    return { complete: false, reason: "UNSUPPORTED_SEASON" };
  }
  if (/공휴일|명절|임시|변동|문의/.test(hours)) {
    return { complete: false, reason: "UNSUPPORTED_HOLIDAY" };
  }
  if (
    (/평일/.test(hours) && /[월화수목금](?:요일)/.test(hours)) ||
    (/주말/.test(hours) && /[토일](?:요일)/.test(hours))
  ) {
    return { complete: false, reason: "CONFLICTING_DAY_RULES" };
  }

  if (/24\s*시간/.test(hours)) {
    return {
      complete: true,
      intervalsByDay: new Map(ALL_DAYS.map((day) => [day, [[0, 1_440]]])),
      admission: parseAdmission(admissionText)
    };
  }

  const embeddedAdmissions = [];
  const admissionPattern = String.raw`(?:입장|매표)\s*마감\s*(?:\d{1,2}:\d{2}|(?:운영\s*)?종료\s*\d{1,3}\s*분\s*전)`;
  const withoutAdmission = hours.replace(
    // Remove parentheses only when their entire content is a recognized cutoff.
    // Other annotations remain in the remainder and cannot silently become OPEN.
    new RegExp(`\\(\\s*(${admissionPattern})\\s*\\)|(${admissionPattern})`, "g"),
    (_match, parenthesized, plain) => {
      embeddedAdmissions.push(parenthesized || plain);
      return " ";
    }
  );
  // A single global cutoff cannot represent different day-specific cutoffs.
  if (embeddedAdmissions.length > 1) {
    return { complete: false, reason: "AMBIGUOUS_LAST_ADMISSION" };
  }
  const admission = parseAdmission(admissionText || embeddedAdmissions[0]);
  if (!admission.complete) return { complete: false, reason: admission.reason };

  const intervalPattern = /(?:(평일|주말|매일|연중무휴|(?:매주\s*)?[월화수목금토일](?:요일)?(?:\s*~\s*[월화수목금토일](?:요일)?)?)\s*)?(\d{1,2})(?::(\d{2}))?\s*~\s*(\d{1,2})(?::(\d{2}))?/g;
  const matches = [...withoutAdmission.matchAll(intervalPattern)];
  if (matches.length === 0) return { complete: false, reason: "MISSING_INTERVAL" };

  const remainder = withoutAdmission
    .replace(intervalPattern, " ")
    .replace(/운영시간|이용시간|영업시간/g, " ")
    .replace(/[\s,/;·|]+/g, "");
  if (remainder) return { complete: false, reason: "AMBIGUOUS_HOURS" };

  const intervalsByDay = new Map();
  for (const match of matches) {
    const days = daysForLabel(match[1]);
    const start = parseClock(match[2], match[3]);
    const end = parseClock(match[4], match[5]);
    if (!days || start == null || end == null || start === end || start === 1_440) {
      return { complete: false, reason: "INVALID_INTERVAL" };
    }
    for (const day of days) {
      const intervals = intervalsByDay.get(day) ?? [];
      intervals.push([start, end]);
      intervalsByDay.set(day, intervals);
    }
  }
  return { complete: true, intervalsByDay, admission };
}

function admissionLimit(admission, intervalStart, intervalEnd) {
  if (admission.type === "none") return null;
  if (admission.type === "before_close") return intervalEnd - admission.minutesBefore;
  const endIsOvernight = intervalEnd > 1_440;
  return endIsOvernight && admission.minute <= intervalStart
    ? admission.minute + 1_440
    : admission.minute;
}

export function evaluateOperatingWindow(
  place,
  { arrival, departure, timeZone = "Asia/Seoul" }
) {
  const hours = normalizeText(place.opening_hours);
  const closedDays = normalizeText(place.closed_days);
  const lastAdmission = normalizeText(place.last_admission);
  const arrivalLocal = localParts(arrival, timeZone);
  const departureLocal = localParts(departure, timeZone);
  const closed = parseClosedDays(closedDays);
  if (closed.complete && closed.days.has(arrivalLocal.weekday)) {
    return { status: "CLOSED", reason: "REGULAR_CLOSED_DAY" };
  }

  const schedule = parseSchedule(hours, lastAdmission);
  if (!schedule.complete) return { status: "UNKNOWN", reason: schedule.reason };
  if (!schedule.admission.complete) {
    return { status: "UNKNOWN", reason: schedule.admission.reason };
  }

  const arrivalMinute = arrivalLocal.dayOrdinal + arrivalLocal.minute;
  const departureMinute = departureLocal.dayOrdinal + departureLocal.minute;
  const anchors = [
    { weekday: arrivalLocal.weekday, dayOrdinal: arrivalLocal.dayOrdinal },
    {
      weekday: (arrivalLocal.weekday + 6) % 7,
      dayOrdinal: arrivalLocal.dayOrdinal - 1_440
    }
  ];

  for (const anchor of anchors) {
    if (closed.complete && closed.days.has(anchor.weekday)) continue;
    for (const [start, rawEnd] of schedule.intervalsByDay.get(anchor.weekday) ?? []) {
      const end = rawEnd > start ? rawEnd : rawEnd + 1_440;
      const intervalStart = anchor.dayOrdinal + start;
      const intervalEnd = anchor.dayOrdinal + end;
      if (arrivalMinute < intervalStart || departureMinute > intervalEnd) continue;
      const limit = admissionLimit(schedule.admission, start, end);
      if (limit != null && arrivalMinute > anchor.dayOrdinal + limit) {
        return { status: "CLOSED", reason: "AFTER_LAST_ADMISSION" };
      }
      // Unresolved holidays can only restrict a clear schedule, not make its
      // closed hours open. Preserve UNKNOWN inside that schedule until verified.
      const result = closed.complete
        ? { status: "OPEN", reason: "WITHIN_OPERATING_WINDOW" }
        : { status: "UNKNOWN", reason: closed.reason };
      // The product uses KST (no daylight-saving shift). A full-day interval is
      // not evidence of a midnight closing time, so never invent a cap for it.
      if (timeZone === "Asia/Seoul" && end - start < 1_440) {
        result.closesAtEpochMillis =
          (arrivalLocal.epochMinute + intervalEnd - arrivalMinute) * 60_000;
      }
      return result;
    }
  }
  return { status: "CLOSED", reason: "OUTSIDE_OPERATING_WINDOW" };
}
