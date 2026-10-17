export const CURATION_COLUMNS = Object.freeze([
  "content_id",
  "name",
  "category",
  "operating_info_status",
  "opening_hours",
  "closed_days",
  "last_admission",
  "admission_info_status",
  "parking_info",
  "parking_info_status",
  "source_urls",
  "source_checked_at",
  "reviewed_at",
  "review_note"
]);

function parseRecords(text) {
  const records = [];
  let record = [];
  let field = "";
  let inQuotes = false;
  const source = String(text ?? "").replace(/^\uFEFF/, "");

  for (let index = 0; index < source.length; index += 1) {
    const character = source[index];
    if (inQuotes) {
      if (character === '"') {
        if (source[index + 1] === '"') {
          field += '"';
          index += 1;
        } else {
          inQuotes = false;
        }
      } else {
        field += character;
      }
      continue;
    }
    if (character === '"' && field === "") {
      inQuotes = true;
    } else if (character === ",") {
      record.push(field);
      field = "";
    } else if (character === "\n" || character === "\r") {
      if (character === "\r" && source[index + 1] === "\n") index += 1;
      record.push(field);
      if (record.some((value) => value !== "")) records.push(record);
      record = [];
      field = "";
    } else {
      field += character;
    }
  }
  if (inQuotes) throw new Error("CSV 따옴표가 닫히지 않았습니다.");
  record.push(field);
  if (record.some((value) => value !== "")) records.push(record);
  return records;
}

export function parseCurationCsv(text) {
  const records = parseRecords(text);
  if (records.length === 0) return [];
  const header = records[0];
  if (
    header.length !== CURATION_COLUMNS.length ||
    !header.every((column, index) => column === CURATION_COLUMNS[index])
  ) {
    throw new Error(`CSV 헤더는 ${CURATION_COLUMNS.join(",")} 순서여야 합니다.`);
  }
  return records.slice(1).map((record, rowIndex) => {
    if (record.length !== header.length) {
      throw new Error(`${rowIndex + 2}행의 열 수가 ${header.length}개가 아닙니다.`);
    }
    return Object.fromEntries(header.map((column, index) => [column, record[index]]));
  });
}

