package com.example.demo.event;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.example.demo.plan.PlaceIncludedInScheduleEvent;
import com.example.demo.wishlist.WishlistAddedEvent;
import com.example.demo.wishlist.WishlistRemovedEvent;

// 지금은 로그만 남긴다 — 실제 집계/랭킹은 이후 이슈(Kafka, Redis 랭킹)에서 다룬다.
// @Async로 발행자와 다른 스레드에서 실행되므로, 여기서 예외가 나도 원본 요청
// (위시리스트 추가/일정 생성)에는 영향을 주지 않는다.
@Component
public class EventLoggingListener {

    @Async
    @EventListener
    public void onWishlistAdded(WishlistAddedEvent event) {
        System.out.println("[event] 위시리스트 추가: id=" + event.wishlistId() + ", name=" + event.name());
    }

    @Async
    @EventListener
    public void onWishlistRemoved(WishlistRemovedEvent event) {
        System.out.println("[event] 위시리스트 삭제: id=" + event.wishlistId());
    }

    @Async
    @EventListener
    public void onPlaceIncludedInSchedule(PlaceIncludedInScheduleEvent event) {
        System.out.println(
            "[event] 일정에 장소 포함: source=" + event.source()
                + ", id=" + event.placeId()
                + ", name=" + event.placeName()
        );
    }
}
