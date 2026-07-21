package com.example.demo.plan;

// source: "jeju_place" 또는 "wishlist" — 두 테이블의 id 공간이 서로 달라서
// 나중에 랭킹 집계할 때 어느 출처의 id인지 구분할 수 있어야 한다.
public record PlaceIncludedInScheduleEvent(String source, Long placeId, String placeName) {
}
