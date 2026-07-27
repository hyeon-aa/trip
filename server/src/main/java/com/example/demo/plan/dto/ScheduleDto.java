package com.example.demo.plan.dto;

import java.util.List;

public record ScheduleDto(List<DayDto> days) {

    public record DayDto(int day, List<PlaceDto> places) {
    }

    public record PlaceDto(
        String id,
        String name,
        String category,
        String reason,
        String recommendedTime,
        Double lat,
        Double lng
    ) {
    }
}
