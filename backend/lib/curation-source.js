export async function listCurationCandidatesFromApi(
  apiBase,
  { fetchFn = fetch, signal } = {}
) {
  const base = String(apiBase ?? "").trim().replace(/\/$/, "");
  if (!/^https:\/\//i.test(base)) {
    throw new Error("검수 후보 API 기준 주소는 HTTPS여야 합니다.");
  }

  const candidates = [];
  for (let page = 1; page <= 100; page += 1) {
    const url = `${base}/api/places?sigunguCode=1&page=${page}&pageSize=100`;
    const response = await fetchFn(url, { signal });
    if (!response.ok) {
      throw new Error(`검수 후보 API 요청 실패 (${response.status})`);
    }
    const payload = await response.json();
    if (!Array.isArray(payload?.data) || typeof payload?.pagination?.hasMore !== "boolean") {
      throw new Error("검수 후보 API 응답 형식이 올바르지 않습니다.");
    }
    candidates.push(...payload.data);
    if (!payload.pagination.hasMore) return candidates;
  }
  throw new Error("검수 후보 API 페이지가 안전 상한 100개를 초과했습니다.");
}