function quoteCsv(value) {
  const text = String(value ?? "");
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

export function serializeCurationCsv(rows) {
  const lines = [CURATION_COLUMNS.join(",")];
  for (const row of rows) {
    lines.push(CURATION_COLUMNS.map((column) => quoteCsv(row[column])).join(","));
  }
  return `${lines.join("\n")}\n`;
}

function parsedTimestamp(value, field, rowNumber, errors) {
  const time = Date.parse(value);
  if (!value || Number.isNaN(time)) {
    errors.push(`${rowNumber}행 ${field}은 유효한 ISO 시각이어야 합니다.`);
    return null;
  }
  return new Date(time).toISOString();
}

function parsedSourceUrls(value, rowNumber, errors) {
  let urls;
  try {
    urls = JSON.parse(value);
  } catch {
    urls = null;
  }
  if (!Array.isArray(urls) || urls.length === 0) {
    errors.push(`${rowNumber}행 source_urls는 하나 이상의 HTTPS URL 배열이어야 합니다.`);
    return [];
  }
  if (urls.some((url) => {
    try {
      return new URL(url).protocol !== "https:";
    } catch {
      return true;
    }
  })) {
    errors.push(`${rowNumber}행 source_urls는 유효한 HTTPS URL만 허용합니다.`);
  }
  return urls;
}

export function validateCurationRows(
  rows,
  { expectedCount, allowPartial = false } = {}
) {
  const errors = [];
  if (Number.isInteger(expectedCount) && rows.length !== expectedCount) {
    errors.push(`검수 행은 정확히 ${expectedCount}개여야 하지만 ${rows.length}개입니다.`);
  }
  const seen = new Set();
  const normalized = [];
  for (const [index, row] of rows.entries()) {
    const rowNumber = index + 2;
    const contentId = String(row.content_id ?? "").trim();
    if (!contentId) errors.push(`${rowNumber}행 content_id가 비어 있습니다.`);
    if (seen.has(contentId)) errors.push(`${rowNumber}행 중복 content_id: ${contentId}`);
    seen.add(contentId);
    if (!String(row.name ?? "").trim()) errors.push(`${rowNumber}행 name이 비어 있습니다.`);
    if (!String(row.category ?? "").trim()) errors.push(`${rowNumber}행 category가 비어 있습니다.`);

    if (!["VERIFIED", "UNKNOWN"].includes(row.operating_info_status)) {
      errors.push(`${rowNumber}행 operating_info_status가 올바르지 않습니다.`);
    }
    if (!["VERIFIED", "NOT_APPLICABLE", "UNKNOWN"].includes(row.admission_info_status)) {
      errors.push(`${rowNumber}행 admission_info_status가 올바르지 않습니다.`);
    }
    if (!["VERIFIED", "UNKNOWN"].includes(row.parking_info_status)) {
      errors.push(`${rowNumber}행 parking_info_status가 올바르지 않습니다.`);
    }

    const reviewed = Boolean(String(row.reviewed_at ?? "").trim());
    if (allowPartial && !reviewed) continue;
    if (row.operating_info_status === "VERIFIED" && !String(row.opening_hours ?? "").trim()) {
      errors.push(`${rowNumber}행 operating VERIFIED에는 opening_hours가 필수입니다.`);
    }
    if (row.admission_info_status === "VERIFIED" && !String(row.last_admission ?? "").trim()) {
      errors.push(`${rowNumber}행 admission VERIFIED에는 last_admission이 필수입니다.`);
    }
    if (row.parking_info_status === "VERIFIED" && !String(row.parking_info ?? "").trim()) {
      errors.push(`${rowNumber}행 parking VERIFIED에는 parking_info가 필수입니다.`);
    }

    const sourceUrls = parsedSourceUrls(row.source_urls, rowNumber, errors);
    const sourceCheckedAt = parsedTimestamp(
      row.source_checked_at,
      "source_checked_at",
      rowNumber,
      errors
    );
    const reviewedAt = parsedTimestamp(row.reviewed_at, "reviewed_at", rowNumber, errors);
    normalized.push({
      content_id: contentId,
      operating_info_status: row.operating_info_status,
      opening_hours: String(row.opening_hours ?? "").trim() || null,
      closed_days: String(row.closed_days ?? "").trim() || null,
      last_admission: String(row.last_admission ?? "").trim() || null,
      admission_info_status: row.admission_info_status,
      parking_info: String(row.parking_info ?? "").trim() || null,
      parking_info_status: row.parking_info_status,
      source_urls: sourceUrls,
      source_checked_at: sourceCheckedAt,
      reviewed_at: reviewedAt,
      review_note: String(row.review_note ?? "").trim() || null
    });
  }
  if (errors.length > 0) throw new Error(errors.join("\n"));
  return normalized;
}

function qualityScore(place) {
  return (place.image_url ? 4 : 0) + (place.overview ? 2 : 0) +
    (place.intro_synced_at ? 1 : 0);
}

const categoryTargets = Object.freeze({
  ATTRACTION: 30,
  CULTURE: 8,
  FESTIVAL: 3,
  LEISURE: 20,
  CAFE: 12,
  RESTAURANT: 22,
  SHOPPING: 5
});

const nonDestinationNamePattern = new RegExp([
  "화장실",
  "종합사회복지관",
  "대한노인회",
  "관광안내센터",
  "문화교육센터",
  "여객터미널",
  "공설운동장",
  "국민체육센터",
  "생활체육센터",
  "실내체육관",
  "스쿼시장",
  "볼링장",
  "복권마트",
  "식자재마트",
  "다이소",
  "베이비하우스",
  "배스킨라빈스",
  "컴포즈커피",
  "이디야커피"
].join("|"));

export function selectCurationCandidates(candidates, limit = 100) {
  const unique = [...new Map(
    candidates
      .filter((place) =>
        String(place?.content_id ?? "").trim() &&
        !nonDestinationNamePattern.test(String(place?.name ?? ""))
      )
      .map((place) => [String(place.content_id), place])
  ).values()];
  const groups = new Map();
  for (const place of unique) {
    const category = String(place.category ?? "UNKNOWN");
    const group = groups.get(category) ?? [];
    group.push(place);
    groups.set(category, group);
  }
  for (const group of groups.values()) {
    group.sort((left, right) =>
      qualityScore(right) - qualityScore(left) ||
      String(left.name ?? "").localeCompare(String(right.name ?? ""), "ko")
    );
  }

  const selected = [];
  for (const [category, targetAt100] of Object.entries(categoryTargets)) {
    const group = groups.get(category) ?? [];
    const target = Math.floor(limit * targetAt100 / 100);
    selected.push(...group.splice(0, Math.min(target, group.length)));
  }

  const categories = [...groups.keys()].sort();
  while (selected.length < limit) {
    let added = false;
    for (const category of categories) {
      const place = groups.get(category).shift();
      if (!place) continue;
      selected.push(place);
      added = true;
      if (selected.length === limit) break;
    }
    if (!added) break;
  }
  return selected;
}
