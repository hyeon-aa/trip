package com.example.demo.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class RouteOptimizerTest {

    private final RouteOptimizer optimizer = new RouteOptimizer();
    private final ObjectMapper mapper = new ObjectMapper();

    private ObjectNode place(String name, double lat, double lng) {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", name);
        node.put("lat", lat);
        node.put("lng", lng);
        return node;
    }

    @Test
    void 장소가_하나면_그대로_반환한다() {
        ObjectNode a = place("A", 33.000, 126.000);

        List<ObjectNode> result = optimizer.optimalOrder(List.of(a), null, null, null, null);

        assertThat(result).containsExactly(a);
    }

    @Test
    void 앵커가_없으면_전체_이동거리가_최소인_순서로_정렬한다() {
        // 위도상 일직선(A-B-C)이므로 최적 경로는 A-B-C 또는 C-B-A 둘 중 하나여야 한다
        ObjectNode a = place("A", 33.000, 126.000);
        ObjectNode b = place("B", 33.005, 126.000);
        ObjectNode c = place("C", 33.010, 126.000);

        List<ObjectNode> result = optimizer.optimalOrder(List.of(c, a, b), null, null, null, null);

        List<String> names = result.stream().map(n -> n.get("name").asText()).toList();
        assertThat(names).containsAnyOf("A", "C"); // 양 끝점이 순서 양 끝에 온다
        assertThat(names.get(1)).isEqualTo("B"); // 가운데 점은 항상 가운데
    }

    @Test
    void 시작과_끝_앵커가_있으면_그_방향으로_순서가_고정된다() {
        ObjectNode a = place("A", 33.000, 126.000);
        ObjectNode b = place("B", 33.005, 126.000);
        ObjectNode c = place("C", 33.010, 126.000);
        double startLat = 32.990, startLng = 126.000; // A보다 남쪽 (A에 더 가까움)
        double endLat = 33.020, endLng = 126.000; // C보다 북쪽 (C에 더 가까움)

        List<ObjectNode> result = optimizer.optimalOrder(
            List.of(c, a, b), startLat, startLng, endLat, endLng
        );

        List<String> names = result.stream().map(n -> n.get("name").asText()).toList();
        assertThat(names).containsExactly("A", "B", "C");
    }

    @Test
    void 여덟개_초과면_최근접_이웃_방식으로도_전체_장소를_빠짐없이_반환한다() {
        List<ObjectNode> places = java.util.stream.IntStream.range(0, 9)
            .mapToObj(i -> place("P" + i, 33.000 + i * 0.005, 126.000))
            .toList();
        double startLat = 32.990, startLng = 126.000; // P0에 가장 가까움

        List<ObjectNode> result = optimizer.optimalOrder(places, startLat, startLng, null, null);

        assertThat(result).hasSize(9);
        List<String> names = result.stream().map(n -> n.get("name").asText()).toList();
        // 일직선상에 등간격으로 놓인 점들이므로 최근접 이웃 탐욕 알고리즘은
        // 결국 순서대로(P0..P8) 방문하게 된다
        assertThat(names).containsExactly(
            "P0", "P1", "P2", "P3", "P4", "P5", "P6", "P7", "P8"
        );
    }
}
