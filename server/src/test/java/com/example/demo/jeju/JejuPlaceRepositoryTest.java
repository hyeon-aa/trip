package com.example.demo.jeju;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

// findBestMatchesByName은 JpaRepository 구현이 필요 없는 순수 default 메서드
// 로직이라, Testcontainers 없이 Mockito만으로 검증한다. CALLS_REAL_METHODS로
// 실제 default 메서드 구현을 타게 하고, 그 안에서 부르는 findByName/
// findByNameContaining만 stub한다.
class JejuPlaceRepositoryTest {

    private final JejuPlaceRepository repository =
        mock(JejuPlaceRepository.class, Mockito.CALLS_REAL_METHODS);

    private JejuPlace place(String name) {
        JejuPlace p = new JejuPlace();
        p.setName(name);
        return p;
    }

    @Test
    void 정확히_일치하는_이름이_있으면_그것만_반환한다() {
        JejuPlace exact = place("협재해수욕장");
        when(repository.findByName("협재해수욕장")).thenReturn(List.of(exact));

        List<JejuPlace> result = repository.findBestMatchesByName("협재해수욕장");

        assertThat(result).containsExactly(exact);
    }

    @Test
    void 정확히_일치하는_이름이_없으면_검색어가_앞쪽에서_시작하는_후보를_우선한다() {
        // 실사용 검증 중 실제로 재현된 사례 — "성산일출봉"을 검색했는데 DB에는
        // 정확히 그 이름이 없고, 부분 일치로 전혀 무관한 음식점이 먼저 나왔었다.
        // (테스트 이름 주의: "이름이 짧은 것"이 아니라 "검색어로 시작하는 것"이
        // 기준이다 — 이 사례에서는 오히려 무관한 음식점 이름이 랜드마크 이름보다
        // 짧아서, 길이 기준이었다면 여전히 틀렸을 것이다.)
        JejuPlace unrelatedRestaurant = place("성산흑돼지두루치기 성산일출봉점");
        JejuPlace theLandmark = place("성산일출봉 [유네스코 세계자연유산]");

        when(repository.findByName("성산일출봉")).thenReturn(List.of());
        when(repository.findByNameContaining("성산일출봉"))
            .thenReturn(List.of(unrelatedRestaurant, theLandmark));

        List<JejuPlace> result = repository.findBestMatchesByName("성산일출봉");

        assertThat(result.get(0)).isEqualTo(theLandmark);
    }

    @Test
    void 검색어로_시작하는_후보끼리_동점이면_이름이_짧은_쪽을_우선한다() {
        // 이 클래스 상단 주석에 적힌 원래 버그 사례 — "협재"로 셋 다 시작해서
        // indexOf만으로는 동점이 나는 경우. 이럴 때만 이름 길이를 2차 기준으로
        // 쓴다(1차로 길이만 봤다가 위 테스트 사례에서 틀려서 폐기했던 것과는
        // 다른 용도 — 여기선 indexOf가 이미 같은 후보들 사이에서만 비교한다).
        JejuPlace beach = place("협재해수욕장");
        JejuPlace port = place("협재포구");
        JejuPlace restaurant = place("협재해물라면오빠네");

        when(repository.findByName("협재")).thenReturn(List.of());
        when(repository.findByNameContaining("협재"))
            .thenReturn(List.of(beach, restaurant, port));

        List<JejuPlace> result = repository.findBestMatchesByName("협재");

        assertThat(result.get(0)).isEqualTo(port);
    }
}
