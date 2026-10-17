import {
  normalizeTourCommon,
  normalizeTourInfo,
  normalizeTourIntro,
  normalizeTourMedia
} from "./tour-api.js";

export async function runIntroBatch({
  places,
  fetchIntro,
  saveIntro,
  recordFailure,
  concurrency = 4,
  syncedAt,
  signal,
  canStart = () => true
}) {
  const queue = Array.isArray(places) ? places : [];
  const workerCount = Math.min(Math.max(Number.parseInt(concurrency, 10) || 1, 1), 4, queue.length);
  const counts = { processed: 0, deferred: 0, updated: 0, empty: 0, failed: 0 };
  const failureTime = new Date(syncedAt);
  let nextIndex = 0;
  let recordError = null;

  async function worker() {
    while (nextIndex < queue.length) {
      if (!canStart()) break;
      const place = queue[nextIndex];
      nextIndex += 1;
      counts.processed += 1;
      try {
        const intro = await fetchIntro(place.content_id, place.content_type_id, { signal });
        const enrichment = normalizeTourIntro({
          contentTypeId: place.content_type_id,
          intro,
          syncedAt
        });
        await saveIntro(place, enrichment, { signal });
        if (intro === null) counts.empty += 1;
        else counts.updated += 1;
      } catch (error) {
        counts.failed += 1;
        try {
          await recordFailure(place, error, failureTime);
        } catch (failureRecordError) {
          recordError ??= failureRecordError;
        }
      }
    }
  }

  await Promise.all(Array.from({ length: workerCount }, () => worker()));
  counts.deferred = queue.length - counts.processed;
  if (recordError) throw recordError;
  return counts;
}

function emptyPresentationCounts() {
  return {
    processed: 0,
    deferred: 0,
    completed: 0,
    partial: 0,
    failed: 0,
    commonUpdated: 0,
    commonEmpty: 0,
    commonFailed: 0,
    mediaUpdated: 0,
    mediaEmpty: 0,
    mediaFailed: 0,
    infoUpdated: 0,
    infoEmpty: 0,
    infoFailed: 0
  };
}

async function settled(work) {
  try {
    return { fulfilled: true, value: await work() };
  } catch (error) {
    return { fulfilled: false, error };
  }
}

export async function runPresentationBatch({
  places,
  fetchCommon,
  fetchImages,
  fetchPet,
  fetchInfo,
  saveCommon,
  saveMedia,
  saveInfo,
  recordFailure,
  concurrency = 2,
  syncedAt,
  signal,
  canStart = () => true
}) {
  const queue = Array.isArray(places) ? places : [];
  const workerCount = Math.min(
    Math.max(Number.parseInt(concurrency, 10) || 1, 1),
    4,
    queue.length
  );
  const counts = emptyPresentationCounts();
  const failureTime = new Date(syncedAt);
  let nextIndex = 0;
  let recordError = null;

  async function worker() {
    while (nextIndex < queue.length) {
      if (!canStart()) break;
      const place = queue[nextIndex];
      nextIndex += 1;
      counts.processed += 1;

      const needsCommon = place.common_synced_at == null;
      const needsMedia = place.media_synced_at == null;
      const needsInfo = place.info_synced_at === null && !needsCommon && !needsMedia;
      const [commonResult, mediaResult, infoResult] = await Promise.all([
        needsCommon
          ? settled(() => fetchCommon(place.content_id, { signal }))
          : null,
        needsMedia
          ? settled(async () => {
              const [images, pet] = await Promise.all([
                fetchImages(place.content_id, { signal }),
                fetchPet(place.content_id, { signal })
              ]);
              return { images, pet };
            })
          : null,
        needsInfo
          ? settled(() => fetchInfo(
              place.content_id,
              place.content_type_id,
              { signal }
            ))
          : null
      ]);

      const stageFailures = [];
      let completedStages = 0;
      let placeForMedia = place;

      if (commonResult?.fulfilled) {
        try {
          const enrichment = normalizeTourCommon({
            common: commonResult.value,
            syncedAt
          });
          await saveCommon(place, enrichment, { signal });
          placeForMedia = {
            ...place,
            enrichment_raw: {
              ...(place.enrichment_raw ?? {}),
              common: enrichment.common
            }
          };
          completedStages += 1;
          if (enrichment.overview || enrichment.homepageUrl) counts.commonUpdated += 1;
          else counts.commonEmpty += 1;
        } catch (error) {
          counts.commonFailed += 1;
          stageFailures.push({ stage: "common", error });
        }
      } else if (commonResult) {
        counts.commonFailed += 1;
        stageFailures.push({ stage: "common", error: commonResult.error });
      }

      if (mediaResult?.fulfilled) {
        try {
          const enrichment = normalizeTourMedia({
            contentTypeId: place.content_type_id,
            intro: place.enrichment_raw?.intro ?? null,
            images: mediaResult.value.images,
            pet: mediaResult.value.pet,
            syncedAt
          });
          await saveMedia(placeForMedia, enrichment, { signal });
          completedStages += 1;
          if (
            enrichment.imageUrls.length > 0 ||
            enrichment.pet !== null ||
            enrichment.tags.includes("실내 활동")
          ) {
            counts.mediaUpdated += 1;
          } else {
            counts.mediaEmpty += 1;
          }
        } catch (error) {
          counts.mediaFailed += 1;
          stageFailures.push({ stage: "media", error });
        }
      } else if (mediaResult) {
        counts.mediaFailed += 1;
        stageFailures.push({ stage: "media", error: mediaResult.error });
      }

      if (infoResult?.fulfilled) {
        try {
          const enrichment = normalizeTourInfo({
            items: infoResult.value,
            syncedAt
          });
          await saveInfo(place, enrichment, { signal });
          completedStages += 1;
          if (enrichment.infoItems.length > 0) counts.infoUpdated += 1;
          else counts.infoEmpty += 1;
        } catch (error) {
          counts.infoFailed += 1;
          stageFailures.push({ stage: "info", error });
        }
      } else if (infoResult) {
        counts.infoFailed += 1;
        stageFailures.push({ stage: "info", error: infoResult.error });
      }

      if (stageFailures.length === 0) {
        counts.completed += 1;
        continue;
      }

      if (completedStages > 0) counts.partial += 1;
      else counts.failed += 1;
      const failedStages = stageFailures.map(({ stage }) => stage).join(",");
      const failure = new AggregateError(
        stageFailures.map(({ error }) => error),
        `TourAPI presentation stages failed: ${failedStages}`
      );
      try {
        await recordFailure(place, failure, failureTime, { signal });
      } catch (error) {
        recordError ??= error;
      }
    }
  }

  await Promise.all(Array.from({ length: workerCount }, () => worker()));
  counts.deferred = queue.length - counts.processed;
  if (recordError) throw recordError;
  return counts;
}
