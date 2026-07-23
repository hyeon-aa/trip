# Spring AI — 개념 정리 및 이 프로젝트 도입 계획

Spring AI 도입을 시작하기 전에 정리한 개념 문서. "무엇을 배우는지"와 "이
프로젝트에서 어떤 순서로 적용할지"를 함께 기록한다. 진행하면서 실제 구현
내용이 생기면 이 문서도 같이 갱신한다.

## Spring AI가 뭔가

Spring Boot 생태계에서 LLM 기반 기능(챗봇, 에이전트)을 만들 때 반복적으로
필요한 부품을 표준화해서 제공하는 Spring 팀의 공식 프레임워크. 지금
`AiService`가 Gemini REST API를 WebClient로 직접 호출하고 프롬프트를 문자열로
손수 조립하는 부분을, `ChatClient`/`Advisor`/`Tool`/`VectorStore` 같은 표준
추상화로 대체하는 게 목표다.

## 핵심 개념 4가지 — 서로 다른 역할의 조립 블록

이 넷은 서로 동의어가 아니라, 서로 다른 역할을 하는 블록이고 **Agent는 그
블록들을 조립해서 반복 실행하는 패턴**이다.

- **Model** — 진짜 엔진. 텍스트를 넣으면 텍스트를 뱉는 것 자체. 판단/생성만
  하고 아무것도 실행하지 못한다.
- **Tool** — Model에 붙이는 "실행 능력". LLM이 필요하다고 판단하면 우리가
  등록해둔 실제 Java 메서드를 호출하고, 그 결과(진짜 DB 값 등)를 받아서 다시
  Model에 넘겨준다. **LLM은 판단만 하고, 실행은 우리 코드(Spring Bean)가
  한다**는 경계가 핵심이다.
- **RAG** — Model에 붙이는 "지식 주입 기법". 답하기 전에 관련 정보를 검색해서
  프롬프트에 끼워 넣는 것. Model 자체를 바꾸는 게 아니라 Model이 보는 입력
  (맥락)을 풍부하게 만든다. 이 프로젝트는 이미 pgvector로 이 패턴을 손으로
  구현하고 있다 (`JejuPlaceRepository.findSimilarPlacesWithFilter`).
- **Agent** — 별도 기술이 아니라 **Model + Tool(+ RAG + Memory)을
  판단 → 실행 → 관찰 루프로 반복시키는 행동 패턴**. 이전 Tool 호출 결과를
  보고 다음 행동을 스스로 결정할 수 있다는 게 단순 챗봇과의 차이.

```
Agent (판단 → 실행 → 관찰 루프)
 ├─ Model   : 판단/생성 엔진        → Spring AI: ChatClient/ChatModel
 ├─ Tool    : 실행 능력 (선택적)     → Spring AI: @Tool
 ├─ RAG     : 지식 주입 기법 (선택적) → Spring AI: VectorStore + RetrievalAugmentationAdvisor
 └─ Memory  : 맥락 유지 (선택적)     → Spring AI: ChatMemory
```

## Advisor — 이 흐름 전체를 감싸는 체인

`Advisor`는 요청/응답을 가로채서 로깅·가드레일·RAG 주입 같은 걸 끼워넣는
체인이다. Spring MVC의 인터셉터/AOP와 같은 결 — 핸들러(여기서는 Model 호출)
앞뒤를 감싸서 뭔가를 주입하거나 검사한다.

## 다른 생태계 대응표

같은 문제를 각 생태계가 자기 스타일로 푼 것뿐이라, 개념은 어디서든 거의
1:1로 대응된다.

| 역할 | Spring AI (Java) | LangChain (Python) | NestJS |
|---|---|---|---|
| 만든 주체 | Spring 팀 공식 (2024~) | LangChain Inc. (2022~, 이 분야 원조) | 공식 프레임워크 없음 — 서드파티 사용 |
| 채팅 호출 | `ChatClient` | `ChatModel` (`ChatOpenAI` 등) | Vercel AI SDK `generateText`/`streamText`, 또는 LangChain.js |
| Tool 등록 | `@Tool` 붙은 Java 메서드 | `@tool` 데코레이터 붙은 Python 함수 | Vercel AI SDK `tool({ parameters: zod스키마, execute })` |
| 단계 엮기 | `Advisor` 체인 (인터셉터 스타일) | LCEL(`\|` 파이프) 또는 `Chain`/`Runnable` | 정해진 표준 없음 — 보통 NestJS `Interceptor`로 직접 구성 |
| Memory | `ChatMemory` | `ConversationBufferMemory` 등 | 라이브러리별로 제공 (Vercel AI SDK는 메시지 배열을 직접 관리) |
| RAG | `VectorStore` + `RetrievalAugmentationAdvisor` | `VectorStore`(이름도 동일) + `Retriever` | 라이브러리별 벡터 DB 클라이언트를 직접 조합 |
| 모델 교체 | 의존성(starter)만 교체 | 클래스만 교체 (`ChatOpenAI` → `ChatOllama`) | provider 함수만 교체 (`google(...)` → `openai(...)`) |
| DI/설정 | `application.properties` + Bean 자동 설정 | 코드에서 직접 인스턴스 생성 + 환경변수 | NestJS `@Injectable()` 생성자 주입 (Spring의 `@Service`/`@Autowired`와 동일한 결) |

