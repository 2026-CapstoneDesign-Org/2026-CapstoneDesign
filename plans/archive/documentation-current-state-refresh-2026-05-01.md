# 작업명
문서 최신화를 위한 전체 구조 파악 및 SeokH-dev 이후 변경 반영

## 목적
현재 `main` 브랜치 기준 프로젝트 구조를 다시 확인하고, SeokH-dev의 마지막 병합 커밋 이후 추가된 작업물을 운영 문서에 반영한다.

## 사용자 관점 결과
문서를 읽는 사람이 현재 코드 구조, 최근 추가된 외부 식당 fallback 흐름, 시드 preview 변경, 남은 미확정 항목을 코드 기준으로 파악할 수 있다.

## 범위
- `GUIDE.md`, `DB.md`, `LOGIC.md`, `docs/db/*`, `docs/logic/*`, `docs/current-gaps.md`의 현재성 점검
- `Capstone/` 하위 컨트롤러, 서비스, DTO, 도메인, 저장소, 테스트 구조 파악
- SeokH-dev 마지막 병합 커밋 `4081de7` 이후 `HEAD`까지 변경된 작업물 반영
- 문서와 현재 코드가 다를 경우 `현재 코드 기준`, `추가 확인 필요`, `후속 수정 후보`를 분리

## 비범위
- 백엔드 기능 구현 변경
- 엔티티/서비스 구조 재설계
- 미확정 정책을 새 정책으로 확정
- 테스트 코드 수정

## 관련 문서/코드
- `GUIDE.md`
- `AGENTS.md`
- `DB.md`
- `LOGIC.md`
- `docs/db/*`
- `docs/logic/*`
- `docs/current-gaps.md`
- `Capstone/src/main/java/com/example/Capstone/controller/UserListController.java`
- `Capstone/src/main/java/com/example/Capstone/service/UserListService.java`
- `Capstone/src/main/java/com/example/Capstone/service/SearchService.java`
- `Capstone/src/main/java/com/example/Capstone/dto/request/AddExternalRestaurantRequest.java`
- `Capstone/src/test/java/com/example/Capstone/service/UserListServiceExternalFallbackTest.java`
- `Capstone/seed-data/*seed-preview.json`

## 사전 확인 사항
- SeokH-dev 기준 커밋: `4081de7` (`Merge pull request #5`, 2026-04-15)
- 이후 추가 변경: `d2ed4c4`, `558bfa6`, `b7526c1`, `7e457b3`
- 외부 식당 fallback이 현재 API 계약과 문서에 어떻게 표현되어 있는지 확인 필요
- `PcmapSearchClient` 사용처 부재 gap이 최신 코드 기준에서도 유효한지 재확인 필요
- 시드 preview 파일 변경이 DB/seed import 문서에 반영되어야 하는지 확인 필요

## 구현 단계
1. SeokH-dev 이후 변경 파일과 diff를 확인한다.
2. 관련 컨트롤러, 서비스, DTO, 테스트를 읽고 현재 코드 기준 동작을 정리한다.
3. DB/로직 세부 문서를 읽고 문서와 코드의 차이를 찾는다.
4. 확정된 현재 동작은 관련 문서에 반영한다.
5. 확정하기 어려운 항목은 `docs/current-gaps.md`에 unresolved 항목으로 남긴다.
6. 문서 변경 후 Git diff와 실행 가능한 검증 명령을 확인한다.

## 검증 방법
- 문서 변경 범위 확인: `git diff -- docs DB.md GUIDE.md LOGIC.md plans`
- 코드 변경 없음 확인: `git diff --name-only`
- 실행 가능한 테스트 명령 확인: `Capstone/gradlew` 또는 `Capstone/gradlew.bat` 존재 여부 확인
- 코드 변경이 없으므로 전체 테스트 실행은 필수로 보지 않되, 외부 fallback 문서화 근거로 관련 테스트 파일을 읽어 확인한다.

## 리스크
- 커밋 메시지 인코딩이 일부 깨져 보여 작성자/내용 판단은 해시와 diff 중심으로 확인해야 한다.
- 외부 검색 fallback의 정책 중 일부는 구현은 존재해도 운영 정책이 완전히 확정되지 않았을 수 있다.
- 시드 preview 데이터는 운영 seed source와 구분해 문서화해야 한다.

## Progress
- [x] 계획 작성 완료
- [x] 사용자 요청 확인 완료
- [x] 구조 및 변경분 분석 완료
- [x] 문서 반영 완료
- [x] 최종 검증 완료

## 결정 사항 / 변경 로그
- 2026-05-01: SeokH-dev 이후 변경 기준을 `4081de7..HEAD`로 잡고 문서 최신화 작업을 시작한다.
- 2026-05-01: 현재 코드 기준 `RestaurantCategory` 엔티티 / `restaurant_categories` 테이블 의존은 제거된 상태로 확인했다.
- 2026-05-01: `PcmapSearchClient`는 `SearchService`와 `UserListService`에서 실제 사용 중이므로 기존 사용처 부재 gap을 해소 항목으로 정리했다.
- 2026-05-01: 통합 검색의 외부 fallback은 내부 식당 결과가 0개일 때만 사용되는 것으로 정리했다.
- 2026-05-01: 외부 fallback으로 생성되는 식당의 후속 정제 / seed 편입 정책은 미확정 항목으로 남겼다.
- 2026-05-01: 대상 테스트 실행을 시도했으나 로컬 Java 17 toolchain 부재로 실행되지 못했다. 자세한 내용은 `docs/logic/validation-rules.md`에 남겼다.

## 완료 조건
- 현재 코드 기준 구조와 SeokH-dev 이후 변경분이 운영 문서에 반영되어 있다.
- 코드와 문서가 충돌하는 항목은 확정 문서가 아니라 `docs/current-gaps.md`에 남아 있다.
- 문서 변경만 발생하며 백엔드 코드 동작은 수정하지 않는다.
- 검증 결과와 남은 이슈가 최종 응답에 정리되어 있다.
