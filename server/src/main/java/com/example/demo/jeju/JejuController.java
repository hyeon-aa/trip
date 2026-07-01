package com.example.demo.jeju;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jeju")
public class JejuController {

    private final TourApiService tourApiService;

    public JejuController(TourApiService tourApiService) {
        this.tourApiService = tourApiService;
    }

    @PostMapping("/init/all")
    public String initAll() throws Exception {
        tourApiService.initAllJejuPlaces();
        return "TourAPI 데이터 저장 완료";
    }

    @PostMapping("/embedding")
    public String embedding() throws Exception {
        tourApiService.createEmbeddings();
        return "Embedding 완료";
    }
}