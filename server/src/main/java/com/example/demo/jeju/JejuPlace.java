package com.example.demo.jeju;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "jeju_place")
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

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String embedding;
}