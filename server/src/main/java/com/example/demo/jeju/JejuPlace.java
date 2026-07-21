package com.example.demo.jeju;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "jeju_place", indexes = {
    @Index(name = "idx_jeju_place_region_category", columnList = "region, main_category")
})
public class JejuPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String category;
    private String address;
    private Double lat;
    private Double lng;

    @Column(name = "main_category")
    private String mainCategory; // "관광지", "맛집", "카페" 구별용
    private String region;

    @Column(name = "sub_region")
    private String subRegion; // 읍/면/동 단위 (카카오 좌표→행정구역 역지오코딩), region보다 세분화된 값

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "vector(3072)")
    private String embedding;
}