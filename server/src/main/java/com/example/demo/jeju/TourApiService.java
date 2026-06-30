package com.example.demo.jeju;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.example.demo.ai.AiService;

import tools.jackson.databind.JsonNode;

@Service
public class TourApiService {

    private static final Map<Integer, String> CONTENT_TYPES =
        Map.of(
            12, "관광지",
            14, "문화시설",
            28, "레포츠",
            39, "음식점"
        );

    private final TourApiClient tourApiClient;
    private final TourDetailService tourDetailService;
    private final JejuPlaceRepository jejuPlaceRepository;
    private final AiService aiService;

    public TourApiService(
        TourApiClient tourApiClient,
        TourDetailService tourDetailService,
        JejuPlaceRepository jejuPlaceRepository,
        AiService aiService
    ) {
        this.tourApiClient = tourApiClient;
        this.tourDetailService = tourDetailService;
        this.jejuPlaceRepository = jejuPlaceRepository;
        this.aiService = aiService;
    }

    /**
     * TourAPI → DB 저장
     */
    public void initAllJejuPlaces() throws Exception {

        for (Integer contentTypeId : CONTENT_TYPES.keySet()) {

            System.out.println(
                "\n=============================="
            );

            System.out.println(
                CONTENT_TYPES.get(contentTypeId)
                    + " 수집 시작"
            );

            saveContentType(contentTypeId);
        }
    }

    /**
     * 저장된 데이터 Embedding 생성
     */
    public void createEmbeddings() {

        List<JejuPlace> places =
            jejuPlaceRepository.findWithoutEmbedding(
                PageRequest.of(0, 100)
            );

        System.out.println(
            "Embedding 대상 : "
                + places.size()
        );

        for (JejuPlace place : places) {

            try {

                String embeddingText =
                    String.format(
                        "%s %s %s %s %s",
                        place.getName(),
                        place.getCategory(),
                        place.getAddress(),
                        place.getRegion(),
                        place.getDescription()
                    );

                String embedding =
                    aiService.createEmbedding(
                        embeddingText
                    );

                jejuPlaceRepository.updateEmbedding(
                    place.getId(),
                    embedding
                );

                System.out.println(
                    "Embedding 완료 : "
                        + place.getName()
                );

            } catch (Exception e) {

                System.out.println(
                    "Embedding 실패! : " + place.getName()
                );
            
                e.printStackTrace();
            }
        }
    }

    private void saveContentType(
        int contentTypeId
    ) throws Exception {

        for (int page = 1; page <= 200; page++) {

            JsonNode items =
                tourApiClient.getPlaces(
                    contentTypeId,
                    page
                );

            if (
                items == null
                || items.isMissingNode()
                || items.isEmpty()
            ) {
                break;
            }

            for (JsonNode item : items) {

                savePlace(
                    item,
                    contentTypeId
                );
            }
        }
    }

    private void savePlace(
        JsonNode item,
        int contentTypeId
    ) {

        try {

            String name =
                item.path("title")
                    .asText()
                    .trim();

            if (name.isBlank()) {
                return;
            }

            if (
                jejuPlaceRepository.existsByName(name)
            ) {
                return;
            }

            String address =
                item.path("addr1")
                    .asText("");

            double lat =
                item.path("mapy")
                    .asDouble();

            double lng =
                item.path("mapx")
                    .asDouble();

            String contentId =
                item.path("contentid")
                    .asText();

            String overview =
                tourDetailService.getOverview(
                    contentId
                );

            String intro =
                tourDetailService.getDetailIntro(
                    contentId,
                    contentTypeId
                );

            String description =
                overview
                    + "\n"
                    + intro;

            String category =
                CONTENT_TYPES.get(
                    contentTypeId
                );

            String region =
                JejuPlaceUtil.getRegion(
                    lat,
                    lng
                );

            jejuPlaceRepository.insertPlace(
                name,
                category,
                category,
                region,
                address,
                lat,
                lng,
                description
            );

            System.out.println(
                "저장 완료 : "
                    + name
            );

        } catch (Exception e) {

            System.out.println(
                "저장 실패 : "
                    + e.getMessage()
            );
        }
    }

}