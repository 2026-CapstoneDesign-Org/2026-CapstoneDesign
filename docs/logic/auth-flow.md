# auth-flow.md

기준 날짜 및 시간: 2026-05-20 (Asia/Seoul)

## 1. 범위
이 문서는 OAuth2 로그인, JWT 발급, 프로필 보완, refresh token 갱신 흐름을 다룬다.

대상 코드:
- `SecurityConfig`
- `CustomOAuth2UserService`
- `OAuth2SuccessHandler`
- `JwtProvider`
- `JwtFilter`
- `AuthController`
- `AuthService`

## 2. 현재 코드 기준
### 로그인 진입
- 실제 OAuth2 로그인 진입 경로: `/oauth2/authorization/{provider}`
- 지원 제공자: Google / Kakao / Naver
- `/auth/oauth/{provider}`는 현재 placeholder 성격이다

### 로그인 성공 후 처리
1. 제공자 식별
2. 사용자 조회
3. 없으면 새 사용자 생성
4. access token 발급
5. refresh token 발급
6. refresh token DB 저장 또는 갱신
7. `/auth/success?accessToken=...&needsProfile=...` 리다이렉트

신규 사용자는 첫 회원가입 시점에 `NicknameGenerator`로 중복 없는 랜덤 닉네임을 부여받는다.
프로필 이미지는 `DefaultImageProvider.DEFAULT_PROFILE_IMAGE_URL` 값을 기본값으로 사용한다.

소셜 로그인 제공자 설정만으로 생년월일 / 성별을 안정적으로 받을 수 없으므로, `User.birthYear`, `User.birthMonth`, `User.birthDay`, `User.gender`는 nullable로 둔다.
프로필 보완 값은 JWT 발급 후 별도 프로필 보완 API를 통해 저장한다.

### 프로필 보완
- 엔드포인트: `/auth/signup/profile`
- 입력: `birthYear`, `birthMonth`, `birthDay`, `gender`

### refresh token 재발급
- 엔드포인트: `/auth/refresh`
- 동작: JWT 유효성 확인 → DB 저장 token 조회 → 만료 확인 → 새 access/refresh token 발급

## 3. 추가 확인 필요
- refresh token에 role claim이 없는데 role을 어떻게 안정적으로 복원할지
- `/auth/success` 응답 계약을 프론트가 어떻게 사용하는지
- `needsProfile` 판정 기준이 생년월일 전체인지 일부인지
- Google OAuth2 실로그인 동작은 로컬 / 개발 서버 환경에서 추가 검증이 필요하다.

## 4. 후속 수정 후보
- placeholder OAuth 엔드포인트 정리
- 프론트 연동 계약 문서 보강
