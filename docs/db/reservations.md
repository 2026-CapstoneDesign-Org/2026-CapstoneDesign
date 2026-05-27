# 예약 저장 구조

기준 날짜 및 시간: 2026-05-20 (Asia/Seoul)

## 1. 범위
이 문서는 AI 전화 예약 MVP에서 추가된 `RestaurantReservation` 저장 구조를 다룬다.

대상 파일:
- `Capstone/src/main/java/com/example/Capstone/domain/RestaurantReservation.java`
- `Capstone/src/main/java/com/example/Capstone/domain/ReservationStatus.java`
- `Capstone/src/main/java/com/example/Capstone/domain/ReservationProviderEvent.java`
- `Capstone/src/main/java/com/example/Capstone/domain/ReservationProviderEventType.java`
- `Capstone/src/main/java/com/example/Capstone/domain/ReservationProviderEventProcessingStatus.java`
- `Capstone/src/main/java/com/example/Capstone/domain/ReservationCallAttempt.java`
- `Capstone/src/main/java/com/example/Capstone/domain/ReservationCallAttemptStatus.java`
- `Capstone/src/main/java/com/example/Capstone/repository/RestaurantReservationRepository.java`
- `Capstone/src/main/java/com/example/Capstone/repository/ReservationProviderEventRepository.java`
- `Capstone/src/main/java/com/example/Capstone/repository/ReservationCallAttemptRepository.java`

## 2. 현재 코드 기준
### 테이블
- `restaurant_reservations`
- `reservation_provider_events`
- `reservation_call_attempts`

### 주요 컬럼
- `id`
- `user_id`
- `restaurant_id`
- `reservation_date_time`
- `party_size`
- `request_note`
- `status`
- `restaurant_phone_number_snapshot`
- `ai_summary`
- `result_message`
- `failure_reason`
- `provider`
- `provider_call_id`
- `provider_status`
- `attempt_count`
- `last_attempted_at`
- `confirmed_at`
- `canceled_at`
- `created_at`
- `updated_at`

### 관계
- `User` 1:N `RestaurantReservation`
- `Restaurant` 1:N `RestaurantReservation`

현재 예약 대상은 내부 DB에 저장된 visible `Restaurant`다.

### Provider event ledger
실제 provider webhook endpoint는 아직 구현하지 않았지만, provider 이벤트를 내부 표준 형태로 기록하고 중복 이벤트를 막기 위한 `ReservationProviderEvent` Entity는 준비되어 있다.

주요 컬럼:
- `id`
- `reservation_id`
- `provider`
- `provider_call_id`
- `provider_event_id`
- `idempotency_key`
- `event_type`
- `provider_status`
- `target_status`
- `failure_code`
- `failure_reason`
- `retryable`
- `occurred_at`
- `received_at`
- `processed_at`
- `processing_status`
- `duplicate_of_event_id`
- `raw_payload_hash`
- `raw_payload`
- `signature_verified`
- `ai_summary`
- `result_message`
- `created_at`
- `updated_at`

현재 코드 기준 `idempotency_key`는 unique constraint 후보로 매핑되어 있다. 실제 운영 DB migration 방식은 아직 확정되지 않았다.

### Call attempt
실제 전화 provider는 아직 없지만, Mock webhook과 내부 provider event를 기준으로 전화 시도 단위 기록을 남기는 `ReservationCallAttempt` Entity는 준비되어 있다.

주요 컬럼:
- `id`
- `reservation_id`
- `attempt_number`
- `provider`
- `provider_call_id`
- `status`
- `started_at`
- `ended_at`
- `next_retry_at`
- `failure_code`
- `failure_reason`
- `last_provider_event_id`
- `created_at`
- `updated_at`

현재 status:
- `STARTED`
- `CONNECTED`
- `RETRY_SCHEDULED`
- `RETRY_DISPATCHED`
- `COMPLETED`
- `FAILED_FINAL`

현재 코드 기준 같은 예약에서 같은 `(provider, provider_call_id)` 조합과 같은 `(reservation_id, attempt_number)` 조합은 중복되지 않도록 unique constraint 후보로 매핑되어 있다. 실제 운영 DB migration 방식은 아직 확정되지 않았다.

