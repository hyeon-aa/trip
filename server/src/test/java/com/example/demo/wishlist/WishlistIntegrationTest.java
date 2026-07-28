package com.example.demo.wishlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.IntegrationTestSupport;
import com.example.demo.wishlist.dto.CreateWishlistRequest;
import com.example.demo.wishlist.dto.UpdateWishlistMemoRequest;
import com.example.demo.wishlist.dto.WishlistResponse;

import tools.jackson.databind.ObjectMapper;

// 컨트롤러 → 서비스 → 리포지토리 → 진짜 Postgres(Testcontainers)까지 실제로
// 거치는 통합 테스트. Mockito 유닛 테스트(WishlistServiceTest)는 리포지토리를
// 가짜로 대체해서 SQL 자체가 맞는지는 검증 못 하는데, 여기선 real DB가
// 붙어있어서 그 부분까지 커버된다(이슈 #49). @Transactional로 각 테스트 뒤
// 자동 롤백되어 컨테이너에 데이터가 안 쌓인다.
@AutoConfigureMockMvc
@Transactional
class WishlistIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 위시리스트_생성_메모수정_조회_삭제_전체_흐름() throws Exception {
        CreateWishlistRequest createRequest = new CreateWishlistRequest(
            "협재해수욕장", "관광지", "제주 한림읍", 33.39, 126.24, "친구 추천"
        );

        String createResponseJson = mockMvc.perform(post("/wishlist")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        WishlistResponse created = objectMapper.readValue(createResponseJson, WishlistResponse.class);
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("협재해수욕장");
        assertThat(created.memo()).isEqualTo("친구 추천");

        String getAllAfterCreateJson = mockMvc.perform(get("/wishlist"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        List<WishlistResponse> afterCreate = objectMapper.readValue(
            getAllAfterCreateJson, objectMapper.getTypeFactory().constructCollectionType(List.class, WishlistResponse.class)
        );
        assertThat(afterCreate).extracting(WishlistResponse::id).contains(created.id());

        UpdateWishlistMemoRequest updateMemoRequest = new UpdateWishlistMemoRequest("다음엔 노을 볼 때 가기");
        String updateResponseJson = mockMvc.perform(patch("/wishlist/{id}/memo", created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateMemoRequest)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        WishlistResponse updated = objectMapper.readValue(updateResponseJson, WishlistResponse.class);
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.memo()).isEqualTo("다음엔 노을 볼 때 가기");

        mockMvc.perform(delete("/wishlist/{id}", created.id()))
            .andExpect(status().isNoContent());

        String getAllAfterDeleteJson = mockMvc.perform(get("/wishlist"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        List<WishlistResponse> afterDelete = objectMapper.readValue(
            getAllAfterDeleteJson, objectMapper.getTypeFactory().constructCollectionType(List.class, WishlistResponse.class)
        );
        assertThat(afterDelete).extracting(WishlistResponse::id).doesNotContain(created.id());
    }
}
