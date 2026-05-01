# test-validation-2026-05-01.md

기준 날짜 및 시간: 2026-05-01 (Asia/Seoul)

## 1. 목적
이 문서는 2026-05-01 전체 테스트 실행 중 발견한 실패 원인, 수정 내역, 최종 검증 결과를 별도로 기록한다.

## 2. 테스트 실행 요약
실행 명령:
```powershell
$env:JAVA_HOME='C:\Users\wowjd\.jdks\ms-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test
```

최종 결과:
- 전체 50개 테스트 통과
- `BUILD SUCCESSFUL`

## 3. 발견한 문제
### 3-1. Java 17 toolchain 부재
- 기본 `java`는 11이었다.
- 프로젝트 Gradle 설정은 Java 17 toolchain을 요구한다.
- 사용자 JDK 폴더에 Microsoft OpenJDK 17을 준비한 뒤 테스트를 진행했다.

### 3-2. 테스트용 datasource 설정 부재
- `SpringBootTest`와 repository 테스트가 datasource를 찾지 못해 실패했다.
- 실패 메시지 핵심: `Failed to determine a suitable driver class`
- 운영용 비밀 설정 파일에 의존하지 않도록 테스트 전용 설정을 추가했다.

### 3-3. H2 numeric 나눗셈 정밀도 문제
- 테스트 전용 H2 DB에서 랭킹 보정 점수 SQL이 `numeric` 나눗셈을 매우 긴 `DECFLOAT`로 계산했다.
- 실패 메시지 핵심: `Value too long for column "DECFLOAT"`
- PostgreSQL / H2 모두에서 유한한 비율 계산이 되도록 SQL cast를 `double precision`으로 바꿨다.

## 4. 수정한 내역
- `Capstone/build.gradle`
  - 테스트 런타임에 H2 의존성 추가
- `Capstone/src/test/resources/application.yml`
  - 테스트 전용 H2 datasource, JPA, OAuth2 dummy client, JWT dummy secret, Pcmap 비활성화 설정 추가
- `.gitignore`
  - 테스트 전용 `Capstone/src/test/resources/application.yml`은 Git에서 추적되도록 예외 추가
- `RestaurantRankingRepositoryImpl`
  - 랭킹 보정 점수 비율 계산 cast를 `numeric`에서 `double precision`으로 변경
- `RestaurantRecommendationRepositoryImpl`
  - 식당 추천 ranking 보정 신호 비율 계산 cast를 `numeric`에서 `double precision`으로 변경
- `ListRecommendationRepositoryImpl`
  - 리스트 추천 품질 보정 신호 비율 계산 cast를 `numeric`에서 `double precision`으로 변경
- `README.md`
  - 테스트가 H2 인메모리 DB로 실행 가능하다는 현재 상태 반영
- `docs/logic/validation-rules.md`
  - 전체 테스트 실패 원인과 최종 통과 결과 기록
- `docs/logic/ranking-policy.md`
  - 보정 점수 SQL의 `double precision` 계산 기준 기록
- `docs/logic/recommendation-policy.md`
  - 식당 추천 ranking 보정 신호의 `double precision` 계산 기준 기록
- `docs/logic/list-recommendation-policy.md`
  - 리스트 추천 품질 보정 신호의 `double precision` 계산 기준 기록

## 5. 변경 내용
- 테스트는 로컬 PostgreSQL 또는 운영 비밀 yml 없이 실행 가능해졌다.
- 전체 테스트가 Java 17 환경에서 재현 가능해졌다.
- 보정 점수 SQL은 DB별 arbitrary precision 나눗셈 차이에 덜 민감해졌다.
- 추천 / 랭킹의 정책 의미는 유지하고, SQL 산술 타입만 명시했다.

## 6. 남은 이슈
- Java 17 JDK 자체는 저장소 파일이 아니라 로컬 사용자 환경에 설치된 도구다.
- H2는 테스트 실행 안정성을 위한 대체 DB이므로, 운영 PostgreSQL과 완전히 동일한 실행 계획을 보장하지는 않는다.
