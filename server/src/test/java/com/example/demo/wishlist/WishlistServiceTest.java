package com.example.demo.wishlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.example.demo.wishlist.dto.CreateWishlistRequest;
import com.example.demo.wishlist.dto.UpdateWishlistMemoRequest;
import com.example.demo.wishlist.dto.WishlistResponse;

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

        wishlistService.add(new CreateWishlistRequest("협재해수욕장", "관광지", "제주 한림읍", 33.39, 126.24, "친구 추천"));

        ArgumentCaptor<WishlistAddedEvent> captor = ArgumentCaptor.forClass(WishlistAddedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().wishlistId()).isEqualTo(1L);
        assertThat(captor.getValue().name()).isEqualTo("협재해수욕장");
    }

    @Test
    void 위시리스트_추가시_memo를_저장한다() {
        ArgumentCaptor<Wishlist> captor = ArgumentCaptor.forClass(Wishlist.class);
        when(wishlistRepository.save(any(Wishlist.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WishlistResponse response = wishlistService.add(
            new CreateWishlistRequest("협재해수욕장", "관광지", "제주 한림읍", 33.39, 126.24, "SNS에서 봄")
        );

        verify(wishlistRepository).save(captor.capture());
        assertThat(captor.getValue().getMemo()).isEqualTo("SNS에서 봄");
        assertThat(response.memo()).isEqualTo("SNS에서 봄");
    }

    @Test
    void 위시리스트_삭제시_WishlistRemovedEvent를_발행한다() {
        wishlistService.delete(5L);

        ArgumentCaptor<WishlistRemovedEvent> captor = ArgumentCaptor.forClass(WishlistRemovedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().wishlistId()).isEqualTo(5L);
    }

    @Test
    void 위시리스트_메모를_수정한다() {
        Wishlist existing = new Wishlist();
        existing.setId(1L);
        existing.setName("협재해수욕장");
        existing.setMemo("이전 메모");
        when(wishlistRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(wishlistRepository.save(any(Wishlist.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WishlistResponse response = wishlistService.updateMemo(1L, new UpdateWishlistMemoRequest("수정된 메모"));

        assertThat(response.memo()).isEqualTo("수정된 메모");
        assertThat(existing.getMemo()).isEqualTo("수정된 메모");
    }

    @Test
    void 존재하지_않는_위시리스트의_메모를_수정하면_예외가_발생한다() {
        when(wishlistRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wishlistService.updateMemo(999L, new UpdateWishlistMemoRequest("메모")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
