package com.example.demo.jeju;

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
