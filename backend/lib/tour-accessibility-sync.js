import { normalizeTourAccessibility } from "./tour-accessibility.js";

export async function runAccessibilityBatch({
  items,
  fetchDetail,
  saveAccessibility,
  concurrency = 4,
  syncedAt,
  signal,
  canStart = () => true
}) {
  const queue = Array.isArray(items) ? items : [];
  const counts = {
    processed: 0,
    deferred: 0,
    updated: 0,
    empty: 0,
    unmatched: 0,
    failed: 0
  };
  const workerCount = Math.min(
    Math.max(Number.parseInt(concurrency, 10) || 1, 1),
    4,
    queue.length
  );
  let nextIndex = 0;

  async function worker() {
    while (nextIndex < queue.length) {
      if (!canStart()) break;
      const item = queue[nextIndex];
      nextIndex += 1;
      counts.processed += 1;
      const contentId = String(item?.contentid ?? "").trim();
      if (!contentId) {
        counts.failed += 1;
        continue;
      }
      try {
        const detail = await fetchDetail(contentId, { signal });
        const enrichment = normalizeTourAccessibility({ detail, syncedAt });
        const matched = await saveAccessibility(contentId, enrichment, { signal });
        if (!matched) counts.unmatched += 1;
        else if (enrichment.items.length === 0) counts.empty += 1;
        else counts.updated += 1;
      } catch {
        counts.failed += 1;
      }
    }
  }

  await Promise.all(Array.from({ length: workerCount }, () => worker()));
  counts.deferred = queue.length - counts.processed;
  return counts;
}
