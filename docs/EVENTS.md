# 도메인 이벤트 — 개념 정리 및 이 프로젝트에서의 쓰임

스프링 애플리케이션 이벤트 개념 정리 + 이 프로젝트에서 현재 쓰이는 이벤트
목록을 함께 정리한 문서. 코드 위치는 전부
`server/src/main/java/com/example/demo/` 기준.

## 이벤트 발행이 뭔가

"어떤 행동이 일어났다"는 사실과 "그 행동에 어떻게 반응할지"를 분리하는
패턴이다. 예를 들어 위시리스트에 장소를 추가하는 로직(`WishlistService.add`)은
"추가됐다"는 이벤트만 던지고, 그걸 받아서 뭘 할지(로그 남기기, 나중엔 랭킹
점수 올리기 등)는 완전히 다른 클래스가 담당한다 — 서로의 존재를 몰라도 된다.

**왜 나누나**: 나중에 "위시리스트 추가될 때마다 랭킹 점수도 올려줘" 같은
요구사항이 생겨도, `WishlistService`를 안 건드리고 이벤트를 듣는 새 클래스만
추가하면 된다.

## 이벤트 하나를 이해하려면 파일 3개를 봐야 한다

역할이 나뉘어 있어서, 이벤트 하나의 전체 그림을 보려면 항상 이 3가지를
같이 봐야 한다:

1. **데이터 모양 정의** — 이벤트 자체. `record`로 정의된 단순한 데이터
   상자다 (예: `wishlist/WishlistAddedEvent.java`). 로직 없음, 필드만 있음.
2. **언제 발행되는지** — 실제 행동이 일어나는 서비스/컨트롤러 코드
   (`WishlistService.add()`, `PlanChatController` 스케줄 후처리 부분)에서
   `ApplicationEventPublisher.publishEvent(new XxxEvent(...))`를 호출하는 지점.
3. **받아서 뭘 하는지** — 리스너 (`event/EventLoggingListener.java`)의
   `@EventListener` 메서드.

## 동기 vs 비동기 — `@Async`를 쓰는 이유

스프링의 `@EventListener`는 기본적으로 **발행자와 같은 스레드에서 동기
실행**된다. 즉 리스너에서 예외가 나면 그 예외가 이벤트를 발행한 원래 코드로
그대로 전파된다 — 예를 들어 로그만 남기는 리스너에 버그가 있으면, 그것
때문에 "위시리스트 추가 자체가 실패했다"는 엉뚱한 결과가 나올 수 있다.

그래서 이 프로젝트의 리스너는 `@Async`를 붙였다 (`DemoApplication`에
`@EnableAsync` 필요). `@Async` 리스너는 **별도 스레드에서 실행**되므로:
- 리스너에서 예외가 나도 원본 요청(위시리스트 추가, 일정 생성)에는 영향 없음
- 원본 요청이 리스너 처리를 기다리지 않고 바로 끝남

**실제로 검증한 방법**: 리스너 안에 일부러 `throw new RuntimeException(...)`을
넣고 `/wishlist` POST를 호출해봤다 — 로그에는 리스너가 실행되고 예외를 던진
스택 트레이스가 찍혔지만, HTTP 응답은 여전히 200으로 정상 성공했다. 확인 후
테스트용 예외 코드는 제거했다.

## 지금 있는 이벤트들 (전부 로그만 남김 — 실제 집계는 미구현)

| 이벤트 | 발행 위치 | 내용 |
|---|---|---|
| `WishlistAddedEvent` | `WishlistService.add()` | `wishlistId`, `name` |
| `WishlistRemovedEvent` | `WishlistService.delete()` | `wishlistId` |
| `PlaceIncludedInScheduleEvent` | `PlanChatController` (일정 전체가 예외 없이 다 만들어진 뒤, 응답을 보내기 직전) | `source`(`"jeju_place"` 또는 `"wishlist"` — 두 테이블 id 공간이 달라서 구분 필요), `placeId`(실제 DB id, AI 프롬프트의 `p38` 같은 임시 id 아님), `placeName` |

`event/EventLoggingListener.java`가 세 이벤트 전부 `System.out.println`으로
로그만 남긴다 — **DB나 다른 저장소에 저장하지 않는다.** 서버를 재시작하면
이 로그도 사라진다.

**`PlaceIncludedInScheduleEvent`는 장소 id를 실제 엔티티로 바꾸는 시점이
아니라, 그 날짜의 모든 처리(동선 최적화, 시간 배정 등)가 예외 없이 끝난
뒤에 한꺼번에 발행한다.** 만약 뒤쪽 날짜 처리 중 예외가 나서 전체 응답이
실패하면, 앞서 처리된 날짜의 장소들도 이벤트가 발행되면 안 되기 때문이다
— 사용자가 결국 에러만 받았는데 "일정에 포함됨"으로 잘못 집계되는 걸
막기 위함. 그래서 place-resolve 루프에서는 이벤트를 바로 발행하지 않고
리스트에 모아뒀다가, 모든 날짜가 성공적으로 처리된 뒤 한 번에 발행한다.

## 왜 지금은 로그만 남기나 — 앞으로의 계획

이 작업(이슈 #32)은 로드맵 6번(Kafka)·7번(Redis 랭킹)의 선행 작업으로,
"이벤트가 발행되고 전달되는 구조" 자체를 먼저 만드는 것까지가 목표였다.
실제 저장/집계는 다음 단계에서 다룬다:
- **7번(Redis 랭킹)**: `EventLoggingListener`처럼 로그만 찍는 대신, 이
  이벤트들을 받아서 Redis Sorted Set에 실제로 점수를 쌓는 리스너를 추가할
  예정. 지금 만든 이벤트 클래스(`WishlistAddedEvent` 등)는 그때도 그대로
  재사용된다 — 리스너만 하나 더 늘어나는 구조.
- **6번(Kafka)**: 지금은 스프링 프로세스 안에서만 도는 이벤트지만, Kafka를
  도입하면 이 이벤트를 실제 메시지 브로커로 흘려보내서 다른 프로세스/서비스도
  소비할 수 있게 확장 가능.
