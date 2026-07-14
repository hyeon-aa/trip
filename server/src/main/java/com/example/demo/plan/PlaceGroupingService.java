package com.example.demo.plan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.demo.jeju.JejuPlace;

@Service
public class PlaceGroupingService {

    public String buildPlacesPrompt(Map<String, JejuPlace> placeIdMap) {
        Map<String, List<Map.Entry<String, JejuPlace>>> regionMap = placeIdMap.entrySet().stream()
            .collect(Collectors.groupingBy(e -> e.getValue().getRegion()));

        StringBuilder placesBuilder = new StringBuilder();
        for (String region : List.of("동부", "서부", "남부", "제주시")) {
            List<Map.Entry<String, JejuPlace>> regionPlaces = regionMap.get(region);
            if (regionPlaces == null || regionPlaces.isEmpty()) {
                continue;
            }
            placesBuilder.append("\n[").append(region).append("]\n");

            // 권역 안에서 읍/면/동 단위(sub_region)로 한 번 더 묶어서 같은 날 동선이
            // 좁은 지역에 모이도록 힌트를 준다. 아직 매핑 안 된 곳은 "기타"로.
            Map<String, List<Map.Entry<String, JejuPlace>>> subRegionMap = regionPlaces.stream()
                .collect(Collectors.groupingBy(
                    e -> {
                        String sr = e.getValue().getSubRegion();
                        return (sr == null || sr.isBlank()) ? "기타" : sr;
                    },
                    LinkedHashMap::new,
                    Collectors.toList()
                ));

            for (Map.Entry<String, List<Map.Entry<String, JejuPlace>>> subEntry : subRegionMap.entrySet()) {
                placesBuilder.append("  - ").append(subEntry.getKey()).append("\n");
                for (Map.Entry<String, JejuPlace> entry : subEntry.getValue()) {
                    JejuPlace p = entry.getValue();
                    placesBuilder
                        .append("    [")
                        .append(entry.getKey())
                        .append("] ")
                        .append(p.getName())
                        .append(" (")
                        .append(p.getCategory())
                        .append(")\n");
                }
            }
        }
        return placesBuilder.toString();
    }
}
