# ADR-0004: 초기 단계 정책 — 단단한 골격 우선, 디테일은 점진적으로 보강

## Status
Proposed

## Date
2026-04-26 (PLAN-0004 완료 이후 회고적으로 작성)

## Context

### 본 ADR이 회고적으로 작성된 이유

PLAN-0004 (CRUD 게시판 서비스 구현)는 본래 별도 ADR 없이 진행되었다.
당시에는 "ADR-0003에서 정의한 Hexagonal/DDD/CQRS 패턴을 그대로 적용하는 구현 작업"으로 간주되었기 때문이다.

그러나 PLAN-0004 완료 이후 다음 두 가지 사실이 드러났다.

1. **PLAN-0004 안에는 ADR-0003만으로 도출되지 않는 boundary 결정들이 다수 포함되어 있었다.** Output Port를 ISP에 따라 분리한 결정, `existsById`/`count` 우회 메서드를 두지 않기로 한 결정, 페이징을 단일 쿼리로 처리한 결정 등은 "왜 이렇게 설계되었는가?"라는 질문이 가능한 정책이었다.
2. **PLAN-0004는 의도적으로 횡단 관심사(예외 처리, 입력 검증, 테스트, 관측성, 인증)를 최소화했다.** 이 "의도적 미완성" 자체가 장기 프로젝트 운영의 메타 정책이었으나, 어디에도 명시되어 있지 않았다.

본 ADR은 이 두 항목을 회고적으로 명문화하여, 이후 도입되는 ADR/Plan들이 같은 출발점을 공유하도록 한다.

또한 본 ADR을 계기로 ADR/Plan 번호 정합 규약(아래 Decision §3)을 도입한다 — ADR-0004 슬롯이 비어 있던 상태가 이런 정합 규약의 부재에서 비롯되었기 때문이다.

### 프로젝트 성격

- 본 프로젝트는 **장기 학습 목적의 게시판 서비스**다. 운영 트래픽이나 비즈니스 마감이 의사결정의 1차 기준이 아니다.
- 1차 기준은 **단단한 구조**다. 다음 도메인(Comment, Auth, Search, Admin 등)이 추가될 때 같은 골격 위에 자연스럽게 쌓일 수 있어야 하고, 구조 결정이 회고적으로 흔들리는 비용을 최소화해야 한다.
- 운영 디테일(에러 응답 스키마, 로깅 포맷, 검증 정책 등)은 **장기적으로 변경 비용이 큰 API 계약 영역부터 고정**하고, 그 위에 인프라 디테일을 점진적으로 얹는 순서를 따른다.

### 상충 관계

다음 두 접근 사이에서 의식적으로 선택이 필요했다.

| 접근 | 특징 | 본 프로젝트와의 정합성 |
|---|---|---|
| **A. 처음부터 풀스택으로 구현** | CRUD + 예외 + 검증 + 테스트 + 관측성을 한 번에 도입 | 빠르게 "동작하는 것"이 보이지만, 각 영역의 정책이 충분히 정렬되지 않은 상태로 굳어지기 쉬움 |
| **B. 골격 먼저, 디테일은 별도 ADR/Plan으로 점진적 추가** | 초기엔 동작이 빈약하지만, 각 영역이 자기 자리에서 정책 결정을 거쳐 합류 | 장기 학습/확장 목표에 적합. 초기 비용은 더 큼 |

본 프로젝트는 **B를 선택**한다.

## Decision

### 1. 초기 단계 메타 정책

다음 원칙을 PLAN-0004 시점부터 소급하여 명시한다.

#### 1.1 골격은 처음부터 엄격하게

- **Hexagonal Architecture (Ports & Adapters)** — Driving/Driven Adapter, Input/Output Port의 위치와 의존 방향은 첫 PLAN부터 엄격히 적용.
- **DDD 전술 패턴** — Aggregate Root, Value Object, 도메인 전용 팩토리(`create` / `reconstitute` 분리). 도메인은 프레임워크 의존성을 갖지 않는다.
- **CQRS 분리** — Command 경로(`PostCommandService`)와 Query 경로(`PostQueryService`)를 처음부터 분리. 이후 Query 경로의 Read Model 도입 여지를 열어둔다.
- **Output Port의 ISP 적용** — `LoadPostPort` / `SavePostPort` / `DeletePostPort`로 역할별 분리. UseCase는 필요한 Port만 의존.

