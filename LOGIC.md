# LOGIC.md

기준 날짜 및 시간: 2026-05-01 (Asia/Seoul)

## 1. 목적
이 문서는 현재 프로젝트의 동작 흐름과 정책 문서를 연결하는 로직 허브다.

## 2. 현재 로직 문서 목록
- [docs/logic/auth-flow.md](docs/logic/auth-flow.md)
- [docs/logic/follow-policy.md](docs/logic/follow-policy.md)
- [docs/logic/search-policy.md](docs/logic/search-policy.md)
- [docs/logic/list-policy.md](docs/logic/list-policy.md)
- [docs/logic/score-policy.md](docs/logic/score-policy.md)
- [docs/logic/visibility-policy.md](docs/logic/visibility-policy.md)
- [docs/logic/ranking-policy.md](docs/logic/ranking-policy.md)
- [docs/logic/recommendation-policy.md](docs/logic/recommendation-policy.md)
- [docs/logic/list-recommendation-policy.md](docs/logic/list-recommendation-policy.md)
- [docs/logic/seed-import.md](docs/logic/seed-import.md)
- [docs/logic/validation-rules.md](docs/logic/validation-rules.md)
- [docs/current-gaps.md](docs/current-gaps.md)

## 3. 어떤 문서를 먼저 보면 되는가
### 인증
`docs/logic/auth-flow.md`

### 팔로우 정책
`docs/logic/follow-policy.md`

### 통합 검색 / 외부 fallback
`docs/logic/search-policy.md`

### 리스트 생성 / 수정 / 점수 / 대표 / 공개 정책
`docs/logic/list-policy.md`

### `autoScore` 계산식
`docs/logic/score-policy.md`

### 공개/비공개 / hide / soft delete
`docs/logic/visibility-policy.md`

### 식당 랭킹
`docs/logic/ranking-policy.md`

### 식당 추천
`docs/logic/recommendation-policy.md`

### 리스트 추천
`docs/logic/list-recommendation-policy.md`

### recommendation internal model / scorer
`Capstone/src/main/java/com/example/Capstone/recommendation/scorer/*`
`Capstone/src/main/java/com/example/Capstone/recommendation/model/*`

### external client / app runner
`Capstone/src/main/java/com/example/Capstone/client/*`
`Capstone/src/main/java/com/example/Capstone/runner/*`

### search support / seed support
`Capstone/src/main/java/com/example/Capstone/service/search/support/*`
`Capstone/src/main/java/com/example/Capstone/service/seed/*`

### seed import 운영 규칙
`docs/logic/seed-import.md`

### 공통 검증과 테스트 운영
`docs/logic/validation-rules.md`

### 아직 남은 이슈
`docs/current-gaps.md`

## 4. 현재 읽기 기준
- 코드가 유일한 기준이다.
- 이미 코드와 테스트로 강제되는 규칙은 확정 정책으로 본다.
- `docs/logic/validation-rules.md`는 공통 기반 검증 규칙만 다룬다.
- 기능별 품질 검증 기록은 활성 정책 문서가 아니라 archive 문서로 분리한다.

## 5. 문서 운영 원칙
- 허브 문서에는 세부 정책을 다시 복붙하지 않는다.
- 세부 정책은 해당 기능 문서 하나에만 남긴다.
- 불확실한 항목만 `docs/current-gaps.md`에 남긴다.