### 코드로 비교 — "시스템 프롬프트로 채팅 한 번 호출 + Tool 하나 등록"

Spring AI, LangChain은 모델을 **설정 시점에 한 번만** 정해두고, 호출부에서는
이미 구성된 객체를 갖다 쓰기만 한다 — 그래서 아래 호출부 코드만 보면 Gemini가
안 보인다. 어디서 정해지는지 주석으로 표시했다.

Spring AI:
```java
// application.properties: spring.ai.vertex.ai.gemini.chat.options.model=gemini-2.5-flash
// 위 설정만으로 Spring이 Gemini로 세팅된 ChatClient Bean을 자동 생성한다.

@Tool(description = "위시리스트에 장소를 추가한다")
public String addToWishlist(String name, String category, double lat, double lng) {
    wishlistService.add(...);
    return "추가 완료";
}

String reply = chatClient.prompt()   // 이미 Gemini로 구성된 ChatClient
    .system("너는 제주 여행 상담사야")
    .user(userMessage)
    .call()
    .content();
```

LangChain (Python):
```python
# 설정 모듈에서 한 번만 인스턴스 생성 — 여기서 모델을 정한다.
chat_model = ChatGoogleGenerativeAI(model="gemini-2.5-flash")

@tool
def add_to_wishlist(name: str, category: str, lat: float, lng: float) -> str:
    """위시리스트에 장소를 추가한다"""
    wishlist_service.add(...)
    return "추가 완료"

# 호출부에서는 이미 만들어둔 chat_model을 그대로 재사용
response = chat_model.invoke([
    SystemMessage(content="너는 제주 여행 상담사야"),
    HumanMessage(content=user_message),
])
```

반면 Vercel AI SDK(NestJS)는 `generateText()`가 매번 호출하는 순수 함수라서,
미리 구성해둔 객체를 재사용하는 대신 **호출할 때마다 모델을 인자로 직접
넘기는** 스타일이다 (아래 예시에서 `model: google(...)`이 매번 보이는 이유).

NestJS (Vercel AI SDK 기준):
```typescript
@Injectable()
export class ChatService {
  constructor(private wishlistService: WishlistService) {}

  async chat(userMessage: string) {
    const { text } = await generateText({
      model: google('gemini-2.5-flash'),
      system: '너는 제주 여행 상담사야',
      prompt: userMessage,
      tools: {
        addToWishlist: tool({
          description: '위시리스트에 장소를 추가한다',
          parameters: z.object({
            name: z.string(),
            category: z.string(),
            lat: z.number(),
            lng: z.number(),
          }),
          execute: async ({ name, category, lat, lng }) =>
            this.wishlistService.add({ name, category, lat, lng }),
        }),
      },
    });
    return text;
  }
}
```

`@Injectable()` 생성자 주입은 Spring의 `@Service`/`@Autowired`와 완전히 같은
패턴 — NestJS도 DI 프레임워크라 Tool 안에서 다른 서비스를 그대로 주입받아
쓸 수 있다. 다만 NestJS 자체에 "공식 AI 모듈"은 없어서, `Advisor`처럼 요청을
감싸는 표준 체인은 보통 NestJS의 `Interceptor`로 직접 구성해야 한다.

## 이 프로젝트에 적용할 순서

