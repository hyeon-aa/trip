package com.example.demo.jeju;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.transaction.Transactional;

public interface JejuPlaceRepository extends JpaRepository<JejuPlace, Long> {

    @Query(value = """
        SELECT *
        FROM jeju_place
        WHERE (:region IS NULL OR region = :region)
          AND (:mainCategory IS NULL OR main_category = :mainCategory)
        ORDER BY embedding::vector <=> CAST(:embedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<JejuPlace> findSimilarPlacesWithFilter(
        @Param("embedding") String embedding,
        @Param("region") String region,
        @Param("mainCategory") String mainCategory,
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
}