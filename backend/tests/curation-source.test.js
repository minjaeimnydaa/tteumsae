import assert from "node:assert/strict";
import test from "node:test";

import { listCurationCandidatesFromApi } from "../lib/curation-source.js";

test("공개 장소 API를 끝 페이지까지 조회해 강릉 검수 후보를 모은다", async () => {
  const requests = [];
  const pages = [
    { data: [{ content_id: "1" }, { content_id: "2" }], pagination: { hasMore: true } },
    { data: [{ content_id: "3" }], pagination: { hasMore: false } }
  ];
  const fetchFn = async (url) => {
    requests.push(String(url));
    return Response.json(pages.shift());
  };

  const candidates = await listCurationCandidatesFromApi(
    "https://api.example/",
    { fetchFn }
  );

  assert.deepEqual(candidates.map((place) => place.content_id), ["1", "2", "3"]);
  assert.deepEqual(requests, [
    "https://api.example/api/places?sigunguCode=1&page=1&pageSize=100",
    "https://api.example/api/places?sigunguCode=1&page=2&pageSize=100"
  ]);
});

test("공개 장소 API 오류나 잘못된 응답은 후보 파일로 위장하지 않는다", async () => {
  await assert.rejects(
    listCurationCandidatesFromApi("https://api.example", {
      fetchFn: async () => Response.json({ error: "down" }, { status: 503 })
    }),
    /503/
  );
  await assert.rejects(
    listCurationCandidatesFromApi("https://api.example", {
      fetchFn: async () => Response.json({ data: null, pagination: {} })
    }),
    /형식/
  );
});