#### 1.2 횡단 관심사는 의도적으로 후순위

다음 영역은 PLAN-0004 시점에서 의식적으로 최소화하고, **각각 별도의 ADR/Plan으로 도입**한다.

| 영역 | PLAN-0004 시점 상태 | 후속 ADR/Plan |
|---|---|---|
| 예외 처리 / 에러 응답 | `PostNotFoundException` 단일, `IllegalArgumentException` 직접 매핑 | ADR-0005 / PLAN-0005-A·B·C |
| 입력 검증 (Bean Validation) | 미도입 | ADR-0005 / PLAN-0005-B |
| 관측성 (MDC, traceId, 구조화 로깅) | 미도입 | ADR-0005 / PLAN-0005-C |
| 자동화 테스트 전략 | 미도입 | 별도 ADR (예정) |
| 인증 / 인가 | 미도입 | 별도 ADR (예정) |
| 도메인 외부 시간 주입 (`Clock` / `now`) | 도메인 내부에서 `LocalDateTime.now()` 직접 호출 | 별도 ADR (예정) |
| 목록 조회용 Read Model 분리 | `Post` 도메인을 직접 반환 | 별도 ADR (예정) |

이 표는 **"미완성"이 아니라 "의도적 보류"임을 문서화**한다. 후속 작업자가 "왜 여기엔 검증이 없지?"라고 물을 때, 답은 ADR-0004와 해당 후속 ADR을 가리키게 된다.

#### 1.3 디테일은 변경 주기 단위로 분리

후속 영역을 한 PLAN에 묶지 않는다. 변경 주기가 다른 영역(API 계약 vs 인프라 도입)을 한 PLAN에 묶으면, 한쪽 변경이 다른 쪽 리뷰까지 끌어들이는 불필요한 결합이 생긴다.

ADR-0005가 같은 영역(예외)에서도 PLAN-0005-A/B/C 세 단계로 쪼개진 이유가 이 원칙의 첫 적용 사례다.

### 2. PLAN-0004이 내린 구체적 boundary 결정 (회고적 정리)

본 ADR로 명문화하는 PLAN-0004의 결정들이다. 이후 동등한 결정이 다른 도메인(Comment 등)에 적용될 때 일관성의 기준이 된다.

#### 2.1 Output Port — 역할별 분리 (ISP)

```java
public interface LoadPostPort {
    Optional<Post> findById(Long id);
    PostPage findPage(int page, int size);
}
public interface SavePostPort { Post save(Post post); }
public interface DeletePostPort { int deleteById(Long id); }
```

- 거대 `PostRepository` 인터페이스 대신 역할별 Port로 분해.
- UseCase는 자신이 필요로 하는 Port만 의존(예: `GetPostUseCase` → `LoadPostPort`만).

#### 2.2 존재 검증 — 우회 조회 메서드 미도입

- `LoadPostPort`에 `existsById`, `count()`를 두지 않는다.
- **삭제 경로**: `DeletePostPort.deleteById(Long)`이 `int`(삭제된 행 수)를 반환. `0`이면 대상 없음 → Service가 `PostNotFoundException`으로 매핑.
- **업데이트 경로**: 이미 `findById`로 Aggregate를 읽으므로 별도 존재 확인 불필요.

이 결정의 효과는 "조회 우회로의 부재"다. 모든 존재 판단은 본래 의도된 경로(load 또는 delete)의 자연스러운 결과로 도출된다.

#### 2.3 Input Port — Result top-level / Command record 중첩

- **Use Case의 반환 result는 top-level DTO로 분리** (`application/port/in/result/PostListResult`).
- **입력 Command record는 Use Case 인터페이스 내부 중첩 허용** (`CreatePostUseCase.CreatePostCommand`).

이유: 결과(반환)는 외부 어댑터가 의존하는 계약이라 top-level로 노출이 자연스럽다. 입력(Command)은 어댑터 → 애플리케이션 단방향 결합이라 Use Case 내부 중첩이 가독성 면에서 더 좋다.

#### 2.4 페이징 — 단일 쿼리