## 3. 현재 코드 해석
- 예약 요청자는 `users.id`로 연결한다.
- 예약 대상은 `restaurants.id`로 연결한다.
- 예약 날짜와 시간은 `reservationDateTime` 단일 값으로 저장한다.
- `partySize`는 현재 서비스와 요청 DTO 기준 `1 ~ 20` 범위다.
- `restaurantPhoneNumberSnapshot`은 예약 요청 시점의 식당 전화번호를 저장한다.
- `status`는 문자열 enum으로 저장한다.
- `provider`, `providerCallId`, `providerStatus`는 Mock provider와 추후 실제 전화 provider 연동을 위한 메타데이터다.
- `aiSummary`, `resultMessage`, `failureReason`은 Mock 또는 추후 provider 결과를 사용자에게 설명하기 위한 필드다.
- 통화 녹음, 전체 transcript, 실제 전화 로그 원문은 저장하지 않는다.

## 4. 저장 구조 관점에서 읽어야 할 점
- 현재 DB unique 제약은 없다.
- 동일 사용자 / 동일 식당 / 동일 예약 시각의 충돌 가능 예약 중복은 서비스 로직에서 막는다.
- 중복 차단 상태는 `REQUESTED`, `CALLING`, `CONFIRMED`, `NEEDS_CONFIRMATION`이다.
- `UNAVAILABLE`, `FAILED`, `CANCELED` 상태는 같은 조건의 새 요청을 막지 않는다.
- 예약 결과는 추천 / 랭킹 / 리스트 점수 입력으로 사용하지 않는다.

## 5. 운영 DB DDL 후보
현재 프로젝트에서 Flyway/Liquibase 같은 고정 migration 도구는 확인되지 않았다. 따라서 아래 SQL은 운영 DB 반영 전 검토할 후보이며, 실제 적용 경로는 별도 확정이 필요하다.

PostgreSQL 기준 후보:

```sql
CREATE TABLE IF NOT EXISTS restaurant_reservations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    restaurant_id BIGINT NOT NULL,
    reservation_date_time TIMESTAMP NOT NULL,
    party_size INTEGER NOT NULL,
    request_note VARCHAR(500),
    status VARCHAR(30) NOT NULL,
    restaurant_phone_number_snapshot VARCHAR(50) NOT NULL,
    ai_summary TEXT,
    result_message VARCHAR(1000),
    failure_reason VARCHAR(500),
    provider VARCHAR(50),
    provider_call_id VARCHAR(100),
    provider_status VARCHAR(100),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_attempted_at TIMESTAMP,
    confirmed_at TIMESTAMP,
    canceled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_restaurant_reservations_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_restaurant_reservations_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurants (id),
    CONSTRAINT ck_restaurant_reservations_party_size
        CHECK (party_size BETWEEN 1 AND 20),
    CONSTRAINT ck_restaurant_reservations_status
        CHECK (status IN (
            'REQUESTED',
            'CALLING',
            'CONFIRMED',
            'UNAVAILABLE',
            'NEEDS_CONFIRMATION',
            'FAILED',
            'CANCELED'
        ))
);
```

## 6. Index 후보
현재 서비스 로직과 조회 패턴 기준 후보:

```sql
CREATE INDEX IF NOT EXISTS idx_restaurant_reservations_user_schedule
    ON restaurant_reservations (user_id, reservation_date_time DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_restaurant_reservations_restaurant_schedule
    ON restaurant_reservations (restaurant_id, reservation_date_time, status);

CREATE INDEX IF NOT EXISTS idx_restaurant_reservations_status_created_at
    ON restaurant_reservations (status, created_at);

CREATE INDEX IF NOT EXISTS idx_restaurant_reservations_provider_call
    ON restaurant_reservations (provider, provider_call_id)
    WHERE provider_call_id IS NOT NULL;
```

동일 사용자 / 동일 식당 / 동일 예약 시각의 진행성 중복 예약을 DB 레벨에서도 막으려면 PostgreSQL partial unique index 후보를 둔다.

```sql
CREATE UNIQUE INDEX IF NOT EXISTS uq_restaurant_reservations_active_user_restaurant_time
    ON restaurant_reservations (user_id, restaurant_id, reservation_date_time)
    WHERE status IN ('REQUESTED', 'CALLING', 'CONFIRMED', 'NEEDS_CONFIRMATION');
```

