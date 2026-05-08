# 식당 영업시간 raw 컬럼 전환 진행 상황

## 완료

- `Restaurant` 엔티티에서 `openingHours`, `businessHoursSummary` 제거
- `Restaurant` 엔티티에 `businessHoursRaw` 단일 영업시간 컬럼 유지
- seed import에서 `business_hours_raw` 우선 사용
- 이전 seed 호환을 위해 `opening_hours`는 import fallback으로만 사용
- 상세 API 응답에서 public raw 문자열 노출 제거
- 상세 API 응답에 `businessHours` 구조 데이터 추가
- 상세 API 응답에 네이버형 표시용 `businessHoursDisplay` 추가
- 상세 API 응답에 조회 시점 기준 `currentBusinessStatus` 추가
- 서버에서 `영업 중`, `영업 전`, `영업 종료`, `브레이크타임`, `휴무` 계산
- Naver seed preview 출력 키를 `business_hours_raw`로 변경
- 로컬 DB 스키마에서 `opening_hours`, `business_hours_summary` 삭제
- 로컬 DB 기존 매칭 식당에 `business_hours_raw` 반영

## API 표시 기준

- 첫 줄: `businessHoursDisplay.statusLine`
- 접힌 줄: `businessHoursDisplay.summaryLine`
- 펼친 목록: `businessHoursDisplay.rows`
- 하단 안내: `businessHoursDisplay.noticeText`
- 상태 코드/계산 시각: `currentBusinessStatus`

## 검증

- `./gradlew.bat test` 통과
- `node -c src/pcmap.js` 통과
- `node -c src/seed.js` 통과
- `node -c src/combine_seed.js` 통과
- `npm run test:tags` 통과

## DB 결과

- 전체 로컬 식당 수: 622
- seed preview 식당 수: 596
- seed preview 영업시간 raw 보유: 549
- 로컬 DB 기존 식당 매칭 업데이트: 546
- 로컬 DB `business_hours_raw` 저장: 502
- 현재 상태 payload 저장: 0
