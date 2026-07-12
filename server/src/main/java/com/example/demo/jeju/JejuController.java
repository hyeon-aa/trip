package com.example.demo.jeju;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jeju")
public class JejuController {

    private final TourApiService tourApiService;
    private final SubRegionService subRegionService;

    public JejuController(TourApiService tourApiService, SubRegionService subRegionService) {
        this.tourApiService = tourApiService;
        this.subRegionService = subRegionService;
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

    @PostMapping("/sub-region")
    public String subRegion() {
        subRegionService.fillSubRegions();
        return "읍면동 매핑 완료";
    }
}