현재 코드도 같은 기준을 application-level 검증으로 수행한다. 다만 동시 요청 race condition까지 막으려면 운영 DB에는 위 partial unique index 또는 이에 준하는 DB 제약이 필요하다.

## 7. 실제 provider 연동 전 추가 테이블 / 필드 후보
실제 전화 provider webhook을 받을 때는 provider raw payload를 곧바로 `restaurant_reservations`에 반영하지 않고, 내부 표준 이벤트로 변환한 뒤 처리하는 구조를 기준으로 둔다.

세부 설계 후보는 `plans/ai-call-reservation-provider-webhook-plan-2026-05-20.md`에 둔다.

### 7-1. `reservation_provider_events`
provider webhook 중복 수신, 역순 수신, 재처리 여부를 기록하기 위한 event ledger 테이블이다. 현재 코드에는 Entity/Repository가 준비되어 있으며, 운영 DB migration 적용 경로는 아직 확정되지 않았다.

주요 컬럼 후보:
- `id`
- `reservation_id`
- `provider`
- `provider_call_id`
- `provider_event_id`
- `idempotency_key`
- `event_type`
- `provider_status`
- `target_status`
- `failure_code`
- `failure_reason`
- `retryable`
- `occurred_at`
- `received_at`
- `processed_at`
- `processing_status`
- `duplicate_of_event_id`
- `raw_payload_hash`
- `raw_payload`
- `signature_verified`
- `created_at`
- `updated_at`

Index / constraint 후보:

```sql
CREATE UNIQUE INDEX IF NOT EXISTS uq_reservation_provider_events_idempotency
    ON reservation_provider_events (idempotency_key);

CREATE INDEX IF NOT EXISTS idx_reservation_provider_events_reservation_occurred
    ON reservation_provider_events (reservation_id, occurred_at, id);

CREATE INDEX IF NOT EXISTS idx_reservation_provider_events_provider_call
    ON reservation_provider_events (provider, provider_call_id);

CREATE INDEX IF NOT EXISTS idx_reservation_provider_events_processing
    ON reservation_provider_events (processing_status, received_at);
```

### 7-2. `reservation_call_attempts`
실제 provider 호출은 아직 없지만, 시도별 provider call id, 실패 사유, 다음 retry 시간을 추적하기 위한 call attempt 테이블은 현재 코드에 Entity/Repository로 준비되어 있다. retry/timeout scheduler 골격은 이 테이블에서 대상 시도를 조회하고 Mock/no-op 방식으로만 처리한다. 운영 DB migration 적용 경로는 아직 확정되지 않았다.

주요 컬럼 후보:
- `id`
- `reservation_id`
- `attempt_number`
- `provider`
- `provider_call_id`
- `started_at`
- `ended_at`
- `status`
- `failure_code`
- `failure_reason`
- `next_retry_at`
- `created_at`
- `updated_at`

Index / constraint 후보:

```sql
CREATE UNIQUE INDEX IF NOT EXISTS uq_reservation_call_attempts_provider_call
    ON reservation_call_attempts (reservation_id, provider, provider_call_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_reservation_call_attempts_attempt_number
    ON reservation_call_attempts (reservation_id, attempt_number);

CREATE INDEX IF NOT EXISTS idx_reservation_call_attempts_retry
    ON reservation_call_attempts (status, next_retry_at);

CREATE INDEX IF NOT EXISTS idx_reservation_call_attempts_timeout
    ON reservation_call_attempts (status, started_at);
```

## 8. 추가 확인 필요
- 식당 단위 전역 중복 예약을 막을지, 현재처럼 사용자 단위 중복만 막을지
- 예약자 개인정보 저장 범위와 보관 / 삭제 정책
- 전화번호 snapshot의 마스킹 / 정규화 / 보관 정책
- 실제 retry scheduler에서 `reservation_call_attempts`를 어떤 주기로 조회하고 처리할지
- 다중 인스턴스 scheduler lock / lease를 DB, Redis, provider job id 중 무엇으로 구현할지
- 확정된 예약의 실제 취소 전화를 어떤 구조로 저장할지
- 운영 DB migration 적용 방식을 Flyway/Liquibase/수동 SQL 중 무엇으로 둘지

## 9. 후속 수정 후보
- 실제 전화 provider 연동 시 별도 call attempt 또는 provider event 테이블 검토
- 운영 정책 확정 후 DB unique 또는 partial index 검토
