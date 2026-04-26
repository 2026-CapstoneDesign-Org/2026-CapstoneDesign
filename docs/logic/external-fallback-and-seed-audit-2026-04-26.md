# 외부 fallback 및 seed 흐름 점검 기록

기준 시각: 2026-04-26

## 1. git 기준 브랜치

- 원격 저장소: `https://github.com/2026-CapstoneDesign-Org/2026-CapstoneDesign`
- 원격 기본 브랜치: `origin/main`
- 작업 시작 시 로컬 브랜치: `pr/swagger-docs`
- 확인 결과: `pr/swagger-docs`는 `origin/main`의 조상이다.
- PR 개발 기준: 오래된 로컬 `main`이 아니라 최신 `origin/main`에서 작업 브랜치를 새로 따야 한다.
- 이번 로컬 작업 브랜치: `codex/seed-fallback-audit`
- push 또는 PR 생성은 수행하지 않았다.

## 2. 외부 fallback

현재 일반 검색은 식당 검색 의도가 있고 내부 DB 결과가 0개일 때 `PcmapSearchClient`를 호출해 `source = EXTERNAL_FALLBACK` 결과를 합친다.
기존 한계는 외부 결과가 `restaurantId = null`인 응답으로만 내려가서 사용자가 선택 즉시 리스트에 추가할 수 없다는 점이었다.

이번 변경으로 `/lists/{id}/restaurants/external-fallback` 엔드포인트를 추가했다.

동작 기준:

- 클라이언트는 기존 `/search` 응답의 `externalPlaceId`와 원 검색어를 전달한다.
- 서버는 전달값을 그대로 믿지 않고 `PcmapSearchClient.searchRestaurants(searchQuery, 20)`를 다시 호출한다.
- 재검색 결과 안에서 `externalPlaceId`가 일치하는 후보만 사용한다.
- 후보 주소가 리스트 지역명을 포함하지 않으면 추가하지 않는다.
- 이미 `pcmapPlaceId`가 저장된 식당이면 기존 식당을 사용한다.
- 없으면 외부 후보를 내부 `restaurants` row로 저장한 뒤 리스트에 추가한다.
- 기존 중복 식당 방지와 리스트 지역 exact match 규칙은 유지한다.
- 내부 DB에 하나라도 매칭 식당이 있으면 검색 단계에서 외부 fallback을 호출하지 않는다.

## 3. 태그 파싱 검증

현재 태그는 메뉴 기반 고정 룰(`MENU_TAG_RULES`)로만 자동 반영된다.
미승인 후보는 `tag-candidate-report.json`으로만 남고 자동 태그 생성은 꺼져 있다.

확인된 리스크:

- 메뉴명이 아닌 예약, 주문, 코스, 안내 문구가 메뉴처럼 들어오면 후보로 잡힐 수 있다.
- 메뉴명에 형용사/부사/세트명/수식어가 섞이면 기존 alias 포함 매칭으로 상위 메뉴 태그가 붙을 수 있다.
- 이 방식은 추천 태그에는 안전한 편이지만, 신규 태그 자동 생성에는 부적합하다.

해결 기준:

- 자동 태그 생성은 계속 금지한다.
- `APPROVAL_READY` 후보도 바로 DB에 넣지 않고 `MENU_TAG_RULES`에 수동 반영한다.
- 메뉴 정규화 단계에서 block keyword, raw type, 업장명 동일 여부, 숫자-only 검증을 유지한다.
- 신규 태그는 최소 메뉴 등장 수와 식당 수 기준을 통과한 후보만 검토한다.

## 4. seed 수집/insert 표준화

현재 표준 흐름:

1. `Naver_seed`에서 지역/키워드 기준 수집
2. `npm run seed:combine`으로 preview JSON 생성
3. `npm run seed:export-to-capstone`으로 결과 파일을 `Capstone/Capstone/seed-data`로 복사
4. Spring `seed.import.enabled=true`로 import 실행

한 번에 준비하려면 `npm run seed:prepare-import`를 사용한다.

이번 실행 확인:

- 명령: `npm run seed:combine`
- 결과: 식당 616개, 메뉴 10,585개, 태그 37개, 식당-태그 1,071개
- 메뉴가 매핑된 식당: 584개
- 중복: 0개

개선 방향:

- 수집 결과와 insert 대상 파일을 항상 같은 4개 preview 파일 기준으로 고정한다.
- 검증 리포트(`combined-seed-summary.json`, `tag-validation-report.json`, `tag-candidate-report.json`)는 import 대상에서 제외하고 사람이 확인한다.
- 배포 전에는 import 결과 count와 preview count를 비교하는 검증 명령을 추가하는 것이 좋다.

## 5. 네이버 지역 검색 API 카테고리 기준

네이버 지역 검색 API의 한식/일식/중식/양식 같은 넓은 카테고리는 최종 `category_name`으로 그대로 쓰기에는 거칠다.

권장 기준:

- 지역 검색 API의 broad category는 후보 수집용 query 확장에만 사용한다.
- 각 상호명별로 다시 pcmap/detail 기반 데이터를 확인해 더 구체적인 카테고리와 메뉴를 보강한다.
- 최종 DB의 `category_name`은 pcmap/detail에서 얻은 값 또는 기존 seed 정규화 결과를 우선한다.
- broad category만 있는 후보는 검색 노출용 보조 정보로만 쓰고 추천/태그 계산의 강한 근거로 쓰지 않는다.

## 6. 남은 리스크

- 외부 fallback 저장은 메뉴/태그 없이 식당 기본 정보만 만든다.
- 외부 후보의 region 세부 필드는 현재 리스트 지역명 기준으로만 저장한다.
- pcmap HTML 구조가 바뀌면 fallback 재검색이 빈 결과가 될 수 있다.
- 전체 테스트는 DB 연결 컨텍스트 문제로 일부 repository 테스트가 실패한다.
