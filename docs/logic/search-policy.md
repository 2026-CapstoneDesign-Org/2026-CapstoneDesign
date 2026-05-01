# search-policy.md

기준 날짜 및 시간: 2026-05-01 (Asia/Seoul)

## 1. 범위
이 문서는 통합 검색 API와 내부 식당 부재 시 사용하는 외부 Pcmap fallback 검색 흐름을 다룬다.

대상 코드:
- `SearchController`
- `SearchService`
- `PcmapSearchClient`
- `PcmapSearchClientImpl`
- `SearchQueryInterpreter`
- `SearchRestaurantMatcher`
- `SearchResultMapper`
- `SearchServiceTest`

## 2. 현재 구현된 기능
- 단일 query 기반 식당 / 사용자 / 지역 검색
- `@nickname` 형식의 명시적 사용자 검색
- `@@region` 형식의 명시적 지역 검색
- 지역 + 메뉴 / 카테고리 / 태그 조합 검색
- 내부 식당 후보가 없을 때 NAVER Pcmap 외부 fallback 검색

## 3. 현재 코드 기준 동작
### 3-1. 엔드포인트
- `GET /search?query=...`

현재 보안 설정 기준:
- `/search`는 permitAll 대상이 아니다.
- 현재 코드는 인증된 사용자 기준 조회 API로 동작한다.

### 3-2. query 해석
- query는 trim 후 연속 공백을 하나로 정규화한다.
- 빈 query는 `400 BAD_REQUEST`로 실패한다.
- `@` prefix는 사용자 검색으로 해석한다.
- `@@` prefix는 지역 검색으로 해석한다.
- 일반 query는 현재 DB의 visible 식당 지역 신호를 조회해 region keyword를 먼저 감지한다.
- `맛집`, `식당`, `밥집`, `추천`은 generic browse term으로 처리한다.

### 3-3. 내부 식당 검색
내부 식당 후보는 아래 신호를 사용한다.
- 식당 이름
- 지번 주소
- 도로명 주소
- 대표 지역명
- 시 / 구 / 군 / 읍면동 지역 필드
- 지역 filter 이름 목록
- 단일 카테고리명
- 메뉴명 / 정규화 메뉴명
- 활성 태그명

내부 식당 검색은 `isDeleted = false`, `isHidden = false` 식당만 대상으로 한다.

### 3-4. 사용자 / 지역 검색
- 사용자 검색은 visible 사용자 조회 결과를 사용한다.
- 지역 검색은 식당의 지역 필드와 `regionFilterNames`를 기반으로 중복을 제거해 반환한다.
- 지역 결과의 `rankingPath`는 `/rankings/restaurants?regionName=...` 형태다.

## 4. 외부 fallback 검색
### 4-1. fallback 사용 조건
현재 코드 기준 외부 fallback은 아래 조건을 모두 만족할 때만 사용한다.
- 내부 식당 결과가 0개다.
- restaurant keyword가 존재한다.
- generic browse query가 아니다.

SeokH-dev의 마지막 병합 커밋 이후 현재 `main`에서는 fallback 기준이 `내부 결과 5개 미만`이 아니라 `내부 결과 0개`로 좁혀져 있다.

### 4-2. Pcmap 조회
- `PcmapSearchClientImpl`은 NAVER Pcmap HTML의 Apollo state를 파싱해 후보를 만든다.
- `search.pcmap.enabled=false`이면 빈 결과를 반환한다.
- 외부 요청 실패, 파싱 실패, 응답 구조 변경 시 예외를 밖으로 던지지 않고 빈 결과를 반환한다.
- `NAVER_COOKIE`가 있으면 요청 Cookie 헤더로 사용한다.

### 4-3. fallback 결과 응답
- fallback 결과의 `source`는 `EXTERNAL_FALLBACK`이다.
- fallback 결과의 `restaurantId`는 아직 DB row가 없을 수 있으므로 `null`일 수 있다.
- fallback 결과는 최대 5개까지 붙인다.
- 클라이언트가 리스트에 추가하려면 `externalPlaceId`와 원래 `searchQuery`를 사용해 `POST /lists/{id}/restaurants/external-fallback`를 호출한다.
- 외부 fallback 결과는 내부 식당의 `pcmapPlaceId` 또는 `name + address`와 중복되면 제외한다.

## 5. 리스트 추가 흐름과의 연결
- 검색 fallback은 외부 후보를 응답에 노출만 한다.
- 실제 DB 저장은 `UserListService.addExternalFallbackRestaurant()`에서 수행한다.
- 저장 시점에는 외부 후보를 다시 조회하고, 요청의 `externalPlaceId`와 일치하는 후보만 허용한다.
- 리스트 지역과 외부 후보 주소의 지역 토큰이 맞지 않으면 리스트에 추가할 수 없다.
- 자세한 리스트 추가 규칙은 `docs/logic/list-policy.md`를 본다.

## 6. 추가 확인 필요
- Pcmap HTML 구조 변경 시 fallback 장애 감지 / 운영 알림을 둘지
- 외부 fallback으로 생성된 식당의 메뉴 / 태그 / 카테고리 정제 흐름
- 외부 fallback 결과를 어느 화면에서 어떤 액션으로 노출할지에 대한 프론트 계약

## 7. 후속 수정 후보
- 외부 fallback 모니터링 로그 / metric 보강
- fallback 후보 저장 후 seed preview 편입 또는 관리자 검수 흐름 추가
