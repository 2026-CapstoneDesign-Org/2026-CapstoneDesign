# restaurants E2E fixture 정리 진행 상황

## 원인

`HiddenGemRecommendationE2ETest`가 `@SpringBootTest(webEnvironment = RANDOM_PORT)`와 `@ActiveProfiles({ "db", "key" })`로 실제 로컬 dev DB를 사용했다.

테스트 내부에서 아래 fixture를 저장했다.

- `e2e-hidden-gem-*`
- `e2e-lower-score-*`
- `e2e-one-count-*`

이 테스트는 HTTP 요청을 실제 서버 스레드로 보내기 때문에 단순 `@Transactional` 롤백 방식으로 처리하기 어렵다.
기존에는 테스트 종료 후 cleanup이 없어 `restaurants`, `list_restaurants`, `user_lists`, `users`에 데이터가 남았다.

## 처리

- `HiddenGemRecommendationE2ETest`에 `@BeforeEach`, `@AfterEach` cleanup 추가
- cleanup은 `JdbcTemplate`으로 자식 테이블부터 삭제
- 기존 로컬 DB에 남아 있던 E2E fixture도 테스트 실행 과정에서 삭제됨

## 검증

- `./gradlew.bat test --tests "com.example.Capstone.e2e.HiddenGemRecommendationE2ETest"` 통과
- `./gradlew.bat test` 통과
- `restaurants`의 `e2e-*` fixture: 0건
- 관련 `list_restaurants`: 0건
- 관련 `user_lists`: 0건
- 관련 `users`: 0건
- `restaurants.pcmap_place_id IS NULL`: 0건

## 현재 상태

스크린샷에 보인 `지번주소-e2e-*` 데이터는 삭제됐다.
앞으로 전체 테스트를 실행해도 같은 fixture가 남지 않는다.
