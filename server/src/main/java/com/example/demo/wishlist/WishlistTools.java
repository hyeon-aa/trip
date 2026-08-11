package com.example.demo.wishlist;

import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.example.demo.jeju.JejuPlace;
import com.example.demo.jeju.JejuPlaceRepository;
import com.example.demo.wishlist.dto.CreateWishlistRequest;
import com.example.demo.wishlist.dto.WishlistResponse;

// Spring AI Tool 로드맵 2단계(docs/SPRING_AI.md) 연습용 — LLM이 대화 중 판단해서
// 호출하는 "실행 능력"만 감싸고, 실제 저장은 그대로 WishlistService에 맡긴다.
//
// LLM이 이름만 보고 좌표를 지어내면 이 프로젝트 전체 원칙(CLAUDE.md의 "AI는
// 후보 중에서 판단만, 서버가 사실의 유일한 source")을 어기고, 좌표 없는
// 위시리스트 항목은 지도 마커/일정 동선 최적화에서 실제로 못 쓰이는 반쪽짜리
// 데이터가 된다 — 그래서 LLM한테 좌표를 직접 받지 않고, 이미 수집해둔
// jeju_place DB에서 이름으로 실제 장소를 찾아 그 row의 진짜 좌표/카테고리를
// 그대로 저장한다. DB에 없는 장소는 추가하지 못한다(이 Tool의 알려진 한계 —
// 실세계 아무 장소나 커버하려면 카카오 장소검색 API를 별도로 연동해야 함).
@Component
public class WishlistTools {

    private final WishlistService wishlistService;
    private final JejuPlaceRepository jejuPlaceRepository;

    public WishlistTools(WishlistService wishlistService, JejuPlaceRepository jejuPlaceRepository) {
        this.wishlistService = wishlistService;
        this.jejuPlaceRepository = jejuPlaceRepository;
    }

    @Tool(description = "사용자가 대화 중 언급한 장소를 위시리스트에 저장한다. "
        + "이 서비스가 이미 수집해둔 제주 장소 DB에서 이름으로 실제 장소를 찾아 "
        + "그 장소의 진짜 좌표/카테고리로 저장한다 — DB에 없는 장소는 추가할 수 없다.")
    public String addToWishlist(
        @ToolParam(description = "위시리스트에 추가할 장소 이름") String placeName,
        @ToolParam(description = "메모 — 사용자가 왜 저장하고 싶어하는지 등", required = false) String memo
    ) {
        List<JejuPlace> matches = jejuPlaceRepository.findByNameContaining(placeName);
        if (matches.isEmpty()) {
            return "\"" + placeName + "\"라는 이름의 장소를 찾을 수 없어요. 다른 이름으로 다시 말씀해 주시겠어요?";
        }

        JejuPlace place = matches.get(0);
        WishlistResponse saved = wishlistService.add(
            new CreateWishlistRequest(
                place.getName(), place.getCategory(), place.getAddress(),
                place.getLat(), place.getLng(), memo
            )
        );
        return "\"" + saved.name() + "\"을(를) 위시리스트에 추가했어요(id=" + saved.id() + ").";
    }
}
