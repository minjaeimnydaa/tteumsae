const DATA_GO_KR_SOURCE = "https://www.data.go.kr/data/15101578/openapi.do";

const introFields = Object.freeze({
  12: { hours: "usetime", closed: "restdate", parking: "parking" },
  14: { hours: "usetimeculture", closed: "restdateculture", parking: "parkingculture" },
  15: { hours: "playtime", closed: null, parking: "parking" },
  28: { hours: "usetimeleports", closed: "restdateleports", parking: "parkingleports" },
  38: { hours: "opentime", closed: "restdateshopping", parking: "parkingshopping" },
  39: { hours: "opentimefood", closed: "restdatefood", parking: "parkingfood" }
});

function cleanText(value) {
  return String(value ?? "")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/gi, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function officialHomepage(value) {
  const raw = String(value ?? "").trim();
  const href = raw.match(/href\s*=\s*(["'])(.*?)\1/i)?.[2];
  const candidate = (href ?? cleanText(raw)).replace(/&amp;/gi, "&").trim();
  try {
    const url = new URL(candidate);
    return url.protocol === "https:" ? url.toString() : null;
  } catch {
    return null;
  }
}

function explicitLastAdmission(hours) {
  const match = String(hours ?? "").match(
    /(?:입장\s*마감|매표\s*마감)\s*[:：]?\s*([01]?\d|2[0-3]):([0-5]\d)/
  );
  return match ? `${match[1].padStart(2, "0")}:${match[2]}` : null;
}

export function applyTourResearch(rows, researchItems, checkedAt) {
  const timestamp = new Date(checkedAt);
  if (Number.isNaN(timestamp.getTime())) throw new Error("검수 시각이 올바르지 않습니다.");
  const iso = timestamp.toISOString();
  const researchById = new Map(
    researchItems.map((item) => [String(item.contentId), item])
  );

  return rows.map((row) => {
    const contentId = String(row.content_id);
    const research = researchById.get(contentId);
    if (!research) throw new Error(`TourAPI 검수 응답 누락: ${contentId}`);
    const fields = introFields[Number(research.contentTypeId)] ?? {};
    const intro = research.intro ?? {};
    const openingHours = fields.hours ? cleanText(intro[fields.hours]) : "";
    const closedDays = fields.closed ? cleanText(intro[fields.closed]) : "";
    const parkingInfo = fields.parking ? cleanText(intro[fields.parking]) : "";
    const lastAdmission = explicitLastAdmission(openingHours);
    const admissionNotApplicable = [38, 39].includes(Number(research.contentTypeId)) ||
      /^(상시\s*개방|24시간)/.test(openingHours);
    const homepage = officialHomepage(research.common?.homepage);
    const sources = homepage
      ? [DATA_GO_KR_SOURCE, homepage]
      : [DATA_GO_KR_SOURCE];
    const unknown = [];
    if (!openingHours) unknown.push("운영시간");
    if (!parkingInfo) unknown.push("주차");
    if (!lastAdmission && !admissionNotApplicable) unknown.push("입장마감");
    const eventPeriod = research.intro?.eventstartdate || research.intro?.eventenddate
      ? ` 행사기간 ${research.intro?.eventstartdate ?? "?"}~${research.intro?.eventenddate ?? "?"}.`
      : "";
    const unknownNote = unknown.length > 0
      ? ` ${unknown.join("·")}은 공식 응답에 명시되지 않아 추정하지 않음.`
      : "";

    return {
      ...row,
      operating_info_status: openingHours ? "VERIFIED" : "UNKNOWN",
      opening_hours: openingHours,
      closed_days: closedDays,
      last_admission: lastAdmission ?? "",
      admission_info_status: lastAdmission
        ? "VERIFIED"
        : admissionNotApplicable
          ? "NOT_APPLICABLE"
          : "UNKNOWN",
      parking_info: parkingInfo,
      parking_info_status: parkingInfo ? "VERIFIED" : "UNKNOWN",
      source_urls: JSON.stringify(sources),
      source_checked_at: iso,
      reviewed_at: iso,
      review_note: `한국관광공사 TourAPI detailIntro2/detailCommon2 contentId=${contentId} 확인.${eventPeriod}${unknownNote}`
    };
  });
}
