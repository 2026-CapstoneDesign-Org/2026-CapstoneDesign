# restaurants E2E fixture 다음 작업

## 권장

로컬 dev DB를 테스트가 직접 쓰는 구조는 위험하다.
추후 별도 작업으로 테스트 전용 profile 또는 testcontainers 기반 DB를 분리하는 것이 좋다.

## 유지 기준

- HTTP E2E 테스트가 dev DB를 쓰는 동안에는 `BeforeEach/AfterEach` cleanup을 유지한다.
- 테스트 fixture 이름은 실제 seed 데이터와 구분되도록 `e2e-*` prefix를 유지한다.
- cleanup은 항상 `list_restaurants`, `user_lists`, `restaurants`, `users` 순서처럼 FK 자식 테이블부터 처리한다.
