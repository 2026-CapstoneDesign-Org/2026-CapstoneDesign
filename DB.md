# DB.md

기준 날짜 및 시간: 2026-05-01 (Asia/Seoul)

## 1. 목적
이 문서는 현재 프로젝트의 저장 구조 문서 허브다.  
실제 엔티티 매핑과 테이블 관계 문서를 연결한다.

## 2. 현재 문서 트리
- [docs/db/users.md](docs/db/users.md)
- [docs/db/follow.md](docs/db/follow.md)
- [docs/db/lists.md](docs/db/lists.md)
- [docs/db/restaurants.md](docs/db/restaurants.md)
- [docs/db/auth.md](docs/db/auth.md)
- [docs/current-gaps.md](docs/current-gaps.md)

## 3. 현재 코드 기준 엔티티 범위
실제 도메인 클래스는 `Capstone/src/main/java/com/example/Capstone/domain` 하위에 있다.

- `User`
- `UserFollow`
- `UserList`
- `ListRestaurant`
- `Restaurant`
- `RestaurantMenuItem`
- `Tag`
- `RestaurantTag`
- `RefreshToken`

## 4. 핵심 관계
- `User` 1:N `UserList`
- `UserList` 1:N `ListRestaurant`
- `Restaurant` 1:N `ListRestaurant`
- `Restaurant` 1:N `RestaurantMenuItem`
- `Restaurant` 1:N `RestaurantTag`
- `Tag` 1:N `RestaurantTag`
- `User` N:M `User` via `UserFollow`

현재 코드 기준 식당 카테고리는 별도 `RestaurantCategory` 엔티티가 아니라 `Restaurant.categoryName` 단일 필드로 저장한다.

## 5. 어떻게 읽으면 되는가
### 사용자 / 팔로우 구조
`docs/db/users.md`

### 팔로우 저장 구조 상세
`docs/db/follow.md`

### 리스트 / 점수 / 추천 입력 구조
`docs/db/lists.md`

### 식당 / 메뉴 / 태그 구조
`docs/db/restaurants.md`

### refresh token 저장 구조
`docs/db/auth.md`

## 6. 읽을 때 주의할 점
- 엔티티 필드 초기값은 서비스 / 생성자 기본값일 수 있으며, DB default 제약과는 다를 수 있다.
- 일부 규칙은 DB 제약이 아니라 서비스 로직으로만 보장된다.
- 공개/비공개 해석은 저장 구조 문서보다 로직 문서에서 더 정확하게 다룬다.
- 저장 구조와 현재 미정 정책의 경계는 `docs/current-gaps.md`에서 확인한다.
