# seed-import.md

기준 날짜 및 시간: 2026-05-01 (Asia/Seoul)

## 1. 범위
이 문서는 식당 seed preview import의 현재 입력 방식과 동작 규칙을 다룬다.

대상 코드:
- `RestaurantSeedImportService`
- `SeedImportRunner` (`com.example.Capstone.runner`)
- `ImportRestaurantSeedRequest`
- `RestaurantSeedFileLoader` (`com.example.Capstone.service.seed`)

구조 메모:
- `RestaurantSeedImportService`는 import 오케스트레이션 서비스다.
- `SeedImportRunner`는 애플리케이션 기동 시 import를 연결하는 runner다.

## 2. 현재 구현된 기능
- 식당 preview JSON import
- 정규화 메뉴 preview JSON import
- 태그 preview JSON import
- 식당-태그 preview JSON import

## 3. 현재 코드 기준 동작
### 실행 조건
- `seed.import.enabled=true`일 때 애플리케이션 시작 시 import를 수행한다.
- `seed.import.exit-after-run=true`면 import 후 애플리케이션을 종료한다.

### 기본 경로
- `seed-data/restaurants-seed-preview.json`
- `seed-data/restaurant-menu-items-seed-preview.json`
- `seed-data/tags-seed-preview.json`
- `seed-data/restaurant-tags-seed-preview.json`

### import 방식
- 식당은 `pcmapPlaceId` 우선, 없으면 `name + address`로 기존 row를 찾는다.
- 식당은 create 또는 update한다.
- 태그는 `tagKey` 기준으로 create 또는 update한다.
- seed source에 없는 태그는 기존 사용 여부에 따라 비활성화하거나 삭제한다.
- 메뉴, 식당-태그는 식당별 delete 후 replace 방식으로 다시 적재한다.
- 카테고리는 별도 파일이 아니라 식당 row의 `category_name`으로 저장한다.

### 정규화 포인트
- `regionCountyName`이 있으면 city/district 대신 county 기준으로 region filter를 만든다.
- `regionFilterNames`가 seed row에 있으면 그 값을 우선 사용하고, 비어 있으면 지역 필드들로 보조 목록을 만든다.
- 음수 가격이나 비정상적으로 큰 가격은 `null` 처리한다.
- `parentTagKey`는 허용된 prefix가 아니면 `null` 처리한다.

## 4. SeokH-dev 이후 preview 데이터 반영
현재 `4081de7..HEAD` 기준으로 seed preview 데이터에는 아래 정리가 추가되어 있다.
- 일부 부가 메뉴 / 소스류가 대표 메뉴 태그로 잘못 매핑되지 않도록 `normalized_menu_name`과 `menu_tag_key`가 조정되었다.
- 그 결과 `restaurant-tags-seed-preview.json`의 `matched_menu_count`가 함께 보정되었다.
- seed import 로직 자체의 입력 파일 수는 현재 4개이며, 별도 카테고리 preview 파일은 사용하지 않는다.

## 5. 추가 확인 필요
- import를 API로 노출할지
- 운영/개발 환경별 seed import 사용 범위를 더 분리할지
- 외부 fallback으로 생성된 식당을 seed preview 정제 흐름으로 편입할지

## 6. 후속 수정 후보
- seed import 전용 운영 문서 보강
- import 결과 검증 스크립트 추가
