import { requiredEnv } from "./env.js";
import { fetchWithTimeout, NETWORK_TIMEOUT_MS } from "./fetch-policy.js";

export function readBearerToken(request) {
  const authorization = request.headers.get("authorization")?.trim() ?? "";
  const match = /^Bearer\s+([^\s]+)$/i.exec(authorization);
  return match?.[1] ?? null;
}

export function supabaseApiHeaders(apiKey, authorizationToken = apiKey, extra = {}) {
  const headers = { apikey: apiKey, ...extra };
  const isOpaqueApiKey = authorizationToken === apiKey
    && /^(sb_publishable|sb_secret)_/.test(apiKey);
  if (authorizationToken && !isOpaqueApiKey) {
    headers.authorization = `Bearer ${authorizationToken}`;
  }
  return headers;
}

export async function verifySupabaseUser(token, fetchImpl = fetch, signal) {
  const baseUrl = requiredEnv("SUPABASE_URL").replace(/\/$/, "");
  const publishableKey = requiredEnv("SUPABASE_PUBLISHABLE_KEY");
  const result = await fetchWithTimeout(`${baseUrl}/auth/v1/user`, {
    method: "GET",
    headers: {
      apikey: publishableKey,
      authorization: `Bearer ${token}`
    }
  }, {
    provider: "SUPABASE",
    timeoutMs: NETWORK_TIMEOUT_MS.SUPABASE,
    signal,
    fetchImpl,
    consume: async (response) => ({
      ok: response.ok,
      status: response.status,
      payload: response.ok ? await response.json() : null
    })
  });

  if ([401, 403].includes(result.status)) return null;
  if (!result.ok) {
    throw new Error(`Supabase user verification failed (${result.status})`);
  }

  const user = result.payload;
  return typeof user?.id === "string" && user.id ? { id: user.id } : null;
}

export async function deleteSupabaseUser(userId, fetchImpl = fetch, signal) {
  const baseUrl = requiredEnv("SUPABASE_URL").replace(/\/$/, "");
  const serviceRoleKey = requiredEnv("SUPABASE_SERVICE_ROLE_KEY");
  const result = await fetchWithTimeout(
    `${baseUrl}/auth/v1/admin/users/${encodeURIComponent(userId)}`,
    {
      method: "DELETE",
      headers: supabaseApiHeaders(serviceRoleKey)
    },
    {
      provider: "SUPABASE",
      timeoutMs: NETWORK_TIMEOUT_MS.SUPABASE,
      signal,
      fetchImpl,
      consume: async (response) => ({
        ok: response.ok,
        status: response.status
      })
    }
  );

  if (result.ok || result.status === 404) return;
  throw new Error(`Supabase admin user deletion failed (${result.status})`);
}
