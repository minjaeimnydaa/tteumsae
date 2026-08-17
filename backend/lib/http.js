const baseHeaders = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store"
};

export function json(data, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...baseHeaders, ...extraHeaders }
  });
}

export function methodNotAllowed(allowed) {
  return json(
    { error: { code: "METHOD_NOT_ALLOWED", message: "지원하지 않는 요청 방식입니다." } },
    405,
    { allow: allowed.join(", ") }
  );
}

export function badRequest(message, details) {
  return json({ error: { code: "BAD_REQUEST", message, details } }, 400);
}

export function notFound(message = "요청한 데이터를 찾을 수 없습니다.") {
  return json({ error: { code: "NOT_FOUND", message } }, 404);
}

export function unauthorized() {
  return json(
    { error: { code: "UNAUTHORIZED", message: "인증되지 않은 요청입니다." } },
    401
  );
}

export function serverError(error) {
  const requestId = crypto.randomUUID();
  const message = error instanceof Error ? error.message : String(error);
  console.error(`[${requestId}] ${message}`);
  return json(
    {
      error: {
        code: "INTERNAL_ERROR",
        message: "서버 처리 중 오류가 발생했습니다.",
        requestId
      }
    },
    500
  );
}

export async function readJson(request) {
  const contentType = request.headers.get("content-type") ?? "";
  if (!contentType.includes("application/json")) {
    throw new Error("Content-Type must be application/json");
  }
  return request.json();
}

