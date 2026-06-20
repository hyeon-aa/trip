package com.example.demo.jeju;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.transaction.Transactional;

public interface JejuPlaceRepository extends JpaRepository<JejuPlace, Long> {

    @Query(value = """
        SELECT * FROM jeju_place
        ORDER BY embedding::vector <=> CAST(:embedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<JejuPlace> findSimilarPlaces(@Param("embedding") String embedding, @Param("limit") int limit);


    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO jeju_place (name, category, address, lat, lng, description, embedding)
        VALUES (:name, :category, :address, :lat, :lng, :description, CAST(:embedding AS vector))
        """, nativeQuery = true)
    void saveWithEmbedding(
        @Param("name") String name,
        @Param("category") String category,
        @Param("address") String address,
        @Param("lat") Double lat,
        @Param("lng") Double lng,
        @Param("description") String description,
        @Param("embedding") String embedding
    );
}