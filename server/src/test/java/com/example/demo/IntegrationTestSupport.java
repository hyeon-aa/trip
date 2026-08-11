package com.example.demo;

import com.example.demo.ai.AiChatService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

// 통합 테스트 공통 기반 — 실제 Postgres(dev/CI와 같은 pgvector 이미지)를
// Testcontainers로 띄우고 @ServiceConnection으로 자동 연결한다(application-test
// .properties의 하드코딩된 spring.datasource.* 값을 대체).
//
// 일부러 @Testcontainers/@Container(JUnit5 확장)를 안 쓴다 — 그 확장은 static
// 필드로 선언해도 "컨테이너 하나를 여러 클래스가 공유"하지 않는다. 실제로는
// 테스트 클래스마다 JUnit5의 클래스 단위 ExtensionContext.Store가 따로 있어서,
// 클래스 하나가 끝날 때마다 그 클래스의 store가 닫히면서 컨테이너가 stop되고,
// 다음 클래스가 다시 처음부터 start한다 — "static이니까 공유되겠지"라는
// 직관과 반대로 동작한다(Testcontainers 소스의 TestcontainersExtension.
// StoreAdapter#close가 매 클래스 종료 시 container.stop()을 호출하는 것으로
// 확인). 그래서 대신 static 초기화 블록에서 직접 start()하는 "싱글턴 컨테이너"
// 패턴을 쓴다 — Testcontainers 공식 문서가 "여러 테스트 클래스가 컨테이너
// 하나를 진짜로 공유"하고 싶을 때 권장하는 방식이다. @Container 없이 그냥
// start()만 해도 @ServiceConnection은 정상 동작한다(스프링 쪽 자동 연결
// 메커니즘은 JUnit5 확장과 무관하게 필드를 직접 스캔한다). stop()은 절대
// 호출하지 않고, JVM 종료 시 Testcontainers의 Ryuk 리퍼가 컨테이너를 정리한다.
//
// AiChatService는 @MockitoBean으로 통째로 갈아끼운다 — 통합 테스트가 실제
// Gemini API 쿼터를 쓰지 않아야 하기 때문(이슈 #49, 2026-07-24 쿼터 소진
// 경험 반영). 이 프로젝트의 통합 테스트 대상(위시리스트 CRUD, pgvector 검색)은
// 애초에 AiChatService를 호출하지 않지만, 스프링 컨텍스트 전체를 띄우는
// @SpringBootTest 특성상 실제 AiService 빈이 구성되려면 ChatClient 빈이 필요한데,
// @MockitoBean으로 인터페이스 자체를 대체해두면 그 구성 과정 자체가 생략되어
// 더 확실하다.
@SpringBootTest
public abstract class IntegrationTestSupport {

  @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg16");

  static {
    postgres.start();
  }

  @MockitoBean protected AiChatService aiChatService;
}
