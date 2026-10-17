import test from "node:test";
import assert from "node:assert/strict";
import placesApi from "../api/places/index.js";

test("장소 API가 강원도 시군구 코드 1~18만 허용한다", async () => {
  const response = await placesApi.fetch(
    new Request("https://example.test/api/places?sigunguCode=19")
  );

  assert.equal(response.status, 400);
  assert.match((await response.json()).error.message, /강원도 지역/);
});
