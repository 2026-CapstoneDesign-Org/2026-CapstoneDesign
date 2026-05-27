# 검색 흐름 점검 진행 상황

## 확인

- `GET /search?query=...`는 맛집, 유저, 지역 3개 배열을 동시에 반환한다.
- `primaryType`은 우선 노출 탭을 알려준다.
- 음식/카테고리/상호는 맛집 탭으로 처리된다.
- 닉네임은 유저 탭으로 처리된다.
- 지역명은 지역 탭과 랭킹 진입점으로 처리된다.
- 검색어가 지역이 아니면 지역 탭은 현재 위치 기반으로 자동 채워지지 않는다.

## 수정

- 일반 닉네임 검색에서 유저 결과가 있을 때 외부 식당 fallback을 섞지 않도록 수정했다.
- 예: `tester` 검색 시 유저가 있으면 네이버 PC Map 식당 fallback을 호출하지 않는다.

## 검증

- `SearchServiceTest`에 일반 닉네임 검색 fallback 오염 방지 테스트 추가
- `./gradlew.bat test --tests "com.example.Capstone.service.SearchServiceTest"` 통과

## 현재 DB 기준 확인

- `한식` 카테고리 검색 가능 식당: 159건
- `와이앤웍` 상호 검색 가능 식당: 1건
- `용인` 지역 신호 매칭 식당: 616건
