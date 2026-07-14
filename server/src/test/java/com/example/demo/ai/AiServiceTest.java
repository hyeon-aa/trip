package com.example.demo.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.example.demo.redis.RedisService;

class AiServiceTest {

    private final RedisService redisService = mock(RedisService.class);
    private final AiService aiService = new AiService(redisService);

    @Test
    void 동일한_텍스트는_항상_같은_캐시_키를_만든다() {
        String key1 = aiService.cacheKey("혼자 2박 3일 자연 위주 여행");
        String key2 = aiService.cacheKey("혼자 2박 3일 자연 위주 여행");

        assertThat(key1).isEqualTo(key2);
    }

    @Test
    void 다른_텍스트는_다른_캐시_키를_만든다() {
        String key1 = aiService.cacheKey("혼자 2박 3일 자연 위주 여행");
        String key2 = aiService.cacheKey("가족 3박 4일 맛집 위주 여행");

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void 캐시에_값이_있으면_API_호출_없이_그대로_반환한다() {
        when(redisService.get(anyString())).thenReturn("[0.1, 0.2, 0.3]");

        String result = aiService.createEmbedding("아무 텍스트");

        assertThat(result).isEqualTo("[0.1, 0.2, 0.3]");
        verify(redisService, never()).save(anyString(), anyString());
    }
}
