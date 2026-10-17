import test from "node:test";
import assert from "node:assert/strict";

async function passthroughLease({ run }) {
  return run();
}

function activeDeadline() {
  return {
    signal: new AbortController().signal,
    canStart: () => true,
    dispose: () => {}
  };
}

test("presentation 배치는 common과 media의 부분 성공을 서로 지우지 않는다", async () => {
  const { runPresentationBatch } = await import("../lib/tour-sync.js");
  const savedCommon = [];
  const savedMedia = [];
  const failures = [];
  const signal = new AbortController().signal;

  const result = await runPresentationBatch({
    places: [
      {
        content_id: "common-fails",
        content_type_id: 12,
        common_synced_at: null,
        media_synced_at: null,
        enrichment_raw: { intro: { parking: "가능" } }
      },
      {
        content_id: "media-fails",
        content_type_id: 14,
        common_synced_at: null,
        media_synced_at: null,
        enrichment_raw: { intro: { parkingculture: "가능" } }
      }
    ],
    concurrency: 2,
    syncedAt: "2026-09-05T00:00:00.000Z",
    signal,
    fetchCommon: async (contentId, options) => {
      assert.equal(options.signal, signal);
      if (contentId === "common-fails") throw new Error("common timeout");
      return { overview: "새 소개", homepage: "https://example.com/place" };
    },
    fetchImages: async (contentId, options) => {
      assert.equal(options.signal, signal);
      if (contentId === "media-fails") throw new Error("image timeout");
      return [{ originimgurl: "https://example.com/image.jpg" }];
    },
    fetchPet: async (_contentId, options) => {
      assert.equal(options.signal, signal);
      return null;
    },
    saveCommon: async (place, enrichment, options) => {
      assert.equal(options.signal, signal);
      savedCommon.push({ place, enrichment });
    },
    saveMedia: async (place, enrichment, options) => {
      assert.equal(options.signal, signal);
      savedMedia.push({ place, enrichment });
    },
    recordFailure: async (place, error, now, options) => {
      assert.equal(options.signal, signal);
      failures.push({ place, error, now });
    }
  });

  assert.deepEqual(result, {
    processed: 2,
    deferred: 0,
    completed: 0,
    partial: 2,
    failed: 0,
    commonUpdated: 1,
    commonEmpty: 0,
    commonFailed: 1,
    mediaUpdated: 1,
    mediaEmpty: 0,
    mediaFailed: 1,
    infoUpdated: 0,
    infoEmpty: 0,
    infoFailed: 0
  });
  assert.deepEqual(savedCommon.map(({ place }) => place.content_id), ["media-fails"]);
  assert.deepEqual(savedMedia.map(({ place }) => place.content_id), ["common-fails"]);
  assert.equal(savedCommon[0].enrichment.overview, "새 소개");
  assert.deepEqual(savedMedia[0].enrichment.imageUrls, ["https://example.com/image.jpg"]);
  assert.deepEqual(failures.map(({ place }) => place.content_id).sort(), [
    "common-fails",
    "media-fails"
  ]);
  assert.ok(failures.every(({ now }) => now.toISOString() === "2026-09-05T00:00:00.000Z"));
});

