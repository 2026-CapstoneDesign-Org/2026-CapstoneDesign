# seed/fallback 다음 작업 계획

기준 브랜치: `codex/seed-fallback-audit`

## 완료

- `origin/main` 기준으로 로컬 작업 브랜치를 생성했다.
- 기존 `pr/swagger-docs`는 `origin/main`에 이미 포함된 조상 커밋임을 확인했다.
- 외부 fallback 검색 결과를 리스트에 바로 추가할 수 있는 별도 API를 추가했다.
- 외부 후보는 서버 재검색으로 검증한 뒤 저장하도록 했다.
- 지역 불일치와 중복 추가는 기존 리스트 규칙을 유지했다.
- fallback 추가 단위 테스트를 추가했다.
- `npm run seed:combine` 실행으로 seed combine 흐름을 확인했다.

## 다음 작업

- 외부 fallback으로 저장된 식당의 메뉴 보강 방식 결정
- pcmap place detail 기반 단건 enrichment API 또는 배치 작업 분리
- seed import 후 DB count 검증 스크립트 추가
- 태그 후보 리포트에서 `APPROVAL_READY` 후보를 사람이 승인하는 절차 문서화
- 네이버 지역 검색 API를 도입할 경우 broad category를 수집 query 확장용으로만 사용하는 규칙 반영

## 검증 명령

```powershell
cd "C:\Users\gkswh\OneDrive\바탕 화면\26-1\캡스톤 디자인\Capstone_Root\Capstone\Capstone"
.\gradlew.bat test --tests com.example.Capstone.service.UserListServiceExternalFallbackTest --tests com.example.Capstone.service.SearchServiceTest
```

```powershell
cd "C:\Users\gkswh\OneDrive\바탕 화면\26-1\캡스톤 디자인\Capstone_Root\Naver_pcmap_api\Naver_seed"
npm run seed:combine
npm run seed:export-to-capstone
npm run test:tags
```
