// TourAPI type 12 also contains ancillary/admin facilities. Keep those records
// available for search and place details, but do not offer a long visit there.
const ANCILLARY_FACILITY_SUFFIX = /(?:화장실|주차장|종합사회복지관|행정복지센터|주민센터)$/;
const SENIOR_ASSOCIATION_BRANCH = /^대한노인회.+지회$/;

export function isRecommendationStopSuitable(place) {
  const name = String(place.name ?? "").replace(/\s+/g, "");
  // Exact suffixes, not substring blacklists: "주차장미술관" and a museum
  // converted from an old facility remain eligible. Conversely, a museum's
  // parking lot is still ancillary even if its name starts with the museum.
  return !ANCILLARY_FACILITY_SUFFIX.test(name) &&
    !SENIOR_ASSOCIATION_BRANCH.test(name);
}
