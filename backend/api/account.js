import {
  emptyResponse,
  methodNotAllowed,
  rateLimit,
  serverError,
  unauthorized
} from "../lib/http.js";
import {
  deleteSupabaseUser,
  readBearerToken,
  verifySupabaseUser
} from "../lib/supabase-auth.js";

export default {
  async fetch(request) {
    if (request.method !== "DELETE") return methodNotAllowed(["DELETE"]);

    const limited = rateLimit(request, "account-delete", 3);
    if (limited) return limited;

    const token = readBearerToken(request);
    if (!token) return unauthorized();

    try {
      const user = await verifySupabaseUser(token);
      if (!user) return unauthorized();
      await deleteSupabaseUser(user.id);
      return emptyResponse(204);
    } catch (error) {
      return serverError(error);
    }
  }
};