- `LoadPostPort.findPage(page, size)`가 `PostPage(items, totalElements)`를 한 번에 반환.
- `count()`를 별도로 두지 않음으로써 호출자가 두 번 쿼리하는 패턴이 코드상 차단됨.

#### 2.5 페이징 모델의 위치 — `application/common/PostPage`

- 페이징 모델은 `application` 계층의 **공용 위치**에 둠 (Output Port 전용도 아니고, Input Port result도 아니므로).
- **알려진 약점**: 이 위치는 "어느 경계의 모델인지"가 흐릿하며, `PostListResult`(Input Port result)와 혼동 여지가 있다.
- 향후 정리 후보로 식별됨 (별도 PLAN에서 결정).

#### 2.6 Web Adapter의 예외/에러 위치

- `GlobalExceptionHandler`, `ErrorResponse` DTO는 **Web Adapter 하위**(`adapter/in/web/exception`, `adapter/in/web/dto/response`)에 위치.
- 이유: HTTP 응답 변환은 Driving Adapter의 관심사. Application/Domain은 예외만 던지고 변환은 모름.

### 3. ADR/Plan 번호 정합 규약 (본 ADR로 도입)

본 ADR이 회고적으로 작성된 사건 자체가 ADR/Plan 정합 규약의 부재에서 비롯되었다. 이후 같은 누락을 줄이기 위해 다음 규약을 채택한다.

- **ADR-NNNN과 그로부터 파생된 Plan은 같은 번호 NNNN을 공유한다.**
- 한 ADR이 여러 Plan으로 펼쳐질 경우 Plan은 `-A`, `-B`, `-C` 접미사로 구분한다 (예: `PLAN-0005-A`, `PLAN-0005-B`).
- ADR 없이 단독 Plan을 만드는 경우, 그 NNNN의 ADR 슬롯은 비어 있을 수 있다. README 인덱스에 사유를 한 줄 명시한다.
- 회고적으로 ADR을 채울 수 있다. 이 경우 Status는 `Proposed`로 시작하고, 정상 흐름과 동일하게 사람의 검토를 거쳐 `Accepted`로 전이한다.

### 4. 본 ADR의 적용 범위

- 본 ADR은 **PLAN-0004 시점의 의도와 결정**을 명문화한다. 새로운 코드 변경을 요구하지 않는다.
- 본 ADR이 명시한 "후속 ADR/Plan 후보 목록"은 향후 작업의 우선순위 판단 기준으로 활용된다.

## Consequences

### 긍정

- "왜 PLAN-0004엔 검증이 없지?", "왜 페이징은 이렇게 했지?" 같은 질문에 답할 수 있는 단일 출처가 생긴다.
- 후속 ADR/Plan들이 동일한 메타 정책("골격 먼저, 디테일은 변경 주기별 분리")을 공유하므로, Comment/Auth/Search 도입 시 같은 패턴을 재사용할 수 있다.
- ADR/Plan 번호 정합 규약이 명문화되어, 이후 새 결정이 누락 없이 ADR로 굳혀진다.

### 부정 / 비용

- 회고적 ADR이라는 점에서 ADR 본연의 시간성("결정 시점에 작성")이 일부 약화된다. 본 ADR이 그 사례를 정당화하는 메타 규약을 함께 제공하지만, 회고 ADR이 반복되면 ADR 신뢰도에 누적 비용이 된다.
- "후속 ADR 후보 목록"은 시간이 지남에 따라 일부가 무의미해질 수 있다(예: 우선순위 변경, 결정 무효화). 정기적인 검토가 필요하다.
- ADR/Plan 번호 정합 규약은 단독 Plan이 빈 ADR 슬롯을 남기는 비대칭을 만든다. 인덱스(README) 관리에 약간의 운영 부담이 추가된다.

## Related

- ADR-0001: Architecture Decision Records 도입
- ADR-0003: Clean/Hexagonal Architecture + DDD + CQRS
- ADR-0005: 예외/에러 응답 정책 (본 ADR이 명시한 "디테일 점진적 보강"의 첫 사례)
- PLAN-0004: CRUD 게시판 서비스 구현 (본 ADR이 회고적으로 정리하는 대상; 이미 done)
