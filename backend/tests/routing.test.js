import test from "node:test";
import assert from "node:assert/strict";
import { createPathBounds, distanceToPathKm } from "../lib/routing.js";

const path = [
  { latitude: 37.75, longitude: 128.87 },
  { latitude: 37.75, longitude: 128.9 }
];

test("경로 바운드와 경로선까지의 거리를 계산한다", () => {
  const bounds = createPathBounds(path, 1);
  assert.ok(bounds.minLatitude < 37.75);
  assert.ok(bounds.maxLongitude > 128.9);
  assert.ok(distanceToPathKm({ latitude: 37.751, longitude: 128.885 }, path) < 0.2);
  assert.ok(distanceToPathKm({ latitude: 37.8, longitude: 128.885 }, path) > 5);
});
