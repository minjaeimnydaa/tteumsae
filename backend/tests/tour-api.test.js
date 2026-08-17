import test from "node:test";
import assert from "node:assert/strict";
import {
  mapTourItem,
  normalizeTourEnrichment
} from "../lib/tour-api.js";

test("TourAPI 관광지를 DB 행으로 변환한다", () => {
  const result = mapTourItem(
    {
      contentid: "123",
      contenttypeid: "12",
      title: "강릉 테스트 장소",
      areacode: "32",
      sigungucode: "1",
      mapx: "128.8761",
      mapy: "37.7519",
      addr1: "강원특별자치도 강릉시",
      firstimage: "https://example.com/image.jpg",
      modifiedtime: "20260723"
    },
    "2026-07-23T00:00:00.000Z"
  );

  assert.equal(result.content_id, "123");
  assert.equal(result.category, "ATTRACTION");
  assert.equal(result.default_stay_minutes, 60);
  assert.equal(result.longitude, 128.8761);
});

test("지원하지 않는 숙박 타입은 제외한다", () => {
  const result = mapTourItem({
    contentid: "456",
    contenttypeid: "32",
    title: "숙박시설",
    mapx: "128.8",
    mapy: "37.7"
  });
  assert.equal(result, null);
});

test("상세정보를 카드 태그와 대표 이미지 후보로 정규화한다", () => {
  const result = normalizeTourEnrichment({
    contentTypeId: 14,
    intro: {
      parkingculture: "주차 가능",
      chkbabycarriageculture: "없음",
      usetimeculture: "09:00~18:00<br>입장 마감 17:30",
      restdateculture: "매주 월요일"
    },
    images: [
      {
        originimgurl: "https://example.com/original.jpg",
        smallimageurl: "https://example.com/thumb.jpg"
      }
    ],
    pet: { acmpyTypeCd: "동반 가능" }
  });

  assert.deepEqual(result.tags, ["주차 가능", "반려동물 동반", "실내 활동"]);
  assert.deepEqual(result.imageUrls, [
    "https://example.com/original.jpg",
    "https://example.com/thumb.jpg"
  ]);
  assert.equal(result.openingHours, "09:00~18:00 입장 마감 17:30");
  assert.equal(result.closedDays, "매주 월요일");
});