test("presentation 배치는 빈 정상응답도 저장해 두 stage를 완료 처리한다", async () => {
  const { runPresentationBatch } = await import("../lib/tour-sync.js");
  const saved = [];

  const result = await runPresentationBatch({
    places: [{
      content_id: "empty",
      content_type_id: 12,
      common_synced_at: null,
      media_synced_at: null,
      enrichment_raw: { intro: null }
    }],
    concurrency: 1,
    syncedAt: "2026-09-05T00:00:00.000Z",
    fetchCommon: async () => null,
    fetchImages: async () => [],
    fetchPet: async () => null,
    saveCommon: async (place, enrichment) => saved.push({ stage: "common", place, enrichment }),
    saveMedia: async (place, enrichment) => saved.push({ stage: "media", place, enrichment }),
    recordFailure: async () => assert.fail("빈 정상응답은 실패가 아닙니다.")
  });

  assert.deepEqual(result, {
    processed: 1,
    deferred: 0,
    completed: 1,
    partial: 0,
    failed: 0,
    commonUpdated: 0,
    commonEmpty: 1,
    commonFailed: 0,
    mediaUpdated: 0,
    mediaEmpty: 1,
    mediaFailed: 0,
    infoUpdated: 0,
    infoEmpty: 0,
    infoFailed: 0
  });
  assert.equal(saved.length, 2);
  assert.equal(saved[0].enrichment.common, null);
  assert.equal(saved[0].enrichment.syncedAt, "2026-09-05T00:00:00.000Z");
  assert.deepEqual(saved[1].enrichment.images, []);
  assert.equal(saved[1].enrichment.pet, null);
  assert.equal(saved[1].enrichment.syncedAt, "2026-09-05T00:00:00.000Z");
});

test("presentation 배치는 완료 stage를 건너뛰고 common raw를 media 저장에 보존한다", async () => {
  const { runPresentationBatch } = await import("../lib/tour-sync.js");
  const savedMediaPlaces = [];

  await runPresentationBatch({
    places: [
      {
        content_id: "media-only",
        content_type_id: 12,
        common_synced_at: "2026-09-04T00:00:00.000Z",
        media_synced_at: null,
        enrichment_raw: { common: { overview: "기존 소개" }, intro: {} }
      },
      {
        content_id: "both",
        content_type_id: 12,
        common_synced_at: null,
        media_synced_at: null,
        enrichment_raw: { intro: {} }
      }
    ],
    concurrency: 1,
    syncedAt: "2026-09-05T00:00:00.000Z",
    fetchCommon: async (contentId) => ({ overview: `${contentId} 소개` }),
    fetchImages: async () => [],
    fetchPet: async () => null,
    saveCommon: async () => {},
    saveMedia: async (place) => savedMediaPlaces.push(place),
    recordFailure: async () => {}
  });

  assert.deepEqual(savedMediaPlaces[0].enrichment_raw.common, { overview: "기존 소개" });
  assert.deepEqual(savedMediaPlaces[1].enrichment_raw.common, { overview: "both 소개" });
});

test("presentation 배치는 deadline 뒤 새 장소를 시작하지 않는다", async () => {
  const { runPresentationBatch } = await import("../lib/tour-sync.js");
  let admissions = 0;
  const started = [];

  const result = await runPresentationBatch({
    places: ["1", "2"].map((content_id) => ({
      content_id,
      content_type_id: 12,
      common_synced_at: null,
      media_synced_at: "2026-09-04T00:00:00.000Z",
      enrichment_raw: {}
    })),
    concurrency: 1,
    syncedAt: "2026-09-05T00:00:00.000Z",
    canStart: () => admissions++ < 1,
    fetchCommon: async (contentId) => {
      started.push(contentId);
      return null;
    },
    fetchImages: async () => assert.fail("완료 media는 호출하지 않습니다."),
    fetchPet: async () => assert.fail("완료 media는 호출하지 않습니다."),
    saveCommon: async () => {},
    saveMedia: async () => {},
    recordFailure: async () => {}
  });

  assert.deepEqual(started, ["1"]);
  assert.equal(result.processed, 1);
  assert.equal(result.deferred, 1);
  assert.equal(result.completed, 1);
});