`PlanChatController`가 지금 "한 번에 JSON 통째로 생성"하는 구조라 부분 수정
요청 시 전체가 재생성되는 문제(이슈 #40)가 있는데, Tool 기반 구조로 바꾸면
근본적으로 해결된다. 의존성 순서대로 진행한다 — 각 단계가 앞 단계 위에
얹히므로 순서를 건너뛰지 않는다.

1. **ChatClient 마이그레이션** — `AiService`의 Gemini 직접 호출을 `ChatClient`로
   교체. 나머지 전부의 기반. **완료(이슈 #42) — 아래 "1단계 적용 기록" 참고.**
2. **위시리스트 Tool 등록** — Tool 호출 흐름을 리스크 낮은 것으로 먼저 연습.
3. **일정 부분 수정 Tool 등록** — 이슈 #40을 이 작업으로 해결. "N일차만 수정"이
   가능해짐.
4. **Tool 호출 로그 + 멱등성** — 3번에서 만든 위험도 높은 Tool(일정 수정)에
   붙이는 보호장치.
5. **ChatMemory 도입** — 2~4번에서 Tool 호출이 실제로 도는 상태라야 "메모리와
   Tool이 어떻게 상호작용하는지" 관찰할 대상이 생긴다.
6. **RAG를 VectorStore/Advisor로 전환** — 지금 손으로 짠 pgvector 검색을 표준
   부품으로.
7. **입력 가드레일** — 여행 도메인 벗어난 요청 필터링, 프롬프트 인젝션 방어.
8. **출력 가드레일** — AI가 존재하지 않는 장소/좌표를 지어내면 걸러내기.
9. **Fallback 처리** — Tool 실패/null/부분 실패 시 스택 트레이스 노출 없이
   안전한 응답.
10. **Advisor 체인 통합 + Observability + E2E 검증** — 앞의 9개가 한 체인에서
    맞물려 동작하는지 로그로 확인하는 마무리 작업.

## 1단계 적용 기록 (이슈 #42, Spring AI ChatClient 마이그레이션)

처음 해보는 작업이라 "무엇을 왜 바꿨는지"를 코드 주석이 아니라 여기 자세히
남긴다. 나중에 코드만 보고 "왜 이렇게 바꿨더라?"를 떠올리기 어려운 부분
위주로 정리.

### 무엇이 바뀌었나 — "매번 새 계좌" → "계좌 하나 재사용"

기존 `AiService.chatWithGemini`는 호출될 때마다 `RestClient.create()`로 새
HTTP 클라이언트를 만들고, 그 자리에서 바로 Gemini에 요청을 보냈다. 비유하면
"편지 한 통 부칠 때마다 은행 계좌를 새로 만들었다가 편지 부치고 나면 바로
버리는" 것과 같다 — 계좌 개설(클라이언트 생성) 자체가 공짜가 아닌데, 매번
새로 한 것.

바뀐 뒤에는 생성자에서 `ChatClient.Builder`(Spring Boot가 `spring-ai-starter-
model-google-genai`를 추가하면 자동으로 만들어주는 Bean)를 주입받아 **딱 한
번만** `this.chatClient = chatClientBuilder.build();`로 빌드해 필드에 저장해두고,
`chatWithGemini`가 호출될 때마다 이 필드를 재사용한다 — "계좌를 하나 만들어
두고 편지 보낼 때마다 그 계좌를 계속 쓰는" 방식.

### "준비"와 "호출"은 서로 다른 시점이다

- **준비 (생성자, 딱 한 번)**: `chatClientBuilder.build()` — 이 시점엔 아직
  Gemini에 아무것도 보내지 않는다. "대화할 준비가 된 도구"를 만들어두는 것뿐.
- **호출 (메서드가 불릴 때마다)**: `chatClient.prompt(new Prompt(chatMessages))
  .call().content()` — 이 `.call()`에서 비로소 실제 네트워크 요청이 Gemini로
  나간다.

이 둘을 헷갈리기 쉬운데, "도구를 만드는 것"과 "그 도구로 실제 전화를 거는
것"은 완전히 다른 순간이라는 걸 기억해두면 좋다.

### 코드가 실제로 어떻게 바뀌었나

```java
// Before
public String chatWithGemini(List<ChatMessageDto> messages) {
    RestClient restClient = RestClient.create();  // 매번 새로 생성
    // ...JSON을 Map으로 직접 조립...
    GeminiChatResponse response = restClient.post()
        .uri("https://generativelanguage.googleapis.com/...?key=" + geminiApiKey)
        .body(body).retrieve().body(GeminiChatResponse.class);
    return response.candidates.get(0).content.parts.get(0).text;
}

// After
public String chatWithGemini(List<ChatMessageDto> messages) {
    List<Message> chatMessages = new ArrayList<>();
    // system → SystemMessage, assistant → AssistantMessage, 나머지 → UserMessage
    // (기존처럼 "system은 첫 번째 것만 사용"하는 동작은 그대로 보존)
    return chatClient.prompt(new Prompt(chatMessages)).call().content();
}
```

응답을 손으로 파싱하던 `GeminiChatResponse` 클래스는 이제 `ChatClient`가 파싱을
대신해줘서 필요 없어져 삭제했다. `createEmbedding`은 이번 범위 밖이라 여전히
`GeminiEmbeddingResponse` + raw REST를 그대로 쓴다.

### 재시도가 어떻게 바뀌었나

기존엔 `PlanChatController.chatWithRetry`가 503이 나면 3번까지, 2초씩 고정으로
기다렸다가 재시도하는 코드를 손으로 짜뒀다. `ChatClient`로 옮긴 뒤에는 이
코드가 삭제됐고, 대신 Spring AI 내부의 `RetryTemplate`이 자동으로 재시도한다
(503뿐 아니라 429, 타임아웃 등 더 넓은 범위의 일시적 오류까지 커버).
`application.properties`에 `spring.ai.retry.max-attempts=3`을 명시해서 기존과
비슷한 재시도 횟수 상한을 유지했다 — Spring AI 기본값(`10`회, 지수 백오프)을
그대로 두면 `/plan/chat`의 SSE 타임아웃보다 재시도가 더 오래 걸릴 수 있어서다.

**사이드이펙트**: `VisitTimeAssigner`(날짜별 시간 배정 호출)는 코드를 하나도
안 고쳤는데, 마찬가지로 `AiService.chatWithGemini`를 통해 호출하기 때문에
이제 자동으로 재시도 혜택을 받는다. 기존엔 이 호출부에 재시도가 전혀 없어서
503이 나면 그냥 그 날짜 시간 배정을 조용히 포기했었다 — 의도한 개선.

### 실사용 검증 중 발견한 문제 — SSE 타임아웃

실제로 서버를 띄우고 `/plan/chat`을 호출해서 검증하는 과정에서, 2박3일
일정 요청이 503으로 실패하는 걸 발견했다. 원인은 이 요청이 Gemini를
**순차로 4번** 호출하기 때문이다 — 메인 일정 생성 1번 + 날짜별 시간 배정
3번(하루씩). 각 호출에 걸린 시간을 로그로 재보니:

```
메인 일정 생성:      18.5초
1일차 시간 배정:      9.5초
2일차 시간 배정:      6.9초
3일차 시간 배정:     10.4초
합계:               약 45초
```

`PlanChatController`의 `SseEmitter` 타임아웃이 기존 60초로 잡혀있었는데, 이
45초에 임베딩 생성·DB 조회·JSON 파싱·동선 최적화 같은 나머지 처리 시간까지
더해지면 60초를 넘기기 쉬운 구조였다 — 실제로 두 번 다 503을 받았다. 짧은
프롬프트로 Gemini를 직접 호출했을 땐 2.3초밖에 안 걸렸고 재시도 로그도 전혀
없었던 걸 보면, 이건 Spring AI 클라이언트가 느려서가 아니라 **프롬프트
크기·순차 호출 4번이라는 구조 자체가 원래도 60초에 아슬아슬했던 것**으로
보인다(예전 raw REST 코드로 정확히 비교 측정을 해본 건 아니라서 100%
단정할 수는 없다).

그래서 타임아웃을 `180_000L`(3분)로 늘렸다 (`PlanChatController`). 근본적으로는
여행 일수가 늘어날수록 순차 호출 수도 늘어나는 구조라, 나중에 일수가 더
길어지면 이 타임아웃도 다시 늘리거나 호출을 병렬화하는 걸 고려해야 할 수
있다 — 지금은 3일 일정 기준으로 검증된 값이다.

`AiService.chatWithGemini`에는 호출 하나당 걸린 시간을 로그로 남기는 코드
(`[chatWithGemini] Gemini 응답 소요시간: ...ms`)를 남겨뒀다 — 앞으로 비슷한
지연 문제를 다시 진단할 때 바로 확인할 수 있게.

### 테스트가 왜/어떻게 바뀌었나

`AiService` 생성자에 `ChatClient.Builder`가 추가되면서 `AiServiceTest`의
생성 방식(`new AiService(redisService)`)이 컴파일이 안 돼서
`new AiService(redisService, chatClientBuilder)`로 고쳤다. 겸사겸사 처음으로
`chatWithGemini`에 대한 단위 테스트도 추가했다 — 기존엔 `RestClient.create()`를
메서드 안에서 직접 만들어버려서 그 부분을 대체할 방법이 없어 단위 테스트가
아예 불가능했는데, `ChatClient.Builder`를 주입받는 구조로 바뀌면서 Mockito로
`ChatClient`를 모킹해 "system/user/assistant 메시지가 올바른 타입으로
변환되는지"를 실제 네트워크 호출 없이 검증할 수 있게 됐다.

### 실제 사용한 버전/설정

- `org.springframework.ai:spring-ai-bom:2.0.0` (2026-06-12 GA, Spring Boot 4.0
  베이스라인과 호환)
- `org.springframework.ai:spring-ai-starter-model-google-genai` (Vertex AI가
  아니라 API 키 기반 Gemini Developer API 스타터 — 기존 GCP 서비스 계정 없이
  `gemini.api.key` 그대로 재사용 가능)
- `application.properties`: `spring.ai.google.genai.api-key=${gemini.api.key}`,
  `spring.ai.google.genai.chat.model=gemini-2.5-flash`,
  `spring.ai.retry.max-attempts=3`

각 단계는 별도 이슈로 등록해서 하나씩 브랜치 → 구현 → PR → 머지 사이클을
온전히 돌린다 (`docs/WORKFLOW.md` 참고).
