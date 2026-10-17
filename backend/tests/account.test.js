import test from "node:test";
import assert from "node:assert/strict";
import { verifySupabaseUser } from "../lib/supabase-auth.js";

async function loadHandler() {
  const module = await import("../api/account.js").catch(() => null);
  assert.ok(module?.default, "account endpoint must exist");
  return module.default;
}

function configureSupabase() {
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_PUBLISHABLE_KEY = "publishable-key";
  process.env.SUPABASE_SERVICE_ROLE_KEY = "service-role-key";
}

test("회원탈퇴는 DELETE만 허용한다", async () => {
  const handler = await loadHandler();
  const response = await handler.fetch(new Request("https://example.test/api/account"));

  assert.equal(response.status, 405);
  assert.equal(response.headers.get("allow"), "DELETE");
});

test("Bearer 토큰이 없으면 외부 호출 없이 401을 반환한다", async () => {
  const handler = await loadHandler();
  const originalFetch = globalThis.fetch;
  let called = false;
  globalThis.fetch = async () => {
    called = true;
    throw new Error("fetch must not be called");
  };
  try {
    const response = await handler.fetch(new Request("https://example.test/api/account", {
      method: "DELETE"
    }));
    assert.equal(response.status, 401);
    assert.equal(called, false);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("무효한 사용자 토큰은 관리자 삭제 전에 401을 반환한다", async () => {
  const handler = await loadHandler();
  configureSupabase();
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url) => {
    calls.push(String(url));
    return Response.json({ message: "invalid token" }, { status: 401 });
  };
  try {
    const response = await handler.fetch(new Request("https://example.test/api/account", {
      method: "DELETE",
      headers: { authorization: "Bearer invalid-token" }
    }));
    assert.equal(response.status, 401);
    assert.equal(calls.length, 1);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("검증된 토큰 사용자만 삭제하고 204를 반환한다", async () => {
  const handler = await loadHandler();
  configureSupabase();
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, init) => {
    calls.push({ url: String(url), init });
    if (String(url).endsWith("/auth/v1/user")) {
      return Response.json({ id: "verified-user" });
    }
    return new Response(null, { status: 204 });
  };
  try {
    const response = await handler.fetch(new Request("https://example.test/api/account", {
      method: "DELETE",
      headers: { authorization: "Bearer valid-token" }
    }));
    assert.equal(response.status, 204);
    assert.equal(await response.text(), "");
    assert.equal(calls.length, 2);
    assert.equal(calls[0].init.headers.apikey, "publishable-key");
    assert.equal(calls[0].init.headers.authorization, "Bearer valid-token");
    assert.match(calls[1].url, /\/auth\/v1\/admin\/users\/verified-user$/);
    assert.equal(calls[1].init.method, "DELETE");
    assert.equal(calls[1].init.headers.apikey, "service-role-key");
    assert.equal(calls[1].init.headers.authorization, "Bearer service-role-key");
    assert.ok(calls.every(({ init }) => init.signal instanceof AbortSignal));
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("새 Supabase secret 키는 관리자 Bearer 토큰으로 보내지 않는다", async () => {
  const handler = await loadHandler();
  configureSupabase();
  process.env.SUPABASE_SERVICE_ROLE_KEY = "sb_secret_server-only-key";
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, init) => {
    calls.push({ url: String(url), init });
    if (String(url).endsWith("/auth/v1/user")) {
      return Response.json({ id: "verified-user" });
    }
    return new Response(null, { status: 204 });
  };
  try {
    const response = await handler.fetch(new Request("https://example.test/api/account", {
      method: "DELETE",
      headers: { authorization: "Bearer valid-user-token" }
    }));

    assert.equal(response.status, 204);
    assert.equal(calls[1].init.headers.apikey, "sb_secret_server-only-key");
    assert.equal("authorization" in calls[1].init.headers, false);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("요청 본문의 위조 user_id는 삭제 대상에 사용하지 않는다", async () => {
  const handler = await loadHandler();
  configureSupabase();
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url) => {
    calls.push(String(url));
    if (String(url).endsWith("/auth/v1/user")) {
      return Response.json({ id: "token-owner" });
    }
    return new Response(null, { status: 204 });
  };
  try {
    const response = await handler.fetch(new Request("https://example.test/api/account", {
      method: "DELETE",
      headers: {
        authorization: "Bearer valid-token",
        "content-type": "application/json"
      },
      body: JSON.stringify({ user_id: "attacker-selected-user" })
    }));
    assert.equal(response.status, 204);
    assert.match(calls[1], /token-owner$/);
    assert.doesNotMatch(calls[1], /attacker-selected-user/);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("관리자 사용자 삭제 실패는 세부정보 없이 500을 반환한다", async () => {
  const handler = await loadHandler();
  configureSupabase();
  const originalFetch = globalThis.fetch;
  const originalConsoleError = console.error;
  const logs = [];
  console.error = (...values) => { logs.push(values.join(" ")); };
  globalThis.fetch = async (url) => {
    if (String(url).endsWith("/auth/v1/user")) {
      return Response.json({ id: "verified-user" });
    }
    return Response.json({ message: "sensitive admin failure" }, { status: 503 });
  };
  try {
    const response = await handler.fetch(new Request("https://example.test/api/account", {
      method: "DELETE",
      headers: { authorization: "Bearer valid-token" }
    }));
    const body = await response.json();
    assert.equal(response.status, 500);
    assert.equal(body.error.code, "INTERNAL_ERROR");
    assert.doesNotMatch(JSON.stringify(body), /sensitive admin failure/);
    assert.doesNotMatch(logs.join("\n"), /sensitive admin failure/);
  } finally {
    globalThis.fetch = originalFetch;
    console.error = originalConsoleError;
  }
});

test("같은 IP의 회원탈퇴 요청은 분당 3회로 제한한다", async () => {
  const handler = await loadHandler();
  const headers = { "x-forwarded-for": "203.0.113.77" };

  for (let count = 0; count < 3; count += 1) {
    const response = await handler.fetch(new Request("https://example.test/api/account", {
      method: "DELETE",
      headers
    }));
    assert.equal(response.status, 401);
  }
  const limited = await handler.fetch(new Request("https://example.test/api/account", {
    method: "DELETE",
    headers
  }));
  assert.equal(limited.status, 429);
});

test("Supabase 사용자 검증은 timeout signal을 전달한다", async () => {
  const originalUrl = process.env.SUPABASE_URL;
  const originalKey = process.env.SUPABASE_PUBLISHABLE_KEY;
  process.env.SUPABASE_URL = "https://supabase.test";
  process.env.SUPABASE_PUBLISHABLE_KEY = "publishable-key";
  try {
    const user = await verifySupabaseUser("user-token", async (_url, options) => {
      assert.ok(options.signal instanceof AbortSignal);
      return Response.json({ id: "user-1" });
    });
    assert.deepEqual(user, { id: "user-1" });
  } finally {
    if (originalUrl === undefined) delete process.env.SUPABASE_URL;
    else process.env.SUPABASE_URL = originalUrl;
    if (originalKey === undefined) delete process.env.SUPABASE_PUBLISHABLE_KEY;
    else process.env.SUPABASE_PUBLISHABLE_KEY = originalKey;
  }
});
