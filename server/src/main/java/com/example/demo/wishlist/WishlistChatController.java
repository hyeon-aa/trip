package com.example.demo.wishlist;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.wishlist.dto.WishlistChatRequest;
import com.example.demo.wishlist.dto.WishlistChatResponse;

// Spring AI Tool 로드맵 2단계(docs/SPRING_AI.md) 연습용 — /plan/chat과 완전히
// 분리된 별도 엔드포인트다. /plan/chat은 JSON Schema(outputSchema)로 출력
// 형태를 강제하는 구조라, Tool 호출(함수 호출 메커니즘)을 같은 곳에서 처음
// 실험하면 두 메커니즘이 어떻게 상호작용하는지 모른 채 리스크가 커진다 —
// 그래서 리스크 낮은 위시리스트로 먼저 격리해서 연습한다.
@RestController
@RequestMapping("/wishlist")
public class WishlistChatController {

    private final ChatClient chatClient;
    private final WishlistTools wishlistTools;

    public WishlistChatController(ChatClient.Builder chatClientBuilder, WishlistTools wishlistTools) {
        this.chatClient = chatClientBuilder.build();
        this.wishlistTools = wishlistTools;
    }

    @PostMapping("/chat")
    public WishlistChatResponse chat(@RequestBody WishlistChatRequest request) {
        String reply = chatClient.prompt()
            .system("너는 제주 여행 위시리스트를 관리해주는 도우미야. 사용자가 장소를 "
                + "저장해달라고 하면 addToWishlist 도구를 사용해서 실제로 저장하고, "
                + "결과를 자연스러운 한국어로 알려줘. 저장을 요청한 게 아니면 도구를 "
                + "쓰지 말고 평범하게 대답해.\n\n"
                + "매우 중요: addToWishlist 도구를 호출했다면, 그 도구가 실제로 반환한 "
                + "메시지의 성공/실패 여부를 반드시 그대로 반영해서 답해야 해. 도구가 "
                + "\"찾을 수 없어요\"라고 반환했는데 \"추가했어요\"라고 답하는 것처럼, "
                + "도구의 실제 결과와 다른 내용을 지어내서 답하면 절대 안 돼 — 도구를 "
                + "호출한 이상 그 반환값이 유일한 사실이고, 네가 스스로 성공/실패를 "
                + "판단하는 게 아니야.")
            .user(request.message())
            .tools(wishlistTools)
            .call()
            .content();

        return new WishlistChatResponse(reply);
    }
}
