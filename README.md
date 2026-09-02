## 기술 스택

- Java 17, Spring Boot 3.5, Spring Data JPA (Hibernate), Spring Security + JWT
- MySQL (운영), H2 (테스트)
- Redis (Redisson 3.37.0) - 분산 락
- Resilience4j - 서킷 브레이커
- Gradle, JUnit 5, Docker

## 주요 기능 및 구현 내용

### 인증 (Spring Security + JWT)
- 회원가입 시 비밀번호를 BCrypt로 암호화하여 저장
- 로그인 성공 시 accessToken/refreshToken 발급
- `JwtAuthenticationEntryPoint`를 구현해 인증 실패 시 403이 아닌 401로 정확히 응답
- `@AuthenticationPrincipal`로 토큰에서 사용자 정보를 추출해, URL 파라미터 조작으로 타인 계좌에 접근하는 것을 원천 차단
- JWT 서명 키를 환경변수로 고정하여, 서버 재시작 시 기존 토큰이 무효화되는 문제 해결

### 계좌 이체 기본 기능
- 이체 성공/잔액 부족/유효하지 않은 금액/존재하지 않는 계좌/타인 계좌 접근 등 5가지 시나리오에 대한 예외 처리
- `@Transactional` 기반으로, 이체 중간에 실패하면 잔액 변경이 롤백되어 정합성이 깨지지 않음을 검증

### 동시성 제어
- 비관적 락(`SELECT ... FOR UPDATE`)을 이체 로직에 적용, 데드락 방지를 위해 계좌 ID가 작은 순서로 고정하여 락 획득
- 낙관적 락(`@Version`) + 재시도 로직(`TransactionTemplate` 활용, 최대 5회 재시도)
- H2 기반 동시성 테스트 코드 작성, 1000건 동시 이체 시나리오로 정합성 검증

| 시나리오 | 소요시간 | 정합성 |
|---|---|---|
| 락 없음 | 197ms | 깨짐 (984,000원 유실) |
| 비관적 락 | 2,459ms | 100% 일치 |
| 낙관적 락 + 재시도 | 2,965ms | 성공 건은 일치 (1000건 중 364건 재시도 5회 후 실패) |

### 중복 요청 방지 (Idempotency-Key)
- 클라이언트가 생성한 UUID를 `Idempotency-Key` 헤더로 전달, 같은 키로 재요청 시 재실행 없이 기존 처리 결과 반환
- 계좌 락 획득 이후 시점에 중복 체크를 수행해, 동시에 들어온 중복 요청도 안전하게 처리
- DB unique 제약 + 예외 처리로 극히 드문 동시 경합에 대한 최후 방어선 마련
- 수동 테스트(같은 키 재요청/다른 키 요청/헤더 누락 400 확인) + 자동화 테스트(동일 키 50건 동시 요청 시 실제 이체는 1건만 발생)로 검증

### 장애 격리 (Redis 분산 락 + 서킷 브레이커)
- 외부 시스템(사기 탐지 API) 장애 상황을 시뮬레이션하는 더미 서비스 구현
- Resilience4j `@CircuitBreaker`를 적용해 외부 호출 실패가 누적되면 자동으로 호출을 차단(OPEN), 일정 시간 후 자동 복구 시도(HALF_OPEN)
- 강제 장애 테스트로 `CLOSED → OPEN → HALF_OPEN → CLOSED` 상태 전이 전 과정을 로그로 검증
- Redisson 기반 분산 락(`RLock`)을 DB 락 앞단에 적용해, DB 커넥션을 점유하기 전에 앱 레벨에서 동시 요청을 조율 (커넥션 풀 고갈 방지, 다중 서버 확장 대비)

### 감사 로그
- 로그인 실패 등 주요 이벤트에 대한 감사 로그(`AuditLog`) 기록 로직 구현 (사용자, 관련 거래, IP, 상세 내용 기록)

## 트러블슈팅

| 문제 | 원인 | 해결 |
|---|---|---|
| 인증 없이 접근 시 401이 아닌 403 반환 | `SecurityConfig`에 인증 실패 처리 방식 미지정 → 스프링 기본값(403)으로 처리 | `JwtAuthenticationEntryPoint` 구현 후 등록 |
| 서버 재시작 후 기존 토큰이 전부 무효화됨 | `JwtProvider`가 매번 랜덤 비밀키 생성 | 비밀키를 환경변수(`jwt.secret`)로 고정 |
| URL 쿼리 파라미터 조작으로 타인 계좌 조회 가능 | 컨트롤러가 토큰이 아닌 `@RequestParam userId`를 그대로 신뢰 | `@AuthenticationPrincipal`로 토큰에서 추출하도록 변경 |
| 정상 요청인데도 401 반환 | `@ExceptionHandler` 파라미터 타입 불일치로 예외 처리기 등록이 꼬여 Security 예외 체계까지 영향 | 예외 타입별로 핸들러 분리 등록 |
| Idempotency-Key 헤더 없이 요청 시 400이 아닌 401 반환 | `MissingRequestHeaderException`의 `/error` 포워딩이 Security 필터에 막힘 | `GlobalExceptionHandler`에서 직접 처리 + `/error` permitAll 추가 |
| 동시성 테스트 데드락 | 스레드풀 크기보다 많은 스레드가 barrier 방식으로 서로 대기 | barrier 제거, 단순 완료 카운트 방식으로 변경 |
| 서킷 브레이커 폴백 미동작 (`No fallback method match found`) | 폴백 메서드 누락 | 폴백 메서드 추가 |
