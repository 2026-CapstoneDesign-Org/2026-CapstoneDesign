# 2026-CapstoneDesign

기준 날짜 및 시간: 2026-05-01 (Asia/Seoul)

2026 Capstone Design 백엔드 저장소입니다.  
사용자 개인 리스트와 리스트 안의 식당 평가 데이터를 중심으로 식당 검색, 리스트 관리, 랭킹, 추천 기능을 제공하는 Spring Boot 기반 프로젝트입니다.

실제 백엔드 애플리케이션 코드는 `Capstone/` 하위에 있고, 저장소 루트에는 문서와 작업 계획 문서가 있습니다.

## 프로젝트 소개
이 프로젝트는 리뷰 텍스트 중심 구조 대신, 사용자가 직접 만든 리스트와 식당별 구조화 점수를 핵심 데이터로 사용합니다.  
이렇게 쌓인 데이터를 바탕으로 식당 랭킹과 개인화 추천을 계산할 수 있도록 설계되어 있습니다.

현재 구현 범위는 핵심 CRUD 안정화와 추천/랭킹 입력 구조 정리에 초점이 맞춰져 있습니다.

## 핵심 기능
- OAuth2 로그인과 JWT access/refresh token 발급 및 재발급
- 사용자 본인 정보 조회, 수정, soft delete
- 팔로우 / 언팔로우 / 팔로워·팔로잉 조회
- 리스트 생성, 수정, 삭제, 대표 리스트 지정, 공개/비공개 전환
- 리스트에 식당 추가, 점수 수정, 식당 제거
- 통합 검색, 내부 식당 부재 시 NAVER Pcmap 외부 fallback 검색
- 식당 검색, 식당 상세 조회
- 관리자 식당 등록, 수정, 카테고리 갱신, hide
- 식당 랭킹 API
- 식당 추천 API
- 리스트 추천 API
- 외부 검색 결과 식당을 리스트에 추가하는 fallback 등록 흐름
- seed preview JSON 기반 식당 / 메뉴 / 태그 import

## 기술 스택
- Java 17
- Spring Boot
- Spring Data JPA
- PostgreSQL
- Spring Security
- OAuth2 Login
- JWT
- springdoc-openapi
- Gradle
- Docker Compose

## 실행 방법
### 1. 필수 설정 파일 준비
아래 파일이 필요합니다.

- `Capstone/src/main/resources/application-db.yml`
- `Capstone/src/main/resources/application-key.yml`

기본 `application.yml`은 `db`, `key` 프로필을 활성화합니다.

### 2. 로컬 PostgreSQL 실행
```powershell
cd Capstone
docker compose up -d
```

### 3. 서버 실행
```powershell
.\gradlew.bat bootRun
```

### 4. Swagger 확인
- `http://localhost:8080/swagger-ui.html`

## 구조 요약
```text
.
├─ Capstone/                  # 실제 Spring Boot 백엔드 프로젝트
│  ├─ src/main/java/          # 애플리케이션 코드
│  ├─ src/test/java/          # 테스트 코드
│  └─ src/test/resources/sql/ # 추천/검증용 SQL 자산
├─ docs/
│  ├─ db/                     # DB / 엔티티 구조 문서
│  ├─ logic/                  # 로직 / 정책 문서
│  ├─ archive/                # 보고서/보관 문서
│  └─ current-gaps.md         # 미확정 / 충돌 항목
├─ plans/                     # 진행 중 작업 계획
├─ plans/archive/             # 완료된 계획 보관
├─ GUIDE.md                   # 문서 허브
├─ DB.md                      # DB 문서 허브
├─ LOGIC.md                   # 로직 문서 허브
├─ AGENTS.md                  # 작업 규칙
└─ PLANS.md                   # 계획 템플릿
```

현재 `Capstone/src/main/java/com/example/Capstone` 기준 주요 패키지:
- `controller/` : API 엔드포인트
- `service/` : `@Service` 오케스트레이션 계층
- `recommendation/scorer/` : 추천 점수 계산 전용 컴포넌트
- `recommendation/model/` : 추천 계산용 내부 모델, 외부 API DTO 아님
- `client/` : 외부 API / HTML 연동 어댑터
- `runner/` : 애플리케이션 기동 훅
- `oauth2/` : OAuth2 사용자 로딩과 성공 핸들러
- `service/search/support/` : 통합 검색 해석, 매칭, 응답 매핑 보조 컴포넌트
- `service/seed/` : seed preview 파일 로딩 보조 컴포넌트
- `service/admin/` : 관리자 명령 / hide 처리 보조 서비스
- `repository/`, `domain/`, `dto/`, `config/`, `common/`, `exception/`

## 문서 안내
- 문서 허브: [GUIDE.md](GUIDE.md)
- 작업 규칙: [AGENTS.md](AGENTS.md)
- DB 구조 허브: [DB.md](DB.md)
- 로직 허브: [LOGIC.md](LOGIC.md)
- 남은 이슈: [docs/current-gaps.md](docs/current-gaps.md)

## 참고
- 테스트는 `src/test/resources/application.yml`의 H2 인메모리 DB 설정으로 로컬 비밀 설정 파일 없이 실행할 수 있습니다.
- 문서와 구현 차이가 있으면 코드를 기준으로 문서를 갱신합니다.
