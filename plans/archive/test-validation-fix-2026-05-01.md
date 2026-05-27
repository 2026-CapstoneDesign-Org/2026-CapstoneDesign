# 작업명
전체 테스트 실행 및 실패 수정

## 목적
현재 코드와 문서 최신화 상태에서 전체 테스트를 실행하고, 실패하거나 어긋난 부분을 수정한다.

## 사용자 관점 결과
프로젝트 전체 테스트가 재현 가능하게 실행되고, 실패 원인과 수정 내역이 별도 문서로 기록된다.

## 범위
- Java 17 테스트 실행 환경 확인
- `.\gradlew.bat test` 전체 실행
- 테스트 실패 원인 분석
- 필요한 최소 코드 / 테스트 설정 / 문서 수정
- 수정 내역과 변경 내용 기록

## 비범위
- 운영 DB 설정 변경
- 인증 / 추천 / 랭킹 정책 의미 변경
- 새 기능 추가

## 관련 문서/코드
- `Capstone/build.gradle`
- `Capstone/src/test/resources/application.yml`
- `Capstone/src/main/java/com/example/Capstone/repository/*RepositoryImpl.java`
- `README.md`
- `docs/logic/validation-rules.md`
- `docs/archive/test-validation-2026-05-01.md`

## 사전 확인 사항
- 프로젝트 Gradle 설정은 Java 17 toolchain을 요구한다.
- 기존 로컬 기본 Java는 11이다.
- 운영 설정 파일은 Git에 포함하지 않는다.

## 구현 단계
1. Java 17 JDK를 준비한다.
2. 전체 테스트를 실행하고 실패 원인을 분류한다.
3. 테스트 전용 datasource 설정을 추가한다.
4. DB별 SQL 산술 타입 차이로 인한 실패를 수정한다.
5. 전체 테스트를 재실행한다.
6. 수정 내역과 검증 결과를 문서로 남긴다.

## 검증 방법
- `.\gradlew.bat test`
- `git diff --check`

## 리스크
- H2 테스트 DB는 운영 PostgreSQL과 완전히 같은 DB가 아니다.
- SQL 호환성 보완은 PostgreSQL과 H2 모두에서 지원하는 표현만 사용해야 한다.

## Progress
- [x] 계획 작성 완료
- [x] 사용자 요청 확인 완료
- [x] 구현 시작
- [x] 중간 검증 완료
- [x] 최종 검증 완료
- [x] 문서 반영 완료

## 결정 사항 / 변경 로그
- 2026-05-01: 사용자 JDK 폴더에 Microsoft OpenJDK 17을 준비해 Gradle toolchain 요구 조건을 맞췄다.
- 2026-05-01: 테스트 전용 H2 datasource 설정을 추가해 로컬 운영 DB 설정 없이 `SpringBootTest`가 실행되게 했다.
- 2026-05-01: 보정 점수 SQL의 비율 계산을 `double precision`으로 명시해 H2 `DECFLOAT` 과정밀 실패를 해결했다.
- 2026-05-01: 전체 50개 테스트 통과를 확인했다.

## 완료 조건
- 전체 테스트가 통과한다.
- 테스트 설정과 SQL 수정 범위가 최소로 유지된다.
- 수정 내역과 변경 내용이 `docs/archive/test-validation-2026-05-01.md`에 별도 기록되어 있다.
