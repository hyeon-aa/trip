package com.example.demo.jeju;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jeju")
public class JejuController {

    private final VisitJejuService visitJejuService;
    private final JejuInitService jejuInitService;

    public JejuController(VisitJejuService visitJejuService, JejuInitService jejuInitService) {
        this.visitJejuService = visitJejuService;
        this.jejuInitService = jejuInitService;
    }

    @PostMapping("/init/tourism")
    public String initTourism() throws Exception {
        visitJejuService.initFromVisitJeju();
        return "비짓제주 관광지 데이터 입력 완료!";
    }

    @PostMapping("/init/food")
    public String initFood() throws Exception {
        jejuInitService.initJejuPlaces();
        return "제주 맛집 데이터 입력 완료!";
    }

    
}