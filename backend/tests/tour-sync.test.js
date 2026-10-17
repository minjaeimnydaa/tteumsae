import test from "node:test";
import assert from "node:assert/strict";

async function waitFor(predicate, message) {
  for (let attempt = 0; attempt < 100; attempt += 1) {
    if (predicate()) return;
    await new Promise((resolve) => setImmediate(resolve));
  }
  assert.fail(message);
}

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

test("intro 배치는 성공·빈 결과·실패를 분리하고 각 결과를 기록한다", async () => {
  const { runIntroBatch } = await import("../lib/tour-sync.js");
  const places = [
    { content_id: "success", content_type_id: 12 },
    { content_id: "empty", content_type_id: 14 },
    { content_id: "failure", content_type_id: 39 }
  ];
  const saved = [];
  const failures = [];
  const syncedAt = "2026-08-27T00:00:00.000Z";

  const result = await runIntroBatch({
    places,
    concurrency: 2,
    syncedAt,
    fetchIntro: async (contentId) => {
      if (contentId === "success") return { usetime: "09:00~18:00" };
      if (contentId === "empty") return null;
      throw new Error("TourAPI timeout");
    },
    saveIntro: async (place, enrichment) => saved.push({ place, enrichment }),
    recordFailure: async (place, error, now) => failures.push({ place, error, now })
  });

  assert.deepEqual(result, { processed: 3, deferred: 0, updated: 1, empty: 1, failed: 1 });
  assert.deepEqual(saved.map(({ place }) => place.content_id), ["success", "empty"]);
  assert.equal(saved[0].enrichment.openingHours, "09:00~18:00");
  assert.equal(saved[0].enrichment.syncedAt, syncedAt);
  assert.equal(saved[1].enrichment.intro, null);
  assert.equal(failures.length, 1);
  assert.equal(failures[0].place.content_id, "failure");
  assert.match(failures[0].error.message, /timeout/);
  assert.equal(failures[0].now.toISOString(), syncedAt);
});

test("intro 배치는 설정한 동시 실행 수를 넘지 않는다", async () => {
  const { runIntroBatch } = await import("../lib/tour-sync.js");
  const places = ["1", "2", "3", "4"].map((content_id) => ({
    content_id,
    content_type_id: 12
  }));
  const blockers = [];
  let active = 0;
  let maxActive = 0;

  const running = runIntroBatch({
    places,
    concurrency: 2,
    syncedAt: "2026-08-27T00:00:00.000Z",
    fetchIntro: async () => {
      active += 1;
      maxActive = Math.max(maxActive, active);
      await new Promise((resolve) => blockers.push(resolve));
      active -= 1;
      return null;
    },
    saveIntro: async () => {},
    recordFailure: async () => {}
  });

  await waitFor(() => blockers.length === 2, "첫 두 작업이 시작되지 않았습니다.");
  assert.equal(maxActive, 2);
  blockers.splice(0).forEach((release) => release());
  await waitFor(() => blockers.length === 2, "다음 두 작업이 시작되지 않았습니다.");
  assert.equal(maxActive, 2);
  blockers.splice(0).forEach((release) => release());

  assert.deepEqual(await running, {
    processed: 4,
    deferred: 0,
    updated: 0,
    empty: 4,
    failed: 0
  });
});

test("intro 배치는 deadline 뒤 새 장소를 시작하지 않고 signal을 전달한다", async () => {
  const { runIntroBatch } = await import("../lib/tour-sync.js");
  const signal = new AbortController().signal;
  const started = [];
  let admissions = 0;

  const result = await runIntroBatch({
    places: ["1", "2", "3"].map((content_id) => ({ content_id, content_type_id: 12 })),
    concurrency: 1,
    syncedAt: "2026-08-27T00:00:00.000Z",
    signal,
    canStart: () => admissions++ < 1,
    fetchIntro: async (contentId, _contentTypeId, options) => {
      assert.equal(options.signal, signal);
      started.push(contentId);
      return null;
    },
    saveIntro: async (_place, _enrichment, options) => {
      assert.equal(options.signal, signal);
    },
    recordFailure: async () => {}
  });

  assert.deepEqual(started, ["1"]);
  assert.deepEqual(result, { processed: 1, deferred: 2, updated: 0, empty: 1, failed: 0 });
});

