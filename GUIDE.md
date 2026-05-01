# GUIDE.md

기준 날짜 및 시간: 2026-05-01 (Asia/Seoul)

## 1. 목적
이 문서는 저장소를 처음 보는 사람이 현재 구조, 문서 역할, 읽기 순서를 빠르게 파악하도록 돕는 문서 허브다.

## 2. 저장소 해석
- 저장소 루트
  - 공개용 소개 문서, 내부 가이드, 작업 계획 문서가 있다.
- 실제 백엔드 프로젝트
  - `Capstone/`
- 현재 백엔드 핵심 성격
  - Spring Boot 기반 API 서버
  - 리스트와 리스트-식당 평가 구조 중심
  - 추천/랭킹 입력 데이터를 점진적으로 정리 중인 상태

## 2-1. 현재 코드 패키지 구조
- `controller/`
  - HTTP 엔드포인트
- `service/`
  - `@Service` 오케스트레이션 계층
- `recommendation/scorer/`
  - 추천 점수 계산 전용 컴포넌트
- `recommendation/model/`
  - 추천 계산용 내부 모델
  - 외부 응답 DTO와 구분된다
- `client/`
  - 외부 API / HTML 연동 어댑터
- `runner/`
  - `ApplicationRunner` 같은 기동 훅
- `oauth2/`
  - OAuth2 사용자 로딩과 로그인 성공 후 JWT 발급 연결
- `service/search/support/`
  - 통합 검색 쿼리 해석, 식당 매칭, 검색 응답 매핑 보조 컴포넌트
- `service/seed/`
  - seed preview 파일 경로 해석과 로딩 보조 컴포넌트
- `service/admin/`
  - 관리자 식당 명령과 hide 처리 보조 서비스
- `repository/`, `domain/`, `dto/`, `config/`, `common/`, `exception/`

## 3. 문서 구조
### 루트 허브 문서
- `README.md`
- `GUIDE.md`
- `AGENTS.md`
- `DB.md`
- `LOGIC.md`
- `PLANS.md`

### 세부 문서
- DB 구조: `docs/db/*`
- 로직 / 정책: `docs/logic/*`
- 미확정 / 충돌 항목: `docs/current-gaps.md`
- 보관용 문서: `docs/archive/*`
- 작업 계획: `plans/*`
- 완료된 계획 보관: `plans/archive/*`

## 4. 추천 읽기 순서
### 프로젝트 전체를 처음 볼 때
1. `README.md`
2. `GUIDE.md`
3. `DB.md`
4. `LOGIC.md`
5. `docs/current-gaps.md`

### DB 구조와 엔티티를 볼 때
1. `DB.md`
2. `docs/db/users.md`
3. `docs/db/follow.md`
4. `docs/db/lists.md`
5. `docs/db/restaurants.md`
6. `docs/db/auth.md`

### 인증 / 팔로우 / 시드 적재 흐름을 볼 때
1. `docs/logic/auth-flow.md`
2. `docs/logic/follow-policy.md`
3. `docs/logic/seed-import.md`
4. `docs/current-gaps.md`

### 통합 검색 / 외부 fallback 흐름을 볼 때
1. `docs/logic/search-policy.md`
2. `docs/logic/list-policy.md`
3. `docs/logic/seed-import.md`
4. `docs/current-gaps.md`

### 리스트 / 점수 / 공통 검증 규칙을 볼 때
1. `docs/logic/list-policy.md`
2. `docs/logic/score-policy.md`
3. `docs/logic/validation-rules.md`
4. `docs/current-gaps.md`

### 랭킹 / 추천 구조를 볼 때
1. `docs/logic/ranking-policy.md`
2. `docs/logic/recommendation-policy.md`
3. `docs/logic/list-recommendation-policy.md`
4. `docs/current-gaps.md`

### 보고서용 설계 설명이나 과거 검토 기록이 필요할 때
1. `docs/archive/design-decisions.md`
2. 필요 시 `docs/archive/*`

## 5. 운영 기준
- `README.md`는 공개 저장소 소개와 실행 방법 중심으로 유지한다.
- `GUIDE.md`는 허브 역할만 맡고 세부 정책은 넣지 않는다.
- `DB.md`와 `LOGIC.md`는 각각 DB 허브, 로직 허브 역할만 맡는다.
- 세부 근거와 실제 정책은 `docs/db/*`, `docs/logic/*`에 둔다.
- 미확정 또는 충돌 항목만 `docs/current-gaps.md`에 남긴다.
- 완료된 계획 문서는 `plans/archive/*`로 옮겨 active plan과 분리한다.

## 6. 현재 읽을 때 특히 주의할 점
- 코드는 유일한 기준이다.
- 이미 코드와 테스트로 강제된 규칙은 확정 정책으로 읽는다.
- 추천/랭킹 관련 정책 문서를 볼 때는 `docs/current-gaps.md`를 같이 확인한다.
- 보관 문서는 과거 결정과 보고서 용도이며, 현재 운영 기준은 활성 문서를 우선한다.
