import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { supabaseApiHeaders } from "../lib/supabase-auth.js";

const requiredNames = [
  "SUPABASE_TEST_URL",
  "SUPABASE_TEST_PUBLISHABLE_KEY",
  "SUPABASE_TEST_SERVICE_ROLE_KEY"
];

const missingNames = requiredNames.filter((name) => !process.env[name]?.trim());
if (missingNames.length > 0) {
  console.log(`RLS verification SKIPPED: missing ${missingNames.join(", ")}`);
  process.exit(0);
}

const baseUrl = process.env.SUPABASE_TEST_URL.replace(/\/$/, "");
const publishableKey = process.env.SUPABASE_TEST_PUBLISHABLE_KEY;
const serviceRoleKey = process.env.SUPABASE_TEST_SERVICE_ROLE_KEY;

function headers(key, token = key, extra = {}) {
  return supabaseApiHeaders(key, token, extra);
}

async function readBody(response) {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

async function expectResponse(response, allowedStatuses, label) {
  const body = await readBody(response);
  assert.ok(
    allowedStatuses.includes(response.status),
    `${label}: expected ${allowedStatuses.join("/")}, received ${response.status}`
  );
  return body;
}

async function createUser(email, password) {
  const response = await fetch(`${baseUrl}/auth/v1/admin/users`, {
    method: "POST",
    headers: headers(serviceRoleKey, serviceRoleKey, { "content-type": "application/json" }),
    body: JSON.stringify({ email, password, email_confirm: true })
  });
  const body = await expectResponse(response, [200, 201], "create test user");
  assert.equal(typeof body?.id, "string", "create test user: missing id");
  return body.id;
}

async function signIn(email, password) {
  const response = await fetch(`${baseUrl}/auth/v1/token?grant_type=password`, {
    method: "POST",
    headers: headers(publishableKey, publishableKey, { "content-type": "application/json" }),
    body: JSON.stringify({ email, password })
  });
  const body = await expectResponse(response, [200], "sign in test user");
  assert.equal(typeof body?.access_token, "string", "sign in test user: missing access token");
  return body.access_token;
}

async function adminRest(path, options = {}) {
  return fetch(`${baseUrl}/rest/v1/${path}`, {
    ...options,
    headers: headers(serviceRoleKey, serviceRoleKey, options.headers)
  });
}

async function userRest(token, path, options = {}) {
  return fetch(`${baseUrl}/rest/v1/${path}`, {
    ...options,
    headers: headers(publishableKey, token, options.headers)
  });
}

async function insertJson(token, table, value) {
  const response = await userRest(token, table, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      prefer: "return=representation"
    },
    body: JSON.stringify(value)
  });
  return expectResponse(response, [200, 201], `insert ${table}`);
}

async function selectOwn(token, table, userId) {
  const response = await userRest(
    token,
    `${table}?user_id=eq.${encodeURIComponent(userId)}&select=*`
  );
  const rows = await expectResponse(response, [200], `select own ${table}`);
  assert.equal(rows.length, 1, `select own ${table}: expected one row`);
}

async function updateOwn(token, table, userId, value) {
  const response = await userRest(
    token,
    `${table}?user_id=eq.${encodeURIComponent(userId)}&select=user_id`,
    {
      method: "PATCH",
      headers: {
        "content-type": "application/json",
        prefer: "return=representation"
      },
      body: JSON.stringify(value)
    }
  );
  const rows = await expectResponse(response, [200], `update own ${table}`);
  assert.equal(rows.length, 1, `update own ${table}: expected one row`);
}

async function assertCrossUserDenied(token, table, otherUserId, value) {
  const encodedId = encodeURIComponent(otherUserId);
  const selectResponse = await userRest(token, `${table}?user_id=eq.${encodedId}&select=*`);
  const selectBody = await expectResponse(selectResponse, [200, 401, 403], `cross-user select ${table}`);
  if (selectResponse.status === 200) {
    assert.deepEqual(selectBody, [], `cross-user select ${table}: leaked a row`);
  }

  const updateResponse = await userRest(
    token,
    `${table}?user_id=eq.${encodedId}&select=user_id`,
    {
      method: "PATCH",
      headers: {
        "content-type": "application/json",
        prefer: "return=representation"
      },
      body: JSON.stringify(value)
    }
  );
  const updateBody = await expectResponse(updateResponse, [200, 401, 403], `cross-user update ${table}`);
  if (updateResponse.status === 200) {
    assert.deepEqual(updateBody, [], `cross-user update ${table}: changed a row`);
  }
}

async function deleteUser(userId) {
  const response = await fetch(`${baseUrl}/auth/v1/admin/users/${encodeURIComponent(userId)}`, {
    method: "DELETE",
    headers: headers(serviceRoleKey)
  });
  await expectResponse(response, [200, 204, 404], "delete test user");
}

const runId = randomUUID();
const password = `Tt!${randomUUID()}9a`;
const users = [
  { email: `tteumsae-rls-a-${runId}@example.com`, id: null, token: null },
  { email: `tteumsae-rls-b-${runId}@example.com`, id: null, token: null }
];
const placeId = `rls-test-${runId}`;

try {
  const placeResponse = await adminRest("places", {
    method: "POST",
    headers: { "content-type": "application/json", prefer: "return=minimal" },
    body: JSON.stringify({
      content_id: placeId,
      source: "RLS_TEST",
      name: "RLS verification place",
      category: "ATTRACTION",
      content_type_id: 12,
      area_code: 32,
      latitude: 37.75,
      longitude: 128.87,
      default_stay_minutes: 15,
      raw: { rlsTest: true }
    })
  });
  await expectResponse(placeResponse, [200, 201], "create test place");

  for (const user of users) {
    user.id = await createUser(user.email, password);
    user.token = await signIn(user.email, password);
    await insertJson(user.token, "profiles", {
      user_id: user.id,
      display_name: "RLS tester",
      age_group: "PREFER_NOT_TO_SAY",
      gender: "PREFER_NOT_TO_SAY"
    });
    await insertJson(user.token, "user_saved_places", {
      user_id: user.id,
      place_id: placeId,
      is_saved: true,
      saved_at: new Date().toISOString()
    });
    await selectOwn(user.token, "profiles", user.id);
    await selectOwn(user.token, "user_saved_places", user.id);
    await updateOwn(user.token, "profiles", user.id, { display_name: "Updated tester" });
    await updateOwn(user.token, "user_saved_places", user.id, { is_saved: false, saved_at: null });
  }

  await assertCrossUserDenied(users[0].token, "profiles", users[1].id, {
    display_name: "Forbidden update"
  });
  await assertCrossUserDenied(users[0].token, "user_saved_places", users[1].id, {
    is_saved: true
  });

  console.log("RLS verification PASS: own CRUD allowed and cross-user access denied");
} finally {
  for (const user of users) {
    if (user.id) {
      try {
        await deleteUser(user.id);
      } catch (error) {
        console.error(`RLS cleanup failed for temporary user: ${error.message}`);
      }
    }
  }
  try {
    const response = await adminRest(`places?content_id=eq.${encodeURIComponent(placeId)}`, {
      method: "DELETE"
    });
    await expectResponse(response, [200, 204], "delete test place");
  } catch (error) {
    console.error(`RLS cleanup failed for temporary place: ${error.message}`);
  }
}