test("presentation 배치는 기존 stage가 끝난 장소에서 반복 상세를 별도 단계로 동기화한다", async () => {
  const { runPresentationBatch } = await import("../lib/tour-sync.js");
  const saved = [];

  const result = await runPresentationBatch({
    places: [{
      content_id: "info-only",
      content_type_id: 15,
      common_synced_at: "2026-09-06T00:00:00.000Z",
      media_synced_at: "2026-09-06T00:00:00.000Z",
      info_synced_at: null,
      enrichment_raw: { common: {}, images: [] }
    }],
    concurrency: 1,
    syncedAt: "2026-09-07T00:00:00.000Z",
    fetchCommon: async () => assert.fail("완료 common은 호출하지 않습니다."),
    fetchImages: async () => assert.fail("완료 media는 호출하지 않습니다."),
    fetchPet: async () => assert.fail("완료 media는 호출하지 않습니다."),
    fetchInfo: async (contentId, contentTypeId) => {
      assert.equal(contentId, "info-only");
      assert.equal(contentTypeId, 15);
      return [{ infoname: "행사 안내", infotext: "우천 시 취소" }];
    },
    saveCommon: async () => {},
    saveMedia: async () => {},
    saveInfo: async (place, enrichment) => saved.push({ place, enrichment }),
    recordFailure: async () => assert.fail("정상 반복 상세는 실패가 아닙니다.")
  });

  assert.equal(result.completed, 1);
  assert.equal(result.infoUpdated, 1);
  assert.equal(result.infoEmpty, 0);
  assert.equal(result.infoFailed, 0);
  assert.deepEqual(saved[0].enrichment.infoItems, [
    { title: "행사 안내", description: "우천 시 취소" }
  ]);
});

