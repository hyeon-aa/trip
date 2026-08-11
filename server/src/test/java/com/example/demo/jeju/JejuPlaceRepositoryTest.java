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
    void 정확히_일치하는_이름이_없으면_부분_일치_중_이름이_짧은_것을_우선한다() {
        // 실사용 검증 중 실제로 재현된 사례 — "성산일출봉"을 검색했는데 DB에는
        // 정확히 그 이름이 없고, 부분 일치로 전혀 무관한 음식점이 먼저 나왔었다.
        JejuPlace unrelatedRestaurant = place("성산흑돼지두루치기 성산일출봉점");
        JejuPlace theLandmark = place("성산일출봉 [유네스코 세계자연유산]");

        when(repository.findByName("성산일출봉")).thenReturn(List.of());
        when(repository.findByNameContaining("성산일출봉"))
            .thenReturn(List.of(unrelatedRestaurant, theLandmark));

        List<JejuPlace> result = repository.findBestMatchesByName("성산일출봉");

        assertThat(result.get(0)).isEqualTo(theLandmark);
    }
}
