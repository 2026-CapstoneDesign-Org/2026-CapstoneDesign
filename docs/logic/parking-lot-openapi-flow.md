# 주차장 경기도 OpenAPI 연동 및 fallback 흐름

## 목표

주차장 기능은 서비스 지역 식당 상세에서 주변 주차장을 안내하고, 미서비스 지역 사용자도 좌표 기반으로 주차장 안내를 받을 수 있게 한다. Capstone은 운영 조회와 fallback만 담당하고, 주차장 seed 원본 수집은 `ParkingLot_seed` 전용 저장소에서 관리한다.

## 데이터 소스

- 경기도 OpenAPI 요청 주소: `https://openapi.gg.go.kr/ParkingPlace`
- API key 설정: `GG_PARKING_PLACE_API_KEY` 환경변수 또는 `parking-lot.gyeonggi-api.key`
- Capstone 내부에는 seed 원본 JSON을 보관하지 않는다.

API key는 코드에 직접 저장하지 않는다.

## 설정

```yaml
parking-lot:
  gyeonggi-api:
    enabled: false
    base-url: https://openapi.gg.go.kr/ParkingPlace
    key: ${GG_PARKING_PLACE_API_KEY:}
    page-size: 1000
    max-pages: 10
    cache-ttl-ms: 86400000
  seed:
    import:
      enabled: false
      source: file
      file-path: import-data/parking-lots-seed.json
      exit-after-run: false
```

`parking-lot.gyeonggi-api.enabled=false`이면 실시간 fallback과 OpenAPI seed import 모두 외부 호출을 하지 않는다.

## Seed 확보 흐름

파일 기반 import는 유지하지만 기본 경로는 `import-data`다. 이 디렉터리는 배포 산출물에 포함하지 않는 임시 입력 경로다.

경기도 OpenAPI에서 직접 import하려면 다음처럼 실행한다.

```powershell
$env:GG_PARKING_PLACE_API_KEY='<발급키>'
.\gradlew.bat bootRun --args='--parking-lot.gyeonggi-api.enabled=true --parking-lot.seed.import.enabled=true --parking-lot.seed.import.source=gg-openapi --parking-lot.seed.import.exit-after-run=true'
```

OpenAPI row는 기존 seed row 형태로 변환한 뒤 기존 upsert 로직을 사용한다.

중복 판단 기준:

- `parkingManagementNumber`
- `parkingLotName`
- `parkingLotDivision`
- `parkingLotType`
- `roadAddress`
- `lotAddress`
- `lat`
- `lng`

## 조회 흐름

| API | 동작 |
| --- | --- |
| `GET /restaurants/{restaurantId}/parking-lots` | 식당 좌표 기준 주변 주차장 조회 |
| `GET /parking-lots/nearby?lat={lat}&lng={lng}` | 사용자 좌표 기준 주변 주차장 조회 |

`GET /parking-lots/nearby`는 식당 DB가 없는 미서비스 지역 화면에서도 주차장 안내를 제공하기 위한 endpoint다.

## Fallback 정책

1. 요청 좌표와 `parkingLotDivision` 필터를 검증한다.
2. 좌표가 있는 DB 주차장을 가져와 haversine 거리로 정렬한다.
3. DB 결과가 요청 `limit` 이상이면 DB 결과만 응답한다.
4. DB 결과가 부족하고 경기도 API가 활성화되어 있으면 API 데이터를 가져온다.
5. API 결과는 DB에 저장하지 않고 응답 보강에만 사용한다.
6. DB 결과와 이름+주소가 같은 API 후보는 중복으로 보고 제외한다.
7. DB와 API 후보를 거리순으로 다시 정렬해 최대 `limit`건만 응답한다.

외부 fallback 응답은 `id=null`이다. 운영 데이터로 안정적으로 넣으려면 seed import 경로를 사용한다.

## API 매핑

| 경기도 API 필드 | 내부 필드 |
| --- | --- |
| `PARKPLC_MANAGE_NO` | `parkingManagementNumber` |
| `PARKPLC_NM` | `parkingLotName` |
| `PARKPLC_DIV_NM` | `parkingLotDivision` |
| `PARKPLC_TYPE` | `parkingLotType` |
| `LOCPLC_ROADNM_ADDR` | `roadAddress` |
| `LOCPLC_LOTNO_ADDR` | `lotAddress` |
| `PARKNG_COMPRT_PLANE_CNT` | `parkingCapacity` |
| `SUBTL_IMPLMTN_DIV_NM` | `alternateNoDivision` |
| `REFINE_WGS84_LAT` | `lat` |
| `REFINE_WGS84_LOGT` | `lng` |
| `CONTCT_NO` | `phoneNumber` |

운영 시간은 시작/종료 필드를 `시작~종료` 문자열로 합친다.

## 남은 리스크

- 주차장 생성, 수정, 삭제는 현재 인증만 요구하고 관리자 권한을 요구하지 않는다.
- 경기도 API fallback은 전체 페이지를 가져와 앱 메모리에서 거리 계산한다. 현재 약 2700건 규모는 가능하지만, 데이터가 커지면 지역 파라미터, bounding box, DB geo index가 필요하다.
- 경기도 외 지역은 이 OpenAPI로 커버되지 않는다.
