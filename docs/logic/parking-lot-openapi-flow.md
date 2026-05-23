# 주차장 OpenAPI 연동 흐름

## 목표

Capstone은 정적 주차장 DB를 우선 사용하고, 외부 API는 두 가지 목적으로만 사용한다.

- 경기도 `ParkingPlace`: DB 결과가 부족할 때 정적 주차장 후보 fallback
- 서울시 `citydata`: 사용자 조회 시점의 실시간 주차 가능 대수 보강

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
  seoul-citydata:
    enabled: false
    base-url: http://openapi.seoul.go.kr:8088
    key: ${SEOUL_CITYDATA_API_KEY:}
    cache-ttl-ms: 30000
```

API key는 코드에 저장하지 않는다. 로컬에서는 환경변수 또는 커밋하지 않는 `application-key.yml`로 주입한다.

## 경기도 주차장 fallback

요청 좌표 기준 DB 주차장 수가 limit보다 적고 `parking-lot.gyeonggi-api.enabled=true`이면 경기도 OpenAPI를 호출한다.

경기도 API 결과는 DB에 저장하지 않고 응답 보강에만 사용한다. 장기 운영 데이터로 반영하려면 `ParkingLot_seed` 수집기 또는 seed import 흐름을 사용한다.

## 서울시 실시간 주차 현황

요청 좌표가 지원 중인 서울 주요장소 권역에 해당하고 `parking-lot.seoul-citydata.enabled=true`이면 서울시 `citydata` API를 호출한다.

요청 형식:

```http
GET http://openapi.seoul.go.kr:8088/{key}/json/citydata/1/5/{AREA_NM}
```

응답 처리:

- `CITYDATA.PRK_STTS[]`만 사용한다.
- `CUR_PRK_YN=Y`인 행만 실시간 제공 대상으로 본다.
- `CUR_PRK_CNT`를 `currentParkingCount`로 내려준다.
- `CUR_PRK_TIME`을 `currentParkingTime`으로 내려준다.
- `PRK_CD`를 `realtimeParkingCode`로 내려준다.

서울시 API는 주요 장소 단위 API다. 현재는 서비스에 등록한 주요 권역 중심 좌표 반경으로 호출 여부를 판정한다. 121개 전체 권역 커버리지가 필요하면 `SeoulCityDataParkingClient`의 권역 목록을 확장한다.

## 실패 정책

외부 API 호출 실패, 인증키 누락, 권역 미매칭은 주차장 조회 실패로 처리하지 않는다. 기존 DB 결과 또는 경기도 fallback 결과만 반환한다.
