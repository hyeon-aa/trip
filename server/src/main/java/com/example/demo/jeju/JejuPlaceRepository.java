package com.example.demo.jeju;

import java.util.Comparator;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.transaction.Transactional;

public interface JejuPlaceRepository extends JpaRepository<JejuPlace, Long> {

    // categories는 List<String>이 아니라 Postgres 배열 리터럴 문자열이다(예: "{음식점,관광지}",
    // 필터 없으면 "{}"). Hibernate가 네이티브 쿼리에서 Collection 타입 파라미터를 만나면
    // 등장하는 모든 자리를 IN절처럼 "?,?"로 펼쳐버려서 cardinality()/ANY() 안에서 문법
    // 오류가 났다(실제로 겪은 문제 — SQLState 42601) — 그래서 자바 쪽에서 미리
    // "{a,b,c}" 형태 문자열로 합쳐서 스칼라 파라미터로 넘기고, CAST로 배열로 바꾼다.
    // ":categories IS NULL"을 맨 앞에 두는 이유: 지금 호출부는 항상 "{}"(빈
    // 배열)를 넘기지만, 혹시 나중에 null을 그대로 넘기면 CAST(NULL AS text[])는
    // NULL이 되고 cardinality(NULL)=0도 NULL이 되어 이 OR 절 전체가 NULL로
    // 평가된다 — WHERE절에서 NULL은 false 취급이라 필터를 끄는 게 아니라
    // 결과가 통째로 0개가 되는 함정이 있었다(코드 리뷰에서 지적됨).
    @Query(value = """
        SELECT *
        FROM jeju_place
        WHERE (:region IS NULL OR region = :region)
          AND (:categories IS NULL OR cardinality(CAST(:categories AS text[])) = 0 OR main_category = ANY(CAST(:categories AS text[])))
        ORDER BY embedding <=> CAST(:embedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<JejuPlace> findSimilarPlacesWithFilter(
        @Param("embedding") String embedding,
        @Param("region") String region,
        @Param("categories") String categories,
        @Param("limit") int limit
    );

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO jeju_place
        (
            name,
            category,
            main_category,
            region,
            address,
            lat,
            lng,
            description
        )
        VALUES
        (
            :name,
            :category,
            :mainCategory,
            :region,
            :address,
            :lat,
            :lng,
            :description
        )
        """, nativeQuery = true)
    void insertPlace(
        @Param("name") String name,
        @Param("category") String category,
        @Param("mainCategory") String mainCategory,
        @Param("region") String region,
        @Param("address") String address,
        @Param("lat") Double lat,
        @Param("lng") Double lng,
        @Param("description") String description
    );

    @Query("""
        select j
        from JejuPlace j
        where j.embedding is null
        """)
    List<JejuPlace> findWithoutEmbedding(org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE jeju_place
        SET embedding = CAST(:embedding AS vector)
        WHERE id = :id
        """, nativeQuery = true)
    void updateEmbedding(
        @Param("id") Long id,
        @Param("embedding") String embedding
    );

    boolean existsByName(String name);

    List<JejuPlace> findByName(String name);

    List<JejuPlace> findByNameContaining(String name);

    // 이름으로 장소를 찾는 여러 곳(WishlistTools, PlanEditTools)이 공유하는 로직 —
    // 부분 일치(findByNameContaining)만 쓰면 "협재"처럼 흔한 조각이 협재해수욕장/
    // 협재포구/협재해물라면오빠네 등 여러 후보에 다 걸려서, DB가 우연히 반환하는
    // 순서대로 아무거나 골라버리는 문제가 있었다(코드 리뷰에서 지적됨). 정확히
    // 일치하는 이름이 있으면 그걸 우선한다.
    //
    // 정확히 일치하는 이름이 없을 때도(예: "성산일출봉"을 검색했는데 DB에는
    // "성산일출봉 [유네스코 세계자연유산]"으로만 있는 경우) 여전히 애매함이
    // 남는다 — 실사용 검증 중 이 경우 부분 일치 후보 중 전혀 무관한
    // "성산흑돼지두루치기 성산일출봉점"이 먼저 골라진 사례를 실제로 확인했다.
    // 처음엔 "이름이 짧은 것을 우선"하는 휴리스틱을 썼는데, 실제로는 그
    // 식당 이름(16자)이 랜드마크 이름(19자)보다 짧아서 오히려 틀린 답을
    // 골랐다 — 길이가 아니라 "검색어가 이름의 앞쪽에서 시작하는지"가 1차
    // 신호였다("성산일출봉 [...]"은 검색어로 시작하고, "성산흑돼지...
    // 성산일출봉점"은 검색어가 뒤쪽에 붙어있을 뿐이다).
    //
    // 근데 indexOf만으로는 이 파일 맨 위에 적힌 원래 버그 사례("협재"가
    // 협재해수욕장/협재포구/협재해물라면오빠네에 다 걸리는 것)를 못 푼다 —
    // 셋 다 "협재"로 시작해서 indexOf가 전부 0으로 동점이기 때문이다(코드
    // 리뷰에서 지적됨). 동점일 때는 이름이 짧은 쪽을 2차 기준으로 우선한다
    // — 검색어 바로 뒤에 수식어가 적게 붙을수록(=이름이 짧을수록) 검색어
    // 자체를 가리키는 대표 명칭일 가능성이 높다는 판단. 이 2차 기준은 위에서
    // 버린 "길이만 보는" 1차 휴리스틱과 다르다 — indexOf로 먼저 걸러진
    // 후보들 사이에서만 쓰이므로, "성산흑돼지..." 같이 검색어가 뒤에 붙은
    // 이름은 애초에 이 단계까지 오지 않는다.
    default List<JejuPlace> findBestMatchesByName(String name) {
        List<JejuPlace> exact = findByName(name);
        if (!exact.isEmpty()) {
            return exact;
        }
        return findByNameContaining(name).stream()
            .sorted(Comparator
                .<JejuPlace>comparingInt(p -> p.getName() == null ? Integer.MAX_VALUE : p.getName().indexOf(name))
                .thenComparingInt(p -> p.getName() == null ? Integer.MAX_VALUE : p.getName().length()))
            .toList();
    }

    @Query("""
        select j
        from JejuPlace j
        where j.subRegion is null
        """)
    List<JejuPlace> findWithoutSubRegion(org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE jeju_place
        SET sub_region = :subRegion
        WHERE id = :id
        """, nativeQuery = true)
    void updateSubRegion(
        @Param("id") Long id,
        @Param("subRegion") String subRegion
    );
}
