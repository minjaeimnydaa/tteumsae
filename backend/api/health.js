import { json, methodNotAllowed } from "../lib/http.js";

export default {
  async fetch(request) {
    if (request.method !== "GET") return methodNotAllowed(["GET"]);

    return json({
      status: "ok",
      service: "tteumsae-backend",
      version: "0.2.0",
      timestamp: new Date().toISOString(),
      integrations: {
        tourApiConfigured: Boolean(process.env.TOUR_API_SERVICE_KEY),
        databaseConfigured: Boolean(
          process.env.SUPABASE_URL && process.env.SUPABASE_SERVICE_ROLE_KEY
        ),
        kakaoRoutingConfigured: Boolean(process.env.KAKAO_REST_API_KEY)
      }
    });
  }
};
