package com.example.demo.plan.dto;

import java.util.List;

public record ScheduleDto(List<DayDto> days) {

  public record DayDto(int day, List<PlaceDto> places) {}

  public record PlaceDto(
      String id,
      String name,
      String category,
      String reason,
      String recommendedTime,
      Double lat,
      Double lng,
      // 바로 이전 장소에서 이 장소까지 실제 이동 시간(분, 카카오모빌리티 조회,
      // 이슈 #44). currentSchedule에 이 필드가 실려서 돌아와야, 완전히 안 바뀐
      // 날짜에서 카카오 API를 다시 호출하지 않고 이전 값을 재사용할 수 있다
      // (PlanChatController.oldTravelMinutesForDay 참고).
      Integer travelMinutesFromPrevious) {}
}
