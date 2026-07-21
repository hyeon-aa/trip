package com.example.demo.wishlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.example.demo.wishlist.dto.CreateWishlistRequest;

class WishlistServiceTest {

    private final WishlistRepository wishlistRepository = mock(WishlistRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final WishlistService wishlistService = new WishlistService(wishlistRepository, eventPublisher);

    @Test
    void 위시리스트_추가시_WishlistAddedEvent를_발행한다() {
        Wishlist saved = new Wishlist();
        saved.setId(1L);
        saved.setName("협재해수욕장");
        when(wishlistRepository.save(any(Wishlist.class))).thenReturn(saved);

        wishlistService.add(new CreateWishlistRequest("협재해수욕장", "관광지", "제주 한림읍", 33.39, 126.24));

        ArgumentCaptor<WishlistAddedEvent> captor = ArgumentCaptor.forClass(WishlistAddedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().wishlistId()).isEqualTo(1L);
        assertThat(captor.getValue().name()).isEqualTo("협재해수욕장");
    }

    @Test
    void 위시리스트_삭제시_WishlistRemovedEvent를_발행한다() {
        wishlistService.delete(5L);

        ArgumentCaptor<WishlistRemovedEvent> captor = ArgumentCaptor.forClass(WishlistRemovedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().wishlistId()).isEqualTo(5L);
    }
}