test("intro 대상은 활성·미완료·재시도 시각 도래 순으로 최대 40개만 조회한다", async () => {
  const { listPlacesForIntroSync } = await import("../lib/database.js");
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  const originalFetch = globalThis.fetch;
  let requestUrl;
  globalThis.fetch = async (url) => {
    requestUrl = new URL(String(url));
    return Response.json([]);
  };

  try {
    const now = new Date("2026-08-27T03:04:05.000Z");
    assert.deepEqual(await listPlacesForIntroSync({ limit: 999, now }), []);
    const query = requestUrl.searchParams;
    assert.match(query.get("select"), /enrichment_raw/);
    assert.match(query.get("select"), /enrichment_attempts/);
    assert.equal(query.get("is_active"), "eq.true");
    assert.equal(query.get("intro_synced_at"), "is.null");
    assert.equal(
      query.get("or"),
      "(next_enrichment_at.is.null,next_enrichment_at.lte.2026-08-27T03:04:05.000Z)"
    );
    assert.equal(query.get("order"), "next_enrichment_at.asc.nullsfirst,content_id.asc");
    assert.equal(query.get("limit"), "40");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("intro 저장은 담당 태그만 교체하고 다른 단계의 태그와 raw를 보존한다", async () => {
  const { savePlaceIntro } = await import("../lib/database.js");
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  const originalFetch = globalThis.fetch;
  let request;
  globalThis.fetch = async (url, init) => {
    request = { url: String(url), init };
    return new Response(null, { status: 204 });
  };

  try {
    await savePlaceIntro(
      {
        content_id: "123",
        tags: ["주차 가능", "반려동물 동반", "실내 활동"],
        enrichment_raw: { legacy: { keep: true }, common: { overview: "소개" } }
      },
      {
        tags: ["아이 동반"],
        openingHours: "09:00~18:00",
        closedDays: "매주 월요일",
        eventStartDate: "2026-09-01",
        eventEndDate: "2026-09-03",
        intro: { usetime: "09:00~18:00" },
        syncedAt: "2026-08-27T00:00:00.000Z"
      }
    );

    assert.match(request.url, /places\?content_id=eq\.123$/);
    const body = JSON.parse(request.init.body);
    assert.deepEqual(body, {
      opening_hours: "09:00~18:00",
      closed_days: "매주 월요일",
      event_start_date: "2026-09-01",
      event_end_date: "2026-09-03",
      tags: ["반려동물 동반", "실내 활동", "아이 동반"],
      enrichment_raw: {
        legacy: { keep: true },
        common: { overview: "소개" },
        intro: { usetime: "09:00~18:00" }
      },
      intro_synced_at: "2026-08-27T00:00:00.000Z",
      enrichment_attempts: 0,
      enrichment_last_error: null,
      next_enrichment_at: null
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("빈 intro도 완료 처리하되 기존 정상 상세값은 지우지 않는다", async () => {
  const { savePlaceIntro } = await import("../lib/database.js");
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  const originalFetch = globalThis.fetch;
  let body;
  globalThis.fetch = async (_url, init) => {
    body = JSON.parse(init.body);
    return new Response(null, { status: 204 });
  };

  try {
    await savePlaceIntro(
      {
        content_id: "empty",
        tags: ["주차 가능", "반려동물 동반"],
        enrichment_raw: { common: { overview: "기존 소개" } }
      },
      {
        tags: [],
        openingHours: null,
        closedDays: null,
        eventStartDate: null,
        eventEndDate: null,
        intro: null,
        syncedAt: "2026-08-27T00:00:00.000Z"
      }
    );

    for (const field of [
      "opening_hours",
      "closed_days",
      "event_start_date",
      "event_end_date",
      "tags"
    ]) {
      assert.equal(field in body, false, `${field}는 빈 결과로 덮어쓰면 안 됩니다.`);
    }
    assert.deepEqual(body.enrichment_raw, {
      common: { overview: "기존 소개" },
      intro: null
    });
    assert.equal(body.intro_synced_at, "2026-08-27T00:00:00.000Z");
    assert.equal(body.enrichment_attempts, 0);
    assert.equal(body.enrichment_last_error, null);
    assert.equal(body.next_enrichment_at, null);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("부분 intro는 응답에 없는 상세값을 보존하고 담당 태그는 최신 응답으로 교체한다", async () => {
  const { savePlaceIntro } = await import("../lib/database.js");
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  const originalFetch = globalThis.fetch;
  let body;
  globalThis.fetch = async (_url, init) => {
    body = JSON.parse(init.body);
    return new Response(null, { status: 204 });
  };

  try {
    await savePlaceIntro(
      {
        content_id: "partial",
        tags: ["주차 가능", "반려동물 동반"],
        enrichment_raw: {}
      },
      {
        tags: [],
        openingHours: null,
        closedDays: "매주 화요일",
        eventStartDate: null,
        eventEndDate: null,
        intro: { restdate: "매주 화요일" },
        syncedAt: "2026-08-27T00:00:00.000Z"
      }
    );

    assert.equal(body.closed_days, "매주 화요일");
    assert.deepEqual(body.tags, ["반려동물 동반"]);
    for (const field of ["opening_hours", "event_start_date", "event_end_date"]) {
      assert.equal(field in body, false, `${field}는 부분 응답으로 덮어쓰면 안 됩니다.`);
    }
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("intro 실패는 기존 상세값을 건드리지 않고 횟수·오류·지수 백오프만 기록한다", async () => {
  const { recordPlaceEnrichmentFailure } = await import("../lib/database.js");
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role";
  const originalFetch = globalThis.fetch;
  let body;
  globalThis.fetch = async (_url, init) => {
    body = JSON.parse(init.body);
    return new Response(null, { status: 204 });
  };

  try {
    await recordPlaceEnrichmentFailure(
      { content_id: "failed", enrichment_attempts: 2 },
      new Error("x".repeat(350)),
      new Date("2026-08-27T00:00:00.000Z")
    );
    assert.deepEqual(Object.keys(body).sort(), [
      "enrichment_attempts",
      "enrichment_last_error",
      "next_enrichment_at"
    ]);
    assert.equal(body.enrichment_attempts, 3);
    assert.equal(body.enrichment_last_error.length, 300);
    assert.equal(body.next_enrichment_at, "2026-08-27T01:00:00.000Z");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("intro Cron은 GET·Bearer 인증만 허용한다", async () => {
  const { createTourIntroSyncHandler } = await import("../api/cron/tour-intro-sync.js");
  process.env.CRON_SECRET = "cron-secret";
  let listed = 0;
  let claimed = 0;
  const handler = createTourIntroSyncHandler({
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
    new Request("https://example.test/api/cron/tour-intro-sync", { method: "POST" })
  );
  assert.equal(methodResponse.status, 405);
  const authResponse = await handler.fetch(
    new Request("https://example.test/api/cron/tour-intro-sync")
  );
  assert.equal(authResponse.status, 401);
  assert.equal(listed, 0);
  assert.equal(claimed, 0);
});

test("intro Cron은 이미 실행 중이면 작업 없이 skipped를 반환한다", async () => {
  const { createTourIntroSyncHandler } = await import("../api/cron/tour-intro-sync.js");
  process.env.CRON_SECRET = "cron-secret";
  let listed = false;
  const handler = createTourIntroSyncHandler({
    withLease: async ({ jobId }) => {
      assert.equal(jobId, "tour_intro");
      return { status: "skipped", reason: "already_running" };
    },
    deadlineFactory: activeDeadline,
    listPlaces: async () => {
      listed = true;
      return [];
    }
  });

  const response = await handler.fetch(
    new Request("https://example.test/api/cron/tour-intro-sync", {
      headers: { authorization: "Bearer cron-secret" }
    })
  );

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { status: "skipped", reason: "already_running" });
  assert.equal(listed, false);
});

test("intro Cron은 배치 40·동시성 4로 제한하고 빈 대상은 idle로 반환한다", async () => {
  const { createTourIntroSyncHandler } = await import("../api/cron/tour-intro-sync.js");
  process.env.CRON_SECRET = "cron-secret";
  process.env.TOUR_INTRO_SYNC_BATCH_SIZE = "999";
  process.env.TOUR_SYNC_CONCURRENCY = "999";
  let listOptions;
  let runOptions;
  const places = [
    { content_id: "1", content_type_id: 12 },
    { content_id: "2", content_type_id: 14 }
  ];
  const handler = createTourIntroSyncHandler({
    withLease: passthroughLease,
    deadlineFactory: activeDeadline,
    listPlaces: async (options) => {
      listOptions = options;
      return places;
    },
    runBatch: async (options) => {
      runOptions = options;
      return { processed: 2, deferred: 0, updated: 1, empty: 1, failed: 0 };
    },
    fetchIntro: async () => null,
    saveIntro: async () => {},
    recordFailure: async () => {}
  });

  const response = await handler.fetch(
    new Request("https://example.test/api/cron/tour-intro-sync", {
      headers: { authorization: "Bearer cron-secret" }
    })
  );
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    status: "completed",
    processed: 2,
    deferred: 0,
    updated: 1,
    empty: 1,
    failed: 0
  });
  assert.equal(listOptions.limit, 40);
  assert.ok(listOptions.now instanceof Date);
  assert.equal(listOptions.signal, runOptions.signal);
  assert.equal(runOptions.concurrency, 4);
  assert.equal(runOptions.places, places);
  assert.equal(typeof runOptions.syncedAt, "string");

  const idleHandler = createTourIntroSyncHandler({
    withLease: passthroughLease,
    deadlineFactory: activeDeadline,
    listPlaces: async () => []
  });
  const idleResponse = await idleHandler.fetch(
    new Request("https://example.test/api/cron/tour-intro-sync", {
      headers: { authorization: "Bearer cron-secret" }
    })
  );
  assert.deepEqual(await idleResponse.json(), {
    status: "idle",
    processed: 0,
    deferred: 0,
    updated: 0,
    empty: 0,
    failed: 0
  });
});
