# users.md

기준 날짜 및 시간: 2026-05-20 (Asia/Seoul)

## 1. 범위
이 문서는 `User` 저장 구조를 다룬다.

대상 클래스:
- `Capstone/src/main/java/com/example/Capstone/domain/User.java`

## 2. 현재 코드 기준
### 테이블
- `users`

### 주요 컬럼
- `id`
- `provider`
- `provider_user_id`
- `nickname`
- `birth_year`
- `birth_month`
- `birth_day`
- `gender`
- `profile_image_url`
- `role`
- `is_hidden`
- `is_deleted`
- `created_at`
- `updated_at`
- `deleted_at`

### 현재 코드 해석
- `nickname`은 unique다.
- `provider`, `providerUserId`는 소셜 로그인 식별자 역할을 한다.
- `birthYear`, `birthMonth`, `birthDay`, `gender`는 nullable이다.
- OAuth2 제공자 설정상 생년월일 / 성별이 가입 시점에 항상 수집되지 않으므로, 사용자는 해당 값 없이 먼저 생성될 수 있다.
- `gender`, `role`은 문자열 enum으로 저장된다.
- `isHidden`과 `isDeleted`는 다른 의미를 가진다.

## 3. 저장 구조 관점에서 읽어야 할 점
- `provider + provider_user_id`는 로그인 식별자지만 DB unique 제약은 없다.
- 사용자 조회에서는 API별로 `isDeleted`, `isHidden` 적용 범위가 다를 수 있다.
- follow 관계는 별도 문서 [follow.md](follow.md)에서 다룬다.

## 4. 추가 확인 필요
- `provider + provider_user_id`를 DB unique로 강제할지
- hidden 사용자 조회 범위를 API별로 더 엄격하게 맞출지
- 탈퇴 사용자와 refresh token / follow 정리 정책 연결 여부

## 5. 후속 수정 후보
- 로그인 식별자 unique 제약 검토
- 사용자 상태 필드 적용 범위 문서화 보강
