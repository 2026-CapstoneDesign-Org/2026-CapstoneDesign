# 패키지 구조 정리 계획

## Status
completed

## 목적
현재 `service/` 패키지에 섞여 있는 scorer, recommendation model, external client, application runner를 역할 기준으로 분리해 실제 코드 구조를 더 명확하게 정리한다.

## 사용자 관점 결과
- `service/`에는 실제 `@Service` 중심 클래스만 남는다.
- 추천 계산 전용 타입은 `recommendation/` 아래로 이동한다.
- 외부 API 연동과 기동 훅은 각각 `client/`, `runner/`로 분리된다.
- 문서는 변경된 실제 패키지 구조 기준으로 최신화된다.

## 범위
- `ListRecommendationScorer`, `RestaurantRecommendationScorer` 이동
- `ListRecommendationModels`, `RestaurantRecommendationModels` 이동
- `PcmapSearchClient`, `PcmapSearchClientImpl` 이동
- `SeedImportRunner` 이동
- 관련 import 수정
- 관련 문서 최신화
- 관련 테스트 재실행

## 비범위
- 추천 점수 로직 변경
- API 스펙 변경
- repository / controller 구조 재설계
- seed import 기능 동작 변경

## 관련 문서/코드
- `AGENTS.md`
- `GUIDE.md`
- `LOGIC.md`
- `docs/current-gaps.md`
- `docs/logic/list-recommendation-policy.md`
- `docs/logic/recommendation-policy.md`
- `Capstone/src/main/java/com/example/Capstone/service/*`
- `Capstone/src/test/java/com/example/Capstone/**/*`

## 사전 확인 사항
- 문서는 실제 코드 기준으로만 갱신한다.
- `service/`에는 가능하면 `@Service`만 남긴다.
- recommendation model은 외부 응답 DTO가 아니라 내부 계산용 타입이다.
- 모델 이동 시 package-private 접근 제어가 깨질 수 있으므로 공개 범위를 함께 정리해야 한다.

## 구현 단계
1. 현재 `service/` 하위 타입의 역할과 의존관계를 확인한다.
2. 서브에이전트 분석으로 구조 리스크와 비효율 포인트를 수집한다.
3. scorer/model/client/runner를 새 패키지로 이동하고 import를 정리한다.
4. package-private 모델 타입은 새 패키지 구조에 맞게 접근 가능한 형태로 정리한다.
5. 구조 관련 문서를 실제 코드 기준으로 갱신한다.
6. 관련 테스트와 전체 테스트를 재실행한다.

## 검증 방법
- `.\gradlew.bat test --tests com.example.Capstone.service.ListRecommendationScorerTest`
- `.\gradlew.bat test --tests com.example.Capstone.service.RestaurantRecommendationScorerTest`
- `.\gradlew.bat test --tests com.example.Capstone.service.ListRecommendationServiceTest`
- `.\gradlew.bat test --tests com.example.Capstone.service.RestaurantRecommendationServiceTest`
- `.\gradlew.bat test --tests com.example.Capstone.controller.RecommendationControllerTest`
- `.\gradlew.bat test`

## 리스크
- package 이동에 따른 import 누락
- package-private model 접근 불가
- 테스트 패키지명 불일치
- 문서가 실제 패키지 구조와 어긋날 가능성

## Progress
- [x] 계획 작성 완료
- [x] 사용자 검토 / 승인 완료
- [x] 구현 시작
- [x] 중간 검증 완료
- [x] 최종 검증 완료
- [x] 문서 반영 완료

## 결정 사항 / 변경 로그
- 2026-04-14: 코드만을 기준으로 패키지 구조를 정리하고 문서를 함께 최신화한다.
- 2026-04-14: 서브에이전트 2개와 저지 에이전트를 사용해 구조 판단 근거를 보강한다.
- 2026-04-14: `service/`에는 실제 서비스만 남기고, recommendation scorer/model, external client, app runner를 역할 패키지로 분리했다.
- 2026-04-14: 관련 추천 테스트는 통과했고, 전체 `.\gradlew.bat test`는 로컬 `postgres-dev` 컨테이너 장애(`pg_notify` 디렉터리 누락)로 끝까지 검증하지 못했다.

## 완료 조건
- `service/`에는 실제 서비스 클래스만 남는다.
- scorer/model/client/runner가 역할에 맞는 패키지로 이동한다.
- 추천 및 seed import 관련 동작이 유지된다.
- 문서가 실제 패키지 구조와 일치한다.
- 관련 테스트가 통과하고, 전체 테스트는 가능한 범위에서 재실행하거나 환경 blocker를 문서화한다.
