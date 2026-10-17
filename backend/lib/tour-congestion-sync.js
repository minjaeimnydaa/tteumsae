import { prepareCongestionRows } from "./tour-congestion.js";

export async function runTourCongestionSync({
  fetchPage,
  listPlaces,
  saveRows,
  pageLimit = 20,
  pageSize = 1_000,
  fetchedAt,
  signal,
  canStart = () => true
}) {
  const places = await listPlaces({ signal });
  let pageNo = 1;
  let processedPages = 0;
  let sourceRows = 0;
  let savedRows = 0;
  let matchedRows = 0;
  let ambiguousRows = 0;
  let unmatchedRows = 0;
  let completed = false;

  while (processedPages < pageLimit) {
    if (!canStart()) break;
    const page = await fetchPage(pageNo, pageSize, { signal });
    const scopedItems = page.items.filter((item) =>
      String(item?.areaCd ?? "") === "51" &&
      String(item?.signguCd ?? "") === "51150"
    );
    const rows = prepareCongestionRows(scopedItems, places, fetchedAt);
    await saveRows(rows, { signal });

    processedPages += 1;
    sourceRows += page.items.length;
    savedRows += rows.length;
    matchedRows += rows.filter((row) => row.match_status === "MATCHED").length;
    ambiguousRows += rows.filter((row) => row.match_status === "AMBIGUOUS").length;
    unmatchedRows += rows.filter((row) => row.match_status === "UNMATCHED").length;

    const totalPages = Math.max(Math.ceil(page.totalCount / page.numOfRows), 1);
    completed = page.pageNo >= totalPages || page.items.length === 0;
    if (completed) break;
    pageNo = page.pageNo + 1;
  }

  return {
    status: completed ? "completed" : "partial",
    processedPages,
    sourceRows,
    savedRows,
    matchedRows,
    ambiguousRows,
    unmatchedRows,
    nextPage: completed ? null : pageNo
  };
}