test("presentation 대상은 intro 완료·미완료 stage·재시도 도래 조건과 최대 10개를 사용한다", async () => {
  const { listPlacesForPresentationSync } = await import("../lib/database.js");
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  const originalFetch = globalThis.fetch;
  let requestUrl;
  globalThis.fetch = async (url) => {
    requestUrl = new URL(String(url));
    return Response.json([]);
  };

  try {
    const now = new Date("2026-09-05T03:04:05.000Z");
    assert.deepEqual(await listPlacesForPresentationSync({ limit: 999, now }), []);
    const query = requestUrl.searchParams;
    assert.match(query.get("select"), /common_synced_at/);
    assert.match(query.get("select"), /media_synced_at/);
    assert.match(query.get("select"), /info_synced_at/);
    assert.equal(query.get("is_active"), "eq.true");
    assert.equal(query.get("intro_synced_at"), "not.is.null");
    assert.equal(
      query.get("and"),
      "(or(common_synced_at.is.null,media_synced_at.is.null,info_synced_at.is.null),or(next_enrichment_at.is.null,next_enrichment_at.lte.2026-09-05T03:04:05.000Z))"
    );
    assert.equal(query.get("order"), "next_enrichment_at.asc.nullsfirst,content_id.asc");
    assert.equal(query.get("limit"), "10");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("반복 상세 저장은 정규화 표시값과 원문을 함께 보존한다", async () => {
  const { savePlaceInfo } = await import("../lib/database.js");
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  const originalFetch = globalThis.fetch;
  let body;
  globalThis.fetch = async (_url, init) => {
    body = JSON.parse(init.body);
    return new Response(null, { status: 204 });
  };

  try {
    await savePlaceInfo({
      content_id: "123",
      enrichment_raw: { common: { overview: "소개" }, images: [{ keep: true }] }
    }, {
      infoItems: [{ title: "이용 안내", description: "예약 불필요" }],
      rawItems: [{ infoname: "이용 안내", infotext: "예약 불필요" }],
      syncedAt: "2026-09-07T00:00:00.000Z"
    });

    assert.deepEqual(body.enrichment_raw, {
      common: { overview: "소개" },
      images: [{ keep: true }],
      info: [{ infoname: "이용 안내", infotext: "예약 불필요" }],
      infoItems: [{ title: "이용 안내", description: "예약 불필요" }]
    });
    assert.equal(body.info_synced_at, "2026-09-07T00:00:00.000Z");
    assert.equal(body.enrichment_attempts, 0);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("common 저장은 자기 필드와 raw만 갱신하고 빈 응답도 완료 시각을 남긴다", async () => {
  const { savePlaceCommon } = await import("../lib/database.js");
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  const originalFetch = globalThis.fetch;
  const bodies = [];
  globalThis.fetch = async (_url, init) => {
    bodies.push(JSON.parse(init.body));
    return new Response(null, { status: 204 });
  };

  try {
    const place = {
      content_id: "123",
      enrichment_raw: { intro: { usetime: "09:00~18:00" }, images: [{ keep: true }] }
    };
    await savePlaceCommon(place, {
      overview: "장소 소개",
      homepageUrl: "https://example.com",
      common: { overview: "<p>장소 소개</p>" },
      syncedAt: "2026-09-05T00:00:00.000Z"
    });
    await savePlaceCommon(place, {
      overview: null,
      homepageUrl: null,
      common: null,
      syncedAt: "2026-09-06T00:00:00.000Z"
    });

    assert.deepEqual(bodies[0], {
      overview: "장소 소개",
      homepage_url: "https://example.com",
      enrichment_raw: {
        intro: { usetime: "09:00~18:00" },
        images: [{ keep: true }],
        common: { overview: "<p>장소 소개</p>" }
      },
      common_synced_at: "2026-09-05T00:00:00.000Z",
      enrichment_attempts: 0,
      enrichment_last_error: null,
      next_enrichment_at: null
    });
    assert.equal("overview" in bodies[1], false);
    assert.equal("homepage_url" in bodies[1], false);
    assert.equal(bodies[1].common_synced_at, "2026-09-06T00:00:00.000Z");
    assert.equal(bodies[1].enrichment_raw.common, null);
    assert.deepEqual(bodies[1].enrichment_raw.images, [{ keep: true }]);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("media 저장은 자기 태그를 교체하고 빈 이미지로 기존 이미지를 지우지 않는다", async () => {
  const { savePlaceMedia } = await import("../lib/database.js");
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  const originalFetch = globalThis.fetch;
  const bodies = [];
  globalThis.fetch = async (_url, init) => {
    bodies.push(JSON.parse(init.body));
    return new Response(null, { status: 204 });
  };

  try {
    const place = {
      content_id: "123",
      image_url: "https://example.com/original.jpg",
      tags: ["주차 가능", "반려동물 동반", "실내 활동", "검수 태그"],
      enrichment_raw: { intro: { parking: "가능" }, common: { overview: "소개" } }
    };
    await savePlaceMedia(place, {
      tags: [],
      imageUrls: [],
      images: [],
      pet: null,
      syncedAt: "2026-09-05T00:00:00.000Z"
    });
    await savePlaceMedia({ ...place, image_url: null }, {
      tags: ["주차 가능", "반려동물 동반"],
      imageUrls: ["https://example.com/detail.jpg"],
      images: [{ originimgurl: "https://example.com/detail.jpg" }],
      pet: { acmpyTypeCd: "동반가능" },
      syncedAt: "2026-09-06T00:00:00.000Z"
    });

    assert.deepEqual(bodies[0].tags, ["주차 가능", "검수 태그"]);
    assert.equal("image_url" in bodies[0], false);
    assert.equal("image_urls" in bodies[0], false);
    assert.deepEqual(bodies[0].enrichment_raw, {
      intro: { parking: "가능" },
      common: { overview: "소개" },
      images: [],
      imageAttributions: [],
      pet: null
    });
    assert.equal(bodies[0].media_synced_at, "2026-09-05T00:00:00.000Z");

    assert.equal(bodies[1].image_url, "https://example.com/detail.jpg");
    assert.deepEqual(bodies[1].image_urls, ["https://example.com/detail.jpg"]);
    assert.deepEqual(bodies[1].tags, ["주차 가능", "검수 태그", "반려동물 동반"]);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("presentation Cron은 GET·Bearer 인증과 lease를 적용한다", async () => {
  const { createTourPresentationSyncHandler } = await import(
    "../api/cron/tour-intro-sync.js"
  );
  process.env.CRON_SECRET = "cron-secret";
  let listed = 0;
  let claimed = 0;
  const handler = createTourPresentationSyncHandler({
    withLease: async (options) => {
      claimed += 1;
      return passthroughLease(options);
    },
    deadlineFactory: activeDeadline,
    listPlaces: async () => {
      listed += 1;
      return [];
    }
  });

  const methodResponse = await handler.fetch(
    new Request("https://example.test/api/cron/tour-intro-sync?stage=presentation", {
      method: "POST"
    })
  );
  assert.equal(methodResponse.status, 405);
  const authResponse = await handler.fetch(
    new Request("https://example.test/api/cron/tour-intro-sync?stage=presentation")
  );
  assert.equal(authResponse.status, 401);
  assert.equal(listed, 0);
  assert.equal(claimed, 0);

  const skippedHandler = createTourPresentationSyncHandler({
    withLease: async ({ jobId }) => {
      assert.equal(jobId, "tour_presentation");
      return { status: "skipped", reason: "already_running" };
    },
    deadlineFactory: activeDeadline,
    listPlaces: async () => assert.fail("lease가 있으면 조회하지 않습니다.")
  });
  const skipped = await skippedHandler.fetch(
    new Request("https://example.test/api/cron/tour-intro-sync?stage=presentation", {
      headers: { authorization: "Bearer cron-secret" }
    })
  );
  assert.deepEqual(await skipped.json(), { status: "skipped", reason: "already_running" });
});

test("presentation Cron은 배치 10·동시성 4로 제한하고 실패나 deferred를 partial로 반환한다", async () => {
  const { createTourPresentationSyncHandler } = await import(
    "../api/cron/tour-intro-sync.js"
  );
  process.env.CRON_SECRET = "cron-secret";
  process.env.TOUR_PRESENTATION_SYNC_BATCH_SIZE = "999";
  process.env.TOUR_SYNC_CONCURRENCY = "999";
  let listOptions;
  let runOptions;
  const places = [{ content_id: "1", content_type_id: 12 }];
  const handler = createTourPresentationSyncHandler({
    withLease: passthroughLease,
    deadlineFactory: activeDeadline,
    listPlaces: async (options) => {
      listOptions = options;
      return places;
    },
    runBatch: async (options) => {
      runOptions = options;
      return {
        processed: 1,
        deferred: 1,
        completed: 0,
        partial: 1,
        failed: 0,
        commonUpdated: 1,
        commonEmpty: 0,
        commonFailed: 0,
        mediaUpdated: 0,
        mediaEmpty: 0,
        mediaFailed: 1
      };
    }
  });

  const response = await handler.fetch(
    new Request("https://example.test/api/cron/tour-intro-sync?stage=presentation", {
      headers: { authorization: "Bearer cron-secret" }
    })
  );
  const body = await response.json();
  assert.equal(body.status, "partial");
  assert.equal(listOptions.limit, 10);
  assert.ok(listOptions.now instanceof Date);
  assert.equal(listOptions.signal, runOptions.signal);
  assert.equal(runOptions.concurrency, 4);
  assert.equal(runOptions.places, places);
  assert.equal(typeof runOptions.syncedAt, "string");
  assert.equal(typeof runOptions.canStart, "function");
});

test("공유 Cron 경로는 stage query로 intro와 presentation을 분리한다", async () => {
  const { createTourEnrichmentSyncHandler } = await import("../api/cron/tour-intro-sync.js");
  const calls = [];
  const handler = createTourEnrichmentSyncHandler({
    introHandler: { fetch: async () => { calls.push("intro"); return new Response(); } },
    presentationHandler: {
      fetch: async () => { calls.push("presentation"); return new Response(); }
    }
  });

  await handler.fetch(new Request("https://example.test/api/cron/tour-intro-sync"));
  await handler.fetch(
    new Request("https://example.test/api/cron/tour-intro-sync?stage=presentation")
  );

  assert.deepEqual(calls, ["intro", "presentation"]);
});
