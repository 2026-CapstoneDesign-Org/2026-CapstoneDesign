# 검색 정책

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
- 검색 화면의 맛집 / 유저 / 지역 탭 동시 응답
- `@nickname` 형식의 명시적 사용자 검색
- `@@region` 형식의 명시적 지역 검색
- 지역 + 메뉴 / 카테고리 / 태그 조합 검색
- 내부 식당 후보가 없을 때 NAVER Pcmap 외부 fallback 검색

## 3. 현재 코드 기준 동작
### 3-1. 엔드포인트
- `GET /search?query=...`
- 응답은 `restaurants`, `users`, `regions`, `primaryType`을 포함한다.

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
- 상위 카테고리명
- 메뉴명 / 정규화 메뉴명
- 활성 태그명

내부 식당 검색은 `isDeleted = false`, `isHidden = false` 식당만 대상으로 한다.

### 3-4. 사용자 / 지역 검색
- 사용자 검색은 visible 사용자 조회 결과를 사용한다.
- 일반 닉네임 검색에서 사용자 결과가 있으면 외부 식당 fallback을 섞지 않는다.
- 지역 검색은 식당의 지역 필드와 `regionFilterNames`를 기반으로 중복을 제거해 반환한다.
- 지역 결과의 `rankingPath`는 `/rankings/restaurants?regionName=...` 형태다.
- 지역 탭은 지역 자체 상세가 아니라 메인 랭킹 진입점이다.

### 3-5. 현재 위치 기준 지역 랭킹
- 현재 `GET /search`는 위치 정보를 받지 않는다.
- 검색어가 지역이 아닐 때 지역 탭을 현재 위치 기반 랭킹으로 채우지 않는다.
- 현재 위치 기반 랭킹이 필요하면 클라이언트가 지역명을 넘기거나, 좌표 기반 지역 resolver를 별도 기능으로 추가한다.

## 4. 외부 fallback 검색
### 4-1. fallback 사용 조건
현재 코드 기준 외부 fallback은 사용자/지역 탐색 의도가 아니라 식당 검색 의도가 있을 때만 검토한다.

fallback 판단 사유는 아래 값으로 분류한다.
- `NO_INTERNAL_RESULTS`: 내부 식당 결과가 없다.
- `LOW_INTERNAL_RESULT_COUNT`: 지역 + 메뉴/태그 검색에서 내부 결과가 5개 미만이고 상호명 매칭이 없다.
- `WEAK_INTERNAL_MATCH`: 내부 결과는 있으나 상호/카테고리/메뉴/태그/편의/멀티 토큰 매칭이 없다.

아래 경우에는 fallback을 호출하지 않는다.
- `@nickname` 명시 사용자 검색
- 지역 단독 또는 `맛집`, `식당`, `밥집`, `추천`, `근처`, `주변` 중심의 generic browse 검색
- 내부 결과가 충분하거나 상호명 매칭이 있는 경우

### 4-2. Pcmap 조회
- `PcmapSearchClientImpl`은 NAVER Pcmap HTML의 Apollo state를 파싱해 후보를 만든다.
- `search.pcmap.enabled=false`이면 빈 결과를 반환한다.
- 외부 요청 실패, 파싱 실패, 응답 구조 변경 시 예외를 밖으로 던지지 않고 빈 결과를 반환한다.
- `NAVER_COOKIE`가 있으면 요청 Cookie 헤더로 사용한다.

### 4-3. fallback 결과 응답
- fallback 경로를 검토하면 `interpretation.fallbackAttempted=true`로 응답한다.
- fallback으로 실제 보강 결과가 생기면 `interpretation.fallbackUsed=true`로 응답한다.
- `interpretation.fallbackReason`은 fallback 판단 사유를 나타낸다.
- `interpretation.fallbackResultCount`는 fallback 경로로 추가된 결과 수다.
- fallback 후보의 `pcmapPlaceId`가 기존 내부 식당과 일치하면 `source=INTERNAL`, 내부 `restaurantId` 포함 형태로 응답한다.
- 내부 DB에 없는 fallback 결과의 `source`는 `EXTERNAL_FALLBACK`이고 `restaurantId=null`일 수 있다.
- fallback 결과는 최대 5개까지 붙인다.
- 클라이언트가 리스트에 추가하려면 `externalPlaceId`와 원래 `searchQuery`를 사용해 `POST /lists/{id}/restaurants/external-fallback`를 호출한다.
- 외부 fallback 결과는 내부 식당의 `pcmapPlaceId` 또는 `name + address`와 중복되면 제외한다.
- 명백한 비식당 카테고리 fallback 후보는 검색 응답에서 제외한다.

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

## 7. 제외

- 검색과 리스트 추가 검색을 통합하지 않는다.
- 지역 탭을 검색어와 무관한 기본 추천 영역으로 사용하지 않는다.
- 유저 닉네임 검색에 외부 식당 fallback을 우선 노출하지 않는다.

## 8. 후속 수정 후보
- 외부 fallback 모니터링 로그 / metric 보강
- fallback 후보 저장 후 seed preview 편입 또는 관리자 검수 흐름 추가
