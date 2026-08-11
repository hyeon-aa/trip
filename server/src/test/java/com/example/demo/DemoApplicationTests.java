package com.example.demo;

import org.junit.jupiter.api.Test;

// 예전엔 src/test/resources/application.properties의 하드코딩된 DB 계정(test/test,
// CI 서비스 컨테이너 기준)이 로컬 dev DB 계정과 안 맞아서 이 테스트가 로컬에서
// 조용히 실패했었다 — IntegrationTestSupport(Testcontainers)를 상속하면서 해결됨
// (이슈 #49).
class DemoApplicationTests extends IntegrationTestSupport {

  @Test
  void contextLoads() {}
}
