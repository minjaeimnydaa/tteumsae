import { getPlace } from "../../lib/database.js";
import { json, methodNotAllowed, notFound, serverError } from "../../lib/http.js";

export default {
  async fetch(request) {
    if (request.method !== "GET") return methodNotAllowed(["GET"]);

    try {
      const id = decodeURIComponent(new URL(request.url).pathname.split("/").pop() ?? "");
      if (!id) return notFound();
      const place = await getPlace(id);
      return place ? json({ data: place }) : notFound("장소를 찾을 수 없습니다.");
    } catch (error) {
      return serverError(error);
    }
  }
};

