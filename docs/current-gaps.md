# current-gaps.md

기준 날짜 및 시간: 2026-05-01 (Asia/Seoul)

## 1. 목적
이 문서는 아직 확정되지 않았거나, 코드와 문서가 어긋나는 정책 항목만 남긴다.

확정된 구조와 정책은 `DB.md`, `LOGIC.md`, `docs/db/*`, `docs/logic/*`에 기록한다.

## 2. 미확정 정책
### 2-1. 리스트 상세 조회 공개 범위
- `GET /lists/{id}`는 현재 `isPublic` 또는 owner 여부를 검증하지 않는다.
- 외부 노출 정책을 API 계약으로 더 명확히 정리할 필요가 있다.

### 2-2. 점수 수정 API path variable 의미
- 경로는 `/lists/{id}/restaurants/{restaurantId}` 형태지만 실제 수정 대상 조회는 `list_restaurants.id` 기준이다.
- 외부 계약과 구현 의미가 다르다.

### 2-3. refresh token 재발급 시 role 복원 경로
- refresh token 자체에는 role claim이 없다.
- `AuthService.refresh()`는 `jwtProvider.getRole(refreshToken)`에 의존한다.

### 2-4. 대표 리스트와 공개 상태 관계
- 대표 리스트를 비공개로 바꾸지 못하게 막는 로직은 있다.
- 대표 리스트 삭제 후 후속 승격, 사용자당 대표 1개 보장의 운영 정책은 더 명확히 정리할 여지가 있다.

### 2-5. cold start 추천
- 식당 추천과 리스트 추천 모두 사용자 상호작용이 없으면 빈 결과를 반환한다.
- 기본 추천 정책은 아직 확정되지 않았다.

### 2-6. follow와 hidden 사용자 관계
- follow 생성은 `isDeleted = false`만 확인한다.
- hidden 사용자를 follow 대상 / 응답에서 어떻게 다룰지는 정리가 필요하다.

### 2-7. placeholder OAuth 엔드포인트
- 실제 로그인 진입점은 `/oauth2/authorization/{provider}`다.
- `/auth/oauth/{provider}`는 현재 `200 OK` placeholder 성격이다.

### 2-8. 외부 fallback으로 생성된 식당의 후속 정제 정책
- `POST /lists/{id}/restaurants/external-fallback`는 Pcmap 후보가 기존 DB에 없으면 `Restaurant` row를 생성한다.
- 현재 생성 시점에는 메뉴 / 태그 상세 데이터가 함께 생성되지 않는다.
- 이 식당을 seed preview, 관리자 검수, 별도 enrichment 흐름 중 어디로 편입할지 정책 결정이 필요하다.

### 2-9. Pcmap HTML fallback 운영 안정성
- `PcmapSearchClientImpl`은 NAVER Pcmap HTML의 Apollo state 구조를 파싱한다.
- 외부 HTML 구조 변경이나 요청 제한이 발생하면 현재 코드는 빈 결과를 반환한다.
- 장애 감지, 알림, 대체 provider, retry 여부는 아직 운영 정책으로 확정되지 않았다.

## 3. 현재 gap에서 제외한 항목
- 리스트 최소 5개 규칙
- 동일 리스트 내 동일 식당 중복 금지
- 리스트와 식당 지역 exact match
- 식당 추천 API 존재
- 리스트 추천 API 존재
- recommendation scorer / model 패키지 분리
- seed import runner 패키지 분리
- `PcmapSearchClient` 사용처 부재
- 검색 fallback 기준: 현재 코드는 내부 식당 결과가 0개일 때만 외부 fallback을 사용한다
- 별도 `RestaurantCategory` 엔티티 / `restaurant_categories` 테이블 의존: 현재 코드 기준 제거됨
