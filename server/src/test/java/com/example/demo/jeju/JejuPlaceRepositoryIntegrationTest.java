package com.example.demo.jeju;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.IntegrationTestSupport;

// findSimilarPlacesWithFilter는 문자열로 조립한 Postgres 배열 리터럴(categories)을
// CAST해서 쓰는 네이티브 쿼리라(JejuPlaceRepository 참고), Mockito로는 SQL 문법
// 자체가 맞는지 검증할 수 없다 — 실제로 예전에 이 방식의 문법 오류(SQLState
// 42601)를 라이브 테스트로만 잡은 적이 있다. Testcontainers로 진짜 pgvector
// 컬럼에 대해 지역/카테고리 필터와 거리순 정렬이 실제로 맞는지 확인한다(이슈 #49).
@Transactional
class JejuPlaceRepositoryIntegrationTest extends IntegrationTestSupport {

    private static final int DIMENSIONS = 3072;

    @Autowired
    private JejuPlaceRepository jejuPlaceRepository;

    private Long eastTourismId;
    private Long eastFoodId;
    private Long westTourismId;

    @BeforeEach
    void seedPlaces() {
        eastTourismId = insertPlace("성산일출봉", "관광지", "동부", vectorLiteral(1.0, 0.0));
        eastFoodId = insertPlace("동부 맛집", "음식점", "동부", vectorLiteral(1.0, 1.0));
        westTourismId = insertPlace("협재해수욕장", "관광지", "서부", vectorLiteral(0.0, 1.0));
    }

    @Test
    void 지역_필터로_동부만_걸러진다() {
        List<JejuPlace> result = jejuPlaceRepository.findSimilarPlacesWithFilter(
            vectorLiteral(1.0, 0.0), "동부", "{}", 10
        );

        assertThat(result).extracting(JejuPlace::getId)
            .containsExactlyInAnyOrder(eastTourismId, eastFoodId)
            .doesNotContain(westTourismId);
    }

    @Test
    void 카테고리_필터로_관광지만_걸러진다() {
        List<JejuPlace> result = jejuPlaceRepository.findSimilarPlacesWithFilter(
            vectorLiteral(1.0, 0.0), null, "{관광지}", 10
        );

        assertThat(result).extracting(JejuPlace::getId)
            .containsExactlyInAnyOrder(eastTourismId, westTourismId)
            .doesNotContain(eastFoodId);
    }

    @Test
    void 지역과_카테고리_필터를_동시에_걸_수_있다() {
        List<JejuPlace> result = jejuPlaceRepository.findSimilarPlacesWithFilter(
            vectorLiteral(1.0, 0.0), "동부", "{관광지}", 10
        );

        assertThat(result).extracting(JejuPlace::getId).containsExactly(eastTourismId);
    }

    @Test
    void 필터가_없으면_코사인_거리가_가까운_순서로_정렬된다() {
        List<JejuPlace> result = jejuPlaceRepository.findSimilarPlacesWithFilter(
            vectorLiteral(1.0, 0.0), null, "{}", 10
        );

        assertThat(result).extracting(JejuPlace::getId)
            .containsExactly(eastTourismId, eastFoodId, westTourismId);
    }

    private Long insertPlace(String name, String mainCategory, String region, String embedding) {
        // insertPlace(category, mainCategory, ...) 둘 다에 같은 값을 넘긴다 — 이
        // 테스트는 category/main_category 구분이 필요 없어서 의도적으로 같게 쓴 것.
        jejuPlaceRepository.insertPlace(
            name, mainCategory, mainCategory, region, "제주도 어딘가", 33.4, 126.5, "테스트용 설명"
        );
        Long id = jejuPlaceRepository.findByName(name).get(0).getId();
        jejuPlaceRepository.updateEmbedding(id, embedding);
        return id;
    }

    // 3072차원 중 앞 두 값만 지정하고 나머지는 0으로 채운 pgvector 리터럴을
    // 만든다. 코사인 거리(<=>)는 방향만 보므로, 앞 두 값의 비율만으로 서로
    // 다른 장소 간 거리를 통제 가능하게 만들 수 있다.
    private String vectorLiteral(double first, double second) {
        double[] values = new double[DIMENSIONS];
        values[0] = first;
        values[1] = second;
        return "[" + IntStream.range(0, DIMENSIONS)
            .mapToObj(i -> String.valueOf(values[i]))
            .collect(Collectors.joining(",")) + "]";
    }
}
