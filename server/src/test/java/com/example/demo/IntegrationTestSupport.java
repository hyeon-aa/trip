package com.example.demo;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.demo.ai.AiChatService;

// 통합 테스트 공통 기반 — 실제 Postgres(dev/CI와 같은 pgvector 이미지)를
// Testcontainers로 띄우고 @ServiceConnection으로 자동 연결한다(application-test
// .properties의 하드코딩된 spring.datasource.* 값을 대체). postgres 필드가
// static이라 이 클래스를 상속하는 모든 통합 테스트 클래스가 컨테이너 하나를
// 공유한다 — 테스트 클래스마다 새로 띄우는 것보다 훨씬 빠르다(Testcontainers
// 공식 권장 패턴). JVM 종료 시 Testcontainers의 Ryuk 리퍼가 자동으로 정리하므로
// 수동으로 stop()할 필요는 없다.
//
// AiChatService는 @MockitoBean으로 통째로 갈아끼운다 — 통합 테스트가 실제
// Gemini API 쿼터를 쓰지 않아야 하기 때문(이슈 #49, 2026-07-24 쿼터 소진
// 경험 반영). 이 프로젝트의 통합 테스트 대상(위시리스트 CRUD, pgvector 검색)은
// 애초에 AiChatService를 호출하지 않지만, 스프링 컨텍스트 전체를 띄우는
// @SpringBootTest 특성상 실제 AiService 빈이 구성되려면 ChatClient 빈이 필요한데,
// @MockitoBean으로 인터페이스 자체를 대체해두면 그 구성 과정 자체가 생략되어
// 더 확실하다.
@Testcontainers
@SpringBootTest
public abstract class IntegrationTestSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @MockitoBean
    protected AiChatService aiChatService;
}
