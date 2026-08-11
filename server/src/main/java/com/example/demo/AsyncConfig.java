package com.example.demo;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AsyncConfig {

  // 날짜별 방문 시간 배정(VisitTimeAssigner)이 Gemini 호출을 병렬로 쏠 때 쓰는
  // 전용 스레드풀. 기본 CompletableFuture.runAsync()는 공용
  // ForkJoinPool.commonPool()(코어 수 - 1개짜리 소규모 풀, CPU 연산용으로
  // 설계됨)을 쓰는데, 여긴 10~25초 걸리는 블로킹 네트워크 호출이라 이 작은
  // 공용 풀을 오래 붙잡으면 앱의 다른 병렬 작업까지 덩달아 느려질 수 있다
  // (코드 리뷰에서 지적됨) — 그래서 블로킹 I/O 전용으로 따로 뗀다. 필요할
  // 때마다 스레드를 만들고 60초 유휴 시 정리하는 캐시 풀이라, 트래픽이 없을
  // 땐 자원을 거의 안 먹으면서도 여러 날짜가 한꺼번에 몰려도 대기 없이 바로
  // 처리된다. Java 17이라 virtual thread는 못 쓴다(21부터 가능).
  @Bean
  public ExecutorService visitTimeAssignerExecutor() {
    return Executors.newCachedThreadPool();
  }
}
