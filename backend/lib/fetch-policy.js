export const NETWORK_TIMEOUT_MS = Object.freeze({
  SUPABASE: 5_000,
  KAKAO_LOCAL: 5_000,
  KAKAO_MOBILITY: 8_000,
  TOUR_API: 8_000,
  RECOMMENDATION: 25_000,
  CRON: 50_000
});

export class UpstreamTimeoutError extends Error {
  constructor(provider) {
    super("외부 서비스 응답 시간이 초과되었습니다.");
    this.name = "UpstreamTimeoutError";
    this.code = "UPSTREAM_TIMEOUT";
    this.provider = provider;
  }
}

export async function fetchWithTimeout(
  input,
  init = {},
  { provider, timeoutMs, signal, fetchImpl = fetch, consume } = {}
) {
  const timeoutSignal = AbortSignal.timeout(timeoutMs);
  const combinedSignal = signal
    ? AbortSignal.any([signal, timeoutSignal])
    : timeoutSignal;

  try {
    const response = await fetchImpl(input, { ...init, signal: combinedSignal });
    return consume ? await consume(response, combinedSignal) : response;
  } catch (error) {
    if (timeoutSignal.aborted && !signal?.aborted) {
      throw new UpstreamTimeoutError(provider);
    }
    throw error;
  }
}

export function createDeadline(timeoutMs, { now = () => Date.now() } = {}) {
  const startedAt = now();
  const controller = new AbortController();
  const timer = setTimeout(
    () => controller.abort(new UpstreamTimeoutError("REQUEST_DEADLINE")),
    timeoutMs
  );

  return {
    signal: controller.signal,
    expiresAt: startedAt + timeoutMs,
    remainingMs: () => Math.max(0, startedAt + timeoutMs - now()),
    canStart: (minimumRemainingMs) =>
      startedAt + timeoutMs - now() >= minimumRemainingMs,
    dispose: () => clearTimeout(timer)
  };
}
