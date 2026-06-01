# mock-data-generation-policy.md

기준 날짜 및 시간: 2026-05-25 (Asia/Seoul)

## 1. 목적
이 문서는 식당 랭킹과 추천 검증을 위한 mock 데이터 생성 기준을 고정한다.

핵심 목표는 다음 3가지를 분리하는 것이다.
- mock 데이터 고득점 식당 선정
- mock 데이터 생성 기준 설립
- mock 데이터 생성

이 문서는 운영 맛집 판정 기준이 아니다. 개발/검증용 mock 데이터에서 랭킹, 추천, 숨은 맛집, 리스트 추천 신호가 의도대로 드러나도록 만드는 기준이다.

## 2. 생성 원칙
mock 데이터는 랜덤 대량 삽입이 아니라 정답지 기반 시나리오 데이터로 만든다.

생성 순서는 아래 방향을 따른다.

```text
식당 잠재 품질 정답지
-> 사용자 페르소나
-> 리스트 테마
-> 리스트-식당 점수
-> API 기대 결과 검증
```

즉, 먼저 "이 식당은 실제로 어떤 특성을 가진 식당인가"를 정하고, 그 다음 "어떤 사용자가 어떤 이유로 이 식당을 리스트에 담고 몇 점을 줄 것인가"를 생성한다.

중요한 구분:
- 식당, 주소, 좌표, 카테고리, 지역은 실제 seed 기반 데이터를 사용한다.
- 사용자, 리스트, 점수는 합성하되 페르소나와 시나리오 기준을 따른다.
- 상위권 식당만 많이 넣지 않고 인기 맛집, 숨은 맛집, 무난한 식당, 낮은 품질 식당, 취향형 식당을 함께 만든다.
- 고득점 식당은 결과 검증 대상 중 하나일 뿐이며 전체 mock 데이터의 목표가 아니다.
- mock 생성과 시연 검증의 중심 지역은 전국구가 아니라 `용인시 처인구 역북동`, 즉 명지대학교 자연캠퍼스 근방이다.
- 전국 단위 식당 후보 수집은 메인 지역 밖의 소량 비교군과 fallback 데이터를 확보하기 위한 보조 과정이다.

### 2-1. generator 내부 정답지와 DB 저장값 분리
아래 값들은 generator 내부 정답지 또는 sidecar 파일에 둘 수 있지만, 현재 DB 컬럼으로 추가하지 않는다.

식당 정답지 예:
- `qualityTier`: `elite`, `good`, `normal`, `weak`
- `exposureTier`: `popular`, `normal`, `hidden`
- `tasteQuality`
- `valueQuality`
- `moodQuality`
- `trueQuality`
- `scenarioRole`: `ranking_top`, `hidden_gem`, `personalized_candidate`, `list_overlap_anchor`, `negative_control`

사용자 정답지 예:
- `personaType`
- `homeRegion`
- `preferredCategories`
- `aspectPreference`: 맛/가성비/분위기 선호 비중
- `ratingStrictness`: 후한/보통/박한 채점 성향
- `discoveryPreference`: 유명 맛집 선호/숨은 맛집 선호

검증 정답 파일 예:
- `mock_truth/restaurants.json`
- `mock_truth/users.json`
- `mock_truth/lists.json`
- `mock_truth/expected_outputs.json`

DB에는 현재 도메인 테이블에 맞춰 mock 데이터를 넣는다. 식당 품질 정답지는 DB 컬럼으로 추가하지 않고 sidecar 파일로 관리한다.

### 2-2. mock 데이터 대상 테이블
mock 데이터는 추천/랭킹만이 아니라 서비스 시연 흐름이 자연스럽게 보이도록 아래 테이블을 함께 채운다.

| 테이블 | 생성 목적 | 생성 기준 |
| --- | --- | --- |
| `restaurants` | 실제 seed 기반 식당 메타데이터 | Pcmap 검증 완료 후 중복 제거된 식당만 적재 |
| `users` | 사용자 페르소나 기반 계정 | 최소 500명 |
| `user_follows` | 유저 간 관계와 피드/신뢰도 맥락 | 페르소나/지역 유사도 기반 일부 연결 |
| `user_lists` | 유저별 식당 리스트 | 공개 70%, 비공개 30% |
| `list_restaurants` | 리스트 내 식당 평가 | 점수 생성 공식에 따라 생성 |
| `list_likes` | 공개 리스트 반응 | 인기 리스트와 취향 일치 리스트에 분산 |
| `reliability_scores` | 사용자 신뢰도 | 평가 일관성, 신고/리뷰 반응 기반 합성 |
| `reviews` | 식당 리뷰 텍스트 | 일부 평가 row에만 생성 |
| `review_votes` | 리뷰별 도움됨/비추천 반응 | 신뢰도 높은 리뷰에 더 많이 분포 |
| `reports` | 신고 데이터 | 소량의 이상 사용자/리뷰에만 생성 |

핵심 추천/랭킹 입력은 `user_lists`, `list_restaurants`, `restaurants`, `users`이지만, 시연 완성도를 위해 좋아요, 팔로우, 리뷰, 투표, 신고, 신뢰도 데이터도 함께 구성한다.

### 2-3. 사용자/콘텐츠 mock 데이터의 실제감 기준
DB에 적재되는 mock 데이터는 개발자가 봐도 생성기 흔적이 드러나지 않게 만든다. 내부 정답지와 DB 저장값을 분리하며, 화면에 노출되는 값에는 `MOCK`, `mock_user`, `persona`, `scenario`, `seed` 같은 생성기 용어를 쓰지 않는다.

사용자 계정 기준:
- `provider`는 실제 OAuth provider 값인 `KAKAO`, `NAVER`, `GOOGLE` 중 하나로 분산한다.
- `provider_user_id`는 provider별 형식 차이가 느껴지도록 숫자형, base36형, 길이가 긴 식별자를 섞되 실제 개인을 식별할 수 없는 합성값만 사용한다.
- `nickname`은 공개 맛집 리뷰 페이지에서 관찰한 표시명 패턴을 원문 저장 없이 집계해 만든다. 짧은 한글 별칭, 음식/일상형 별칭, 이름+취향형, 영문 핸들, 가족/반려동물형, 지역형을 섞는다. 예: `한끼식사`, `은지디저트`, `모카언니`, `cafe_route`.
- 닉네임은 전체 user 테이블에서 유니크해야 하며, `mock`, `test`, `user_0001`처럼 시연 몰입을 깨는 패턴을 금지한다.
- 닉네임 유니크 처리를 위해 숫자를 붙일 수 있지만 숫자만 바뀌는 계정이 과도하게 많아지지 않도록 무숫자 조합을 우선한다.
- 생년월일과 성별은 실제 OAuth/프로필 데이터처럼 일부 결측을 허용한다. 생년월일은 전체 결측, 연도만 공개, 연월만 공개, 전체 공개가 섞여야 하며, 값이 있을 때는 성인 사용자 중심의 현실적인 범위로 제한한다.

리스트/리뷰 콘텐츠 기준:
- 리스트 제목은 지역, 상황, 카테고리가 자연스럽게 섞이도록 만든다. 예: `역북동 점심 회전 빠른 곳`, `명지대 근처 카페 작업 리스트`.
- 리스트 설명에는 내부 페르소나명을 쓰지 않고 사용자의 실제 메모처럼 작성한다.
- `명지대 근처 근처`, `처인구 주변 주변`처럼 지역 표현이 중복되는 제목은 실패로 본다. 지역명 자체에 `근처`가 포함되어 있으면 추가 접미사를 붙이지 않는다.
- 리뷰 문장은 네이버 플레이스 방문자 리뷰와 카카오맵 사용자 후기에서 흔히 보이는 짧은 생활형 문장 구조를 참고한다. 단, 실제 리뷰 문장을 복사하지 않고 합성한다.
- 리뷰에는 식당명, 실제 메뉴명, 가격 체감, 양, 맛의 구체 요소, 친절, 대기, 주차, 좌석, 포장, 재방문 의사 중 2개 이상을 자연스럽게 섞는다.
- 리뷰 문장은 너무 정제된 홍보 문구처럼 만들지 않는다. `좋았어요`, `괜찮았어요`, `다음에 또 갈 듯`, `가격은 조금 있는 편`처럼 실제 사용자가 남길 법한 구어체를 섞는다.
- 긍정 리뷰만 만들지 않고, 중간 점수와 낮은 점수에는 아쉬운 포인트를 명확히 넣는다. 다만 악성 비방이나 허위 사실처럼 보이는 문장은 만들지 않는다.
- 같은 템플릿이 과하게 반복되지 않도록 카테고리별 표현, 실제 메뉴명, 방문 상황, 장단점 조합을 분산한다.
- 리뷰에 넣는 메뉴명은 실제 주문/섭취 가능한 메뉴만 사용한다. 쿠폰, 이벤트, 예약, 대관, 이용권, 어린이/개월 제한, 가격/옵션 문구처럼 메뉴가 아닌 항목은 제외하고, 적절한 메뉴가 없으면 카테고리 표현으로 대체한다.
- 신고 사유도 `MOCK_*` 같은 내부 코드 대신 서비스 운영에서 볼 수 있는 사유 문장으로 저장한다.
- `review_summaries`는 AI가 실제 리뷰를 요약해서 생성하는 결과물이므로 mock/seed 데이터로 미리 생성하거나 적재하지 않는다.

점수/행동 분포 기준:
- `list_restaurants.auto_score`는 0-100 범위의 내부 추천 점수로 보고, 사용자가 선별한 리스트 특성상 중상위가 많되 70-90 구간에만 몰리지 않게 한다.
- 실제 데이터처럼 취향 불일치, 신규 탐색, 과대노출, 방문 경험 편차가 반영되어 낮은 점수와 중간 점수도 충분히 존재해야 한다. 권장 검수 기준은 p10이 45-60, p50이 65-78, p90이 85-93 범위에 있고, 60 미만 항목이 의미 있는 비율로 존재하는 것이다.
- `taste_score`, `value_score`, `mood_score`는 같은 방향으로만 움직이지 않고 업종과 사용자 성향에 따라 한 축은 높고 다른 축은 낮은 조합도 허용한다.

검수 기준:
- provider/nickname/list/review에 생성기 흔적 패턴이 0건이어야 한다.
- 닉네임은 중복이 없어야 하며, 숫자만 바뀌는 동일 패턴이 과도하게 반복되면 실패로 본다.
- 생년월일은 전원 동일 범위의 완전 입력값이면 실패로 본다. 결측/부분 입력/전체 입력이 섞여야 하며 미성년 사용자 데이터는 만들지 않는다.
- `list_restaurants.auto_score`는 min/max/percentile/bucket 분포를 확인한다. 70점 이상으로만 몰리거나 표준편차가 지나치게 낮으면 실패로 본다.
- 리뷰의 50% 이상은 실제 메뉴명 또는 카테고리별 구체 표현을 포함해야 한다.
- 추천/랭킹 상위 결과는 메인 지역의 실제 식당과 카테고리 맥락이 맞아야 한다.
- 지역/카테고리/점수/사용자 행동 조합 분포를 확인해 특정 한 템플릿 또는 특정 식당에 몰리지 않아야 한다.
- `restaurants.region_name`은 실제 주소에서 다시 산출해 검증한다. 같은 시군구명이 다른 도에 존재하는 경우(`강원 고성군`, `경남 고성군` 등)는 도 약칭을 포함해 분리하고, 서울/광역시는 `서울특별시 성북구`처럼 광역시도+구를 유지한다.

재생성 범위 기준:
- 식당 기본정보(`restaurants`, `restaurant_menu_items`, `parking_lots`, `restaurant_photos`)는 Pcmap 검증 및 기존 seed 보존 대상이므로 일반 mock 재생성 때 변경하지 않는다.
- `tags`, `restaurant_tags`, `restaurant_menu_items.menu_tag_key`는 태그 추출 정책을 바꾸는 작업에서만 명시적으로 재생성한다. 이때 태그는 메뉴명만이 아니라 카테고리, 실제 메뉴 키워드, 편의정보, 이용 맥락을 함께 반영한다.
- mock 재생성은 사용자와 상호작용 테이블(`users`, `user_lists`, `list_restaurants`, `list_likes`, `user_follows`, `reliability_scores`, `reviews`, `review_votes`, `reports`)만 대상으로 한다.

## 3. 전제
### 3-1. 후보 수집 결과는 맛집 점수가 아니다
`community-restaurant-name-collector`의 `confidence`는 식당 품질 점수가 아니라 Naver Pcmap enrich 우선순위다.

따라서 `restaurant_name_candidates_for_enrich.csv`만으로 고득점 식당을 선정하지 않는다. 이 파일은 주소, 좌표, 카테고리, placeId를 채우기 전 단계의 후보 목록이다.

전국 단위 수집 결과도 동일하게 해석한다. 전국구 맛집 서비스를 만들기 위한 본 데이터셋이 아니라, 메인 지역 외 fallback/비교군/시연 보조 데이터를 확보하기 위한 후보 풀이다.

### 3-2. mock 생성 직접 입력은 enrich 이후 데이터다
mock 식당 데이터 생성에는 아래 필드가 채워진 식당만 사용한다.
- `resolved_name`
- `road_address` 또는 `jibun_address`
- `lat`
- `lng`
- `category_name`
- `naver_place_id` 또는 동등한 외부 식별자
- `region_id`
- `sido_name`
- `sigungu_name`
- 가능하면 `town_name`
- `seed_status = READY_FOR_MOCK`

위 조건을 만족하지 않으면 mock 평가 점수를 부여하지 않고 enrich 또는 검수 단계로 되돌린다.

### 3-3. 선정 식당 검증과 적재 흐름
선정된 식당은 바로 `restaurants`에 넣지 않는다. Pcmap API 검증과 중복 row 처리를 거친 뒤 저장한다.

처리 순서:
1. 전국/지역 후보 수집 결과에서 식당명을 선정한다.
2. Pcmap API로 `name`, `address`, `roadAddress`, `lat`, `lng`, `categoryName`, `pcmapPlaceId`를 검증한다.
3. `pcmapPlaceId`가 있으면 이를 최우선 중복 키로 사용한다.
4. `pcmapPlaceId`가 없으면 `normalizedName + normalizedAddress`를 보조 중복 키로 사용한다.
5. 이미 저장된 식당이면 기존 `restaurants` row를 재사용하거나 필요한 메타데이터만 update한다.
6. 저장되지 않은 식당만 `restaurants`에 신규 적재한다.
7. 중복 후보는 mock 점수 생성 대상에서 제외하고, 하나의 canonical restaurant id로 합친다.

중복 처리 우선순위:
1. `pcmapPlaceId` exact match
2. `name + roadAddress` exact match
3. `normalizedName + normalizedAddress` match
4. 좌표 근접성과 카테고리 일치 기반 수동 검토

이 과정을 통과한 식당만 `READY_FOR_MOCK`으로 본다.

### 3-4. 서비스 중심 지역
현재 mock 데이터의 중심 서비스 지역은 아래와 같이 고정한다.

| 구분 | 기준 |
| --- | --- |
| 메인 지역 | 경기도 용인시 처인구 역북동 |
| 기준 권역 | 명지대학교 자연캠퍼스 근방 |
| 주요 검증 대상 | 랭킹, 개인화 식당 추천, 리스트 추천, 숨은 맛집 추천 |
| 보조 지역 | 시연 fallback과 비교군을 위한 소량 지역 |

메인 지역 데이터는 사용자가 실제 서비스를 쓴다고 느낄 수 있을 정도로 밀도 있게 만든다. 보조 지역은 전국구 서비스처럼 풍부하게 만들지 않고, fallback 동작과 지역 차이를 보여줄 수 있는 최소 규모만 둔다.

전국 단위 후보 선정은 아래 목적으로만 사용한다.
- 역북동/명지대 자연캠퍼스 근방의 실제 식당 후보를 충분히 확보
- 처인구 내 인접 동네 후보 확보
- 용인시 또는 경기도 내 fallback 후보 확보
- 시연 중 다른 지역 검색/추천 화면이 완전히 비어 보이지 않도록 소량 보조 데이터 확보
- 전국구 후보 분포를 참고해 카테고리와 식당명 다양성 보강

## 4. 식당 잠재 품질 기준
### 4-1. 잠재 품질 축
식당은 생성 전에 아래 3개 잠재 품질을 가진다.

| 값 | 의미 | 범위 |
| --- | --- | --- |
| `tasteQuality` | 맛 자체의 품질 | `1.0 ~ 10.0` |
| `valueQuality` | 가격 대비 만족도 | `1.0 ~ 10.0` |
| `moodQuality` | 분위기, 공간, 데이트/카페 적합도 | `1.0 ~ 10.0` |

기본 잠재 품질:

```text
trueQuality = tasteQuality * 0.6 + valueQuality * 0.2 + moodQuality * 0.2
```

`trueQuality`는 generator 내부 기준이다. DB에 저장하는 `autoScore`는 사용자의 실제 평가 점수에서 현재 코드 공식으로 계산한다.

### 4-2. 식당 유형 분포
mock 데이터는 아래 유형을 넓게 포함해야 한다. 상위권/숨은 맛집만 만들면 랭킹과 추천이 쉽게 맞아 보이는 편향 데이터가 되므로, 낮은 품질, 특정 항목 특화, 프랜차이즈, 과대노출 식당까지 의도적으로 섞는다.

| 유형 | 품질 기준 | 노출 기준 | 평가 수 목표 | 평균 autoScore 목표 | 기대 역할 |
| --- | --- | --- | --- | --- | --- |
| 검증된 인기 맛집 | `elite/good` | `popular` | `20 ~ 45` | `87 ~ 94` | 랭킹 상위, 숨은 맛집 제외 |
| 숨은 맛집 | `elite/good` | `hidden` | `3 ~ 7` | `88 ~ 95` | 숨은 맛집 상위 |
| 신규/저평가 우수 식당 | `good` | `hidden/normal` | `2 ~ 5` | `82 ~ 90` | 성장 후보, 추천 후보 |
| 무난한 동네 식당 | `normal` | `normal` | `8 ~ 20` | `70 ~ 84` | 중간권, 비교군 |
| 가성비 특화 식당 | value 높음 | `normal` | `6 ~ 18` | `74 ~ 88` | 가성비 페르소나 추천 |
| 분위기 특화 식당 | mood 높음 | `normal/hidden` | `5 ~ 15` | `74 ~ 90` | 카페/데이트 추천 |
| 맛 특화 식당 | taste 높음, value/mood 보통 | `normal/popular` | `8 ~ 25` | `80 ~ 92` | 맛 우선 추천 |
| 프랜차이즈/표준형 | `normal` | `popular/normal` | `10 ~ 30` | `65 ~ 82` | 현실성, 대조군 |
| 과대노출 식당 | `normal/weak` | `popular` | `15 ~ 35` | `58 ~ 76` | 평가 수만 많은 식당 검증 |
| 낮은 품질 식당 | `weak` | `normal/hidden` | `3 ~ 15` | `45 ~ 68` | 추천 하위/제외 확인 |
| 취향 불일치 식당 | 한 축만 높고 나머지 낮음 | `normal` | `5 ~ 15` | `60 ~ 78` | 페르소나별 차이 검증 |
| 이상치/노이즈 식당 | 점수 분산 큼 | `normal` | `4 ~ 12` | `55 ~ 88` | 극단 평가 보정 검증 |

지역별로 상위권 식당만 만들지 않는다. 최소한 아래 비율을 맞춘다.

| 유형 그룹 | 권장 비율 |
| --- | --- |
| 검증된 인기 맛집 | `8 ~ 12%` |
| 숨은 맛집 / 신규 우수 식당 | `10 ~ 15%` |
| 무난한 동네 식당 | `25 ~ 35%` |
| 항목 특화 식당 | `20 ~ 30%` |
| 프랜차이즈/표준형 | `8 ~ 12%` |
| 과대노출/낮은 품질/이상치 | `12 ~ 18%` |

### 4-3. 외부 신호 기반 분류 보조 기준
실제 seed 또는 외부 enrich 결과에 아래 값이 있으면 품질이 아니라 노출/신뢰도 보조 신호로만 사용한다.

| 외부 신호 | 사용 방식 |
| --- | --- |
| 수집 언급 수, source 수 | `exposureTier` 보조 |
| 플랫폼 리뷰 수 | `popular/normal/hidden` 보조 |
| 플랫폼 평점 | `qualityTier` 보조 후보. 단, mock 점수의 직접 원천으로 쓰지 않음 |
| 프랜차이즈 여부 | 개별 맛집성보다 무난한/표준화된 식당으로 분산 배치 |
| 카테고리 | 점수 생성 가중치와 리스트 테마 구성에 사용 |

예:
- 리뷰 수와 언급 수가 많고 평점도 높으면 `popular + good/elite` 후보
- 리뷰 수는 적지만 평점/언급 품질이 좋고 카테고리가 명확하면 `hidden + good` 후보
- 프랜차이즈는 지역별로 일부만 포함하고 S 등급 독식을 피한다

## 5. 페르소나 조합 기준
### 5-1. 조합 축
사용자는 단일 타입 이름만 붙이지 않고 여러 축의 조합으로 만든다.

| 축 | 값 예시 |
| --- | --- |
| 지역 성향 | `main_region_strong`, `cheoin_nearby_ok`, `yongin_fallback_ok`, `explorer` |
| 카테고리 선호 | `korean`, `japanese`, `western`, `cafe_dessert`, `street_food`, `bar`, `meat`, `healthy`, `chinese`, `fast_food`, `brunch` |
| 평가 항목 선호 | `taste_first`, `value_first`, `mood_first`, `taste_value`, `taste_mood`, `value_mood`, `balanced`, `category_sensitive`, `low_variance`, `adventurous` |
| 채점 성향 | `very_generous`, `generous`, `neutral`, `strict`, `very_strict` |
| 탐색 성향 | `popular_first`, `hidden_gem_finder`, `safe_choice`, `new_place_explorer`, `review_sensitive` |
| 가격 민감도 | `price_sensitive`, `normal`, `premium_ok`, `occasion_based` |
| 활동 맥락 | `lunch`, `dinner`, `date`, `family`, `solo`, `meeting`, `cafe_work`, `drinking` |

페르소나는 이 축을 조합해서 만든다. 모든 조합을 다 만들 필요는 없지만, 각 축의 값이 최소 1번 이상 등장해야 한다.

### 5-2. 기본 페르소나 세트
최소 15개 페르소나를 둔다. 사용자는 최소 500명이며, 각 페르소나에는 최소 15명 이상을 배정한다. 다만 실제 서비스 데이터처럼 분포는 균등하게 만들지 않는다.

| 페르소나 | 선호 지역 | 선호 카테고리 | 항목 선호 | 채점 성향 | 탐색 성향 |
| --- | --- | --- | --- | --- | --- |
| 지역 거주 점심형 | main-region | 한식/분식/국밥/백반 | value_first | neutral | safe_choice |
| 직장인 점심형 | main-region | 백반/중식/일식 | taste_value | strict | safe_choice |
| 맛 우선 한식러 | main-region | 한식/고기 | taste_first | neutral | popular_first |
| 카페 작업/미팅형 | main-region | 카페/디저트 | mood_value | generous | hidden_gem_finder |
| 데이트/외식형 | cheoin-nearby | 양식/일식/카페 | taste_mood | neutral | review_sensitive |
| 술집 탐색형 | cheoin-nearby | 술집/고기 | mood_first | generous | new_place_explorer |
| 가족/지인 외식형 | main-region | 한식/고기/양식 | balanced | neutral | popular_first |
| 가성비 엄격형 | main-region | 분식/백반/패스트푸드 | value_first | very_strict | safe_choice |
| 프리미엄 외식형 | yongin-fallback | 일식/양식/고기 | taste_mood | strict | review_sensitive |
| 건강식 선호형 | main-region | 건강식/샐러드/브런치 | balanced | strict | safe_choice |
| 카테고리 탐색형 | explorer | 다양한 카테고리 | category_sensitive | neutral | new_place_explorer |
| 숨은 맛집 수집형 | cheoin-nearby | 한식/카페/술집 | adventurous | generous | hidden_gem_finder |
| 리뷰 신뢰형 | main-region | 다양한 카테고리 | low_variance | strict | review_sensitive |
| 무난 안전형 | main-region | 다양한 카테고리 | balanced | neutral | safe_choice |
| 광역 방문형 | yongin-fallback | 일식/양식/카페 | taste_first | generous | popular_first |

사용자 분포 예:

| 구분 | 비율 |
| --- | --- |
| 상위 5개 핵심 페르소나 | 전체 사용자의 `45 ~ 55%` |
| 중간 6개 페르소나 | 전체 사용자의 `30 ~ 40%` |
| 소수 4개 이상 페르소나 | 전체 사용자의 `10 ~ 20%` |

각 페르소나 최소 인원은 15명이다. 단, 모든 페르소나를 같은 수로 만들지 않고 `15 ~ 70명` 범위에서 불균등하게 배치한다. golden demo user는 20명을 별도로 고정하고, 나머지는 같은 분포에서 생성한다.

메인 서비스 지역은 역북동/명지대 자연캠퍼스 근방이지만, 이는 사용자 범위를 학생으로 한정한다는 뜻이 아니다. 페르소나는 지역 거주자, 근처 직장인, 방문자, 외식 사용자, 카페 이용자처럼 일반 사용자를 포괄해야 한다. 다만 추천 검증을 위해 `main-region` 성향을 중심으로 두고, `cheoin-nearby`, `yongin-fallback`, `explorer` 성향을 일부 유지한다.

### 5-3. collaborative score를 위한 overlap 설계
같은 취향 군집 안에서는 일부 식당을 의도적으로 겹치게 넣는다.

기준:
- 같은 페르소나 사용자끼리 고평가 식당 2~4개 overlap
- 인접 페르소나끼리 고평가 식당 1~2개 overlap
- 무관한 페르소나끼리는 overlap 0~1개
- demo user와 추천 후보 생산 유저 사이에는 최소 2개 공통 평가가 있어야 한다

이 overlap은 추천이 납득되도록 만드는 신호다. 단, 모든 사용자가 같은 S/A 식당만 공유하면 개인화 차이가 사라지므로 페르소나별 anchor 식당을 다르게 둔다.

## 6. 리스트 테마 기준
리스트는 무작위 식당 묶음이 아니라 테마를 가져야 한다.

### 6-1. 리스트 테마 축
리스트 테마는 아래 축을 조합해서 만든다.

| 축 | 값 예시 |
| --- | --- |
| 지역 | `regionName`, `regionTownName`, `none` |
| 상황 | 점심, 저녁, 데이트, 혼밥, 회식, 가족 외식, 카페 작업, 술자리, 포장, 배달, none |
| 카테고리 | 한식, 일식, 양식, 카페, 술집, 분식, 중식, 고기, 브런치, 패스트푸드, none |
| 평가 항목 | 맛, 가성비, 분위기, 균형, 양, 접근성, 재방문, none |
| 탐색성 | 실패 없는 곳, 숨은 곳, 자주 가는 곳, 새로 가본 곳, 리뷰 좋은 곳, none |
| 동행 맥락 | 혼자, 친구, 연인, 가족, 직장 동료, 모임, none |
| 가격대 | 저렴한, 적당한, 프리미엄, 가격 무관, none |
| 시간대 | 아침, 점심, 저녁, 야식, 주말, none |
| 이용 방식 | 매장 식사, 포장, 배달, 대기 가능, 빠른 식사, none |

`none`은 테마 축을 의도적으로 비워 정형화된 리스트만 생성되는 것을 막기 위한 값이다. 모든 리스트가 지역+상황+카테고리 조합으로만 만들어지면 제목과 식당 구성이 과도하게 규칙적으로 보이므로, 일부 리스트는 `none` 축을 포함해 더 자연스럽게 만든다.

예:
- `용인 처인구 가성비 점심`
- `역북동 점심 실패 없는 한식 7곳`
- `역북동 혼밥하기 좋은 곳`
- `역북동 카페 작업하기 좋은 곳`
- `처인구 데이트 저녁 코스`
- `카페 작업하기 좋은 곳`

### 6-2. 리스트 구성 규칙
리스트당 식당은 최소 5개, 최대 20개 수준으로 만든다.

구성 기준:
- 같은 리스트 안의 식당은 `regionName` exact match를 지킨다.
- 리스트 제목과 식당 카테고리/상황이 맞아야 한다.
- 추천 후보로 쓰일 공개 리스트는 `isPublic = true`다.
- 공개 리스트는 전체의 `70%`, 비공개 리스트는 `30%`로 둔다.
- 특정 owner가 상위 리스트 추천을 독식하지 않도록 공개 리스트 owner를 분산한다.

리스트 내부 권장 구성:
- 사용자가 이미 좋아한 anchor 식당 1~2개
- 같은 테마의 신규 좋은 식당 3~8개
- 무난한 보조 식당 1~6개
- 큰 리스트에는 취향 확인용 대조 식당 1~3개 포함 가능
- 낮은 품질 식당은 추천 후보 리스트 상위권에는 과도하게 넣지 않는다

리스트 크기 분포:

| 리스트 크기 | 비율 | 용도 |
| --- | --- | --- |
| 5~7개 | `45 ~ 55%` | 일반 추천 후보, 최소 품질 조건 충족 |
| 8~12개 | `30 ~ 40%` | 풍부한 테마 리스트 |
| 13~20개 | `10 ~ 15%` | 인기 유저/정리형 유저의 대형 리스트 |

## 7. 점수 생성 보정 기준
### 7-1. 저장 공식과 generator 보정 분리
저장되는 `autoScore` 공식은 현재 코드 기준을 유지한다.

```text
autoScore = round((tasteScore * 0.6 + valueScore * 0.2 + moodScore * 0.2) * 10, 1)
```

카테고리별 가중치, 치명적 약점 감점, 최고점 cap, 균형 보너스는 DB 저장 공식을 바꾸는 규칙이 아니다. generator가 `tasteScore`, `valueScore`, `moodScore`를 만들 때 사용하는 내부 보정 기준이다.

### 7-2. 사용자 평가 점수 생성식
각 사용자의 식당별 입력 점수는 아래 요소로 만든다.

```text
rawAspectScore
= restaurantAspectQuality
+ personaAspectBonus
+ categoryFitBonus
+ exposureBias
+ userStrictnessBias
+ noise
```

권장 범위:
- `personaAspectBonus`: `-0.5 ~ +0.9`
- `categoryFitBonus`: `-0.8 ~ +1.0`
- `exposureBias`: 인기 식당 선호자에게 `popular +0.2 ~ +0.5`, 숨은 맛집 선호자에게 `hidden +0.2 ~ +0.5`
- `userStrictnessBias`: `very_generous +0.6`, `generous +0.3`, `neutral 0`, `strict -0.4`, `very_strict -0.7`
- `noise`: `-0.4 ~ +0.4`

최종 `tasteScore`, `valueScore`, `moodScore`는 `1.0 ~ 10.0`으로 clamp하고 소수점 1자리로 반올림한다.

### 7-3. 카테고리별 내부 가중치
카테고리별로 사용자가 체감하는 중요도를 다르게 둔다. 이 값은 점수 생성 보조 기준이다.

| 카테고리 | taste | value | mood | 설명 |
| --- | --- | --- | --- | --- |
| 일반 한식/고기/일식/양식 | 0.6 | 0.2 | 0.2 | 기본 식사형 |
| 분식/국밥/백반/점심 | 0.5 | 0.35 | 0.15 | 가성비 체감 증가 |
| 카페/디저트 | 0.35 | 0.2 | 0.45 | 공간성과 분위기 증가 |
| 술집/데이트 | 0.4 | 0.2 | 0.4 | 분위기 영향 증가 |
| 프리미엄/오마카세 | 0.65 | 0.1 | 0.25 | 맛과 경험 중심 |
| 패스트푸드/프랜차이즈 | 0.45 | 0.35 | 0.2 | 표준화와 가격 영향 |
| 건강식/브런치 | 0.45 | 0.2 | 0.35 | 취향성과 분위기 반영 |

### 7-4. 치명적 약점 감점과 최고점 cap
세 항목 중 특정 값이 너무 낮으면 다른 항목이 좋아도 최종 평가가 과도하게 높아지지 않게 한다.

| 조건 | 내부 처리 |
| --- | --- |
| `tasteScore < 6.0` | 최종 `autoScore` 목표를 `75` 이하로 유도 |
| `tasteScore < 5.0` | 최종 `autoScore` 목표를 `65` 이하로 유도 |
| 카페/데이트/술집에서 `moodScore < 6.0` | 최종 `autoScore` 목표를 `78` 이하로 유도 |
| 가성비형 카테고리에서 `valueScore < 6.0`이고 `tasteScore < 8.5` | 최종 `autoScore` 목표를 `78` 이하로 유도 |
| 세 항목 중 두 항목이 `6.5` 미만 | 최종 `autoScore` 목표를 `72` 이하로 유도 |

구현 방식은 `autoScore`를 직접 덮어쓰는 것이 아니라, 입력 점수 생성 단계에서 해당 cap을 만족하도록 `tasteScore`, `valueScore`, `moodScore`를 재샘플링하거나 낮춘다.

### 7-5. 균형 보너스와 불균형 감점
세 항목이 고르게 높은 식당은 약간 유리하게, 한 항목만 높고 나머지가 낮은 식당은 약간 불리하게 만든다.

| 조건 | 내부 보정 |
| --- | --- |
| 세 항목 모두 `8.0` 이상이고 표준편차가 낮음 | `+0.2 ~ +0.4` |
| 한 항목만 `9.0` 이상이고 다른 두 항목이 `7.0` 미만 | `-0.3 ~ -0.6` |
| 맛은 높지만 분위기/가성비가 낮은 식당 | 일반 랭킹은 가능, 분위기/가성비 페르소나 추천에서는 약화 |
| 분위기만 높은 식당 | 카페/데이트 페르소나에는 유리, 일반 맛집 랭킹 독식은 방지 |

## 8. 고득점 식당 선정 기준
### 8-1. 기준 점수
고득점 식당은 개별 사용자가 준 `autoScore` 하나로 선정하지 않는다. 랭킹 정책과 동일하게 사용자-식당 축약 후 보정 점수를 기준으로 선정한다.

사용하는 값:
- `R`: 식당별 평균 `autoScore`
- `v`: 식당별 `evaluationCount`
- `C`: 동일 랭킹 범위 전체 평균 `autoScore`
- `m`: 보정 상수 `5`

```text
adjustedScore = ((v * R) + (m * C)) / (v + m)
```

정렬 기준은 `ranking-policy.md`와 동일하게 적용한다.
1. `adjustedScore` 내림차순
2. `evaluationCount` 내림차순
3. `averageAutoScore` 내림차순
4. `restaurantId` 오름차순

### 8-2. mock 상위권 식당 확정 컷오프
mock 데이터에서 "상위권 식당"으로 확정하려면 아래 조건을 모두 만족해야 한다.

| 구분 | 기준 |
| --- | --- |
| 식당 seed 상태 | `READY_FOR_MOCK` |
| 필수 메타데이터 | 주소, 좌표, 카테고리, 지역, 외부 식별자 존재 |
| 평가 사용자 수 | `evaluationCount >= 6` |
| 평균 점수 | `averageAutoScore >= 88.0` |
| 보정 점수 | `adjustedScore >= 85.0` |
| 상대 순위 | 동일 지역 또는 동일 지역+카테고리 범위 상위 10% |

상위 10%가 3개 미만이면 지역별 최소 3개까지 상위권 후보로 둔다. 단, `evaluationCount < 6`인 식당은 평균 점수가 높아도 상위권으로 확정하지 않는다.

### 8-3. 등급 구분
mock 데이터 생성 시 상위권 식당은 아래 등급으로 나눈다.

| 등급 | 용도 | 기준 |
| --- | --- | --- |
| S | 확실한 랭킹 최상위 | `evaluationCount >= 8`, `averageAutoScore >= 92.0`, `adjustedScore >= 90.0` |
| A | 일반 상위권 | `evaluationCount >= 6`, `averageAutoScore >= 88.0`, `adjustedScore >= 85.0` |
| B | 상위권 경쟁군 | `evaluationCount >= 4`, `averageAutoScore >= 78.0`, `adjustedScore >= 75.0` |
| C | 중립/일반군 | `averageAutoScore 60.0 ~ 77.9` |
| D | 낮은 점수 대조군 | `averageAutoScore < 60.0` |

S/A 식당만 "고득점 식당"으로 설명한다. B/C/D는 추천과 랭킹이 구분되는지 확인하기 위한 대조군이다.

## 9. mock 평가 점수 생성 기준
### 9-1. `autoScore` 공식
점수는 반드시 현재 코드의 공식으로 계산한다.

```text
autoScore = round((tasteScore * 0.6 + valueScore * 0.2 + moodScore * 0.2) * 10, 1)
```

입력 점수 범위:
- `tasteScore`: `1.0 ~ 10.0`
- `valueScore`: `1.0 ~ 10.0`
- `moodScore`: `1.0 ~ 10.0`

### 9-2. 등급별 입력 점수 범위
점수는 완전 난수가 아니라 등급별 범위 안에서 생성한다.

| 등급 | tasteScore | valueScore | moodScore | 기대 autoScore |
| --- | --- | --- | --- | --- |
| S | `9.2 ~ 10.0` | `8.6 ~ 9.8` | `8.6 ~ 9.8` | `90.0 ~ 100.0` |
| A | `8.8 ~ 9.6` | `8.0 ~ 9.4` | `8.0 ~ 9.4` | `85.0 ~ 93.9` |
| B | `7.6 ~ 8.8` | `7.0 ~ 8.6` | `7.0 ~ 8.6` | `75.0 ~ 86.9` |
| C | `5.8 ~ 7.4` | `5.5 ~ 7.4` | `5.5 ~ 7.4` | `58.0 ~ 74.9` |
| D | `3.0 ~ 5.6` | `3.0 ~ 5.8` | `3.0 ~ 5.8` | `30.0 ~ 57.9` |

맛 비중이 60%이므로 S/A 식당은 `tasteScore`를 가장 높게 둔다. 다만 모든 상위권 식당의 세부 점수가 동일하면 추천 검증이 약해지므로 `valueScore`, `moodScore`는 식당 성격에 따라 차이를 둔다.

## 10. mock 데이터 구성 기준
### 10-1. 전체 규모
시연/검증용 기본 규모는 아래를 권장한다.

| 데이터 | 권장 기준                   |
| --- |-------------------------|
| 시연 지역 | 메인 지역 1개 + 주요 도시/보조 지역 |
| 식당 | Pcmap 검증 완료 식당만 사용. DB에 없으면 `restaurants` 신규 적재 |
| 사용자 | `500명 이상`               |
| 페르소나 | `15개 이상`                |
| 사용자별 리스트 | `1 ~ 5개`                |
| 리스트당 식당 | `5 ~ 20개`               |
| 공개 리스트 | `70%`                    |
| 비공개 리스트 | `30%`                   |
| golden demo user | `20명`                   |

golden demo user는 기대 추천 결과를 미리 정해 `expected_outputs.json`에 기록한다.

### 10-2. 지역별 데이터 밀도
mock 데이터의 중심은 전국구가 아니라 역북동/명지대 자연캠퍼스 근방의 메인 서비스 권역이다.

| 지역 구분 | 예시 | 식당 비율 | 사용자/리스트 비율 | 용도 |
| --- | --- | --- | --- | --- |
| 메인 권역 | 용인시 처인구 역북동, 명지대 자연캠퍼스 근방 | `45 ~ 55%` | `60 ~ 70%` | 핵심 랭킹/추천/숨은 맛집 시연 |
| 인접 처인구 | 김량장동, 삼가동, 유방동 등 | `10 ~ 15%` | `10 ~ 15%` | nearby fallback, 처인구 확장 |
| 용인/경기 보조 | 기흥구, 수지구, 수원/성남 일부 | `10 ~ 15%` | `5 ~ 10%` | 지역 fallback, 비교군 |
| 주요 도시 보조 | 서울, 부산, 대구, 광주, 대전 등 | `15 ~ 25%` | `10 ~ 15%` | 전국구 시연 보조, 지역 다양성 |
| 기타 전국 소량 보조 | 주요 도시 외 일부 지역 | `5% 이하` | `5% 이하` | 검색/화면 공백 방지 |

서울/부산/대구/광주/대전 등 주요 도시는 기존 전국 소량 보조보다 비중을 높인다. 다만 추천 품질 검증의 중심은 여전히 메인 권역이며, 주요 도시 데이터는 지역 다양성, 검색 화면, fallback 시연을 위한 보조 데이터다.

### 10-3. 지역 단위 최소 구성
랭킹과 추천을 한 지역에서 검증하려면 지역당 최소 아래 규모를 사용한다.

| 데이터 | 최소 기준 |
| --- | --- |
| enrich 완료 식당 | 20개 이상 |
| 사용자 | 10명 이상 |
| 리스트 | 20개 이상 |
| 리스트-식당 평가 row | 120개 이상 |
| S/A 식당 | 3개 이상 |
| B 식당 | 5개 이상 |
| C/D 대조군 | 8개 이상 |

지역 fallback, 리스트 추천 fallback까지 검증하려면 메인 권역과 보조 지역을 분리한다. 메인 권역은 same-region 후보가 충분한 지역, 보조 지역은 fallback이 발생하는 지역으로 설계한다.

위 표는 보조 지역에도 적용 가능한 최소 기준이다. 메인 권역은 전체 데이터 밀도 기준을 우선 적용하며, 사용자/리스트/평가 row가 보조 지역보다 훨씬 많아야 한다.

### 10-4. 리스트 생성 규칙
리스트는 현재 도메인 규칙을 반드시 만족해야 한다.
- 리스트당 식당 수는 최소 5개다.
- 동일 리스트 안에 같은 식당을 중복 배치하지 않는다.
- 리스트 지역과 식당 `regionName`은 exact match여야 한다.
- `isDeleted = false`, `isHidden = false`인 데이터만 기본 검증 입력으로 사용한다.
- 랭킹/식당 추천 계산에는 공개/비공개 리스트가 모두 포함된다.
- 리스트 추천 후보는 다른 사용자의 공개 리스트만 포함된다.

권장 구성:
- 사용자당 리스트 1~5개
- 리스트당 식당 5~20개
- 공개 리스트 70%
- 비공개 리스트 30%

### 10-5. 평가 사용자 수 설계
랭킹의 `evaluationCount`는 row 수가 아니라 `user_id + restaurant_id` 축약 후 사용자 수다.

따라서 특정 식당을 상위권으로 만들려면 같은 사용자의 여러 리스트에 반복 배치하지 말고, 서로 다른 사용자 6명 이상이 평가하도록 설계한다.

기준:
- S 식당: 서로 다른 사용자 8~12명 평가
- A 식당: 서로 다른 사용자 6~9명 평가
- B 식당: 서로 다른 사용자 4~7명 평가
- 숨은 맛집 후보: 서로 다른 사용자 3~7명 평가
- D 대조군: 서로 다른 사용자 2명 이상 평가

### 10-6. 추천 신호 설계
개인화 식당 추천 검증을 위해 아래 신호가 함께 드러나야 한다.
- 사용자가 이미 가진 식당은 후보에서 제외된다.
- 같은 지역 후보가 우선 나온다.
- 같은 지역 후보가 부족할 때만 fallback 후보가 나온다.
- 사용자가 높게 평가한 카테고리와 후보 식당 카테고리가 맞아야 높은 점수를 받는다.
- 랭킹 보정 점수가 높은 후보가 `rankingAdjustmentScore`에서 유리해야 한다.
- 공통 식당 평가가 있는 사용자가 있어야 `collaborativeScore`를 확인할 수 있다.

리스트 추천 검증을 위해 아래 신호가 함께 드러나야 한다.
- 후보 리스트는 공개 리스트여야 한다.
- 후보 리스트는 현재 사용자의 리스트가 아니어야 한다.
- 후보 리스트는 visible 식당 5개 이상이어야 한다.
- same-region 후보가 먼저 채워져야 한다.
- 공개 리스트마다 식당 겹침, 카테고리 겹침, 점수 스타일 차이가 드러나야 한다.

## 11. 생성 순서
mock 데이터는 아래 순서로 만든다.

1. `READY_FOR_API` 상호명 후보를 Naver Pcmap enrich 입력으로 사용한다.
2. 주소, 좌표, 카테고리, placeId를 채운 `READY_FOR_MOCK` 식당 후보를 만든다.
3. 후보를 메인 권역, 인접 처인구, 용인/경기 보조, 주요 도시 보조, 기타 전국 소량 보조로 분류한다.
4. 메인 권역 식당 수와 카테고리 다양성이 충분한지 먼저 확인한다.
5. Pcmap API 검증과 중복 row 처리를 수행한다.
6. 기존 DB에 없는 검증 완료 식당을 `restaurants`에 적재한다.
7. 식당마다 `qualityTier`, `exposureTier`, 잠재 품질 3축을 부여한다.
8. 지역별 식당 유형 분포가 한쪽으로 몰리지 않는지 확인한다.
9. 사용자 페르소나 조합표를 만든다.
10. golden demo user 20명을 먼저 고정한다.
11. 사용자별 리스트 테마를 만들고 지역을 고정한다.
12. 리스트 테마에 맞게 식당을 배치한다.
13. 식당 잠재 품질, 사용자 취향, 카테고리 적합도, 채점 성향, 약한 noise로 `tasteScore`, `valueScore`, `moodScore`를 만든다.
14. 코드 공식과 동일하게 `autoScore`를 계산한다.
15. 팔로우, 리스트 좋아요, 리뷰, 리뷰 투표, 신고, 신뢰도 데이터를 보강한다.
16. 랭킹 검증 쿼리로 `averageAutoScore`, `evaluationCount`, `adjustedScore`를 확인한다.
17. 컷오프를 만족한 S/A 식당만 최종 고득점 식당으로 확정한다.
18. `expected_outputs.json`으로 demo user별 기대 추천 결과를 기록한다.

## 12. 검증 체크리스트
mock 데이터를 생성한 뒤 아래 항목을 확인한다.

- `autoScore` 공식 불일치가 0건인지
- 리스트당 식당 수가 5개 이상인지
- 동일 리스트 내 식당 중복이 없는지
- 리스트와 식당 지역 불일치가 0건인지
- S/A 식당의 `evaluationCount`가 6 이상인지
- S/A 식당의 `adjustedScore`가 85 이상인지
- 평균 점수만 높고 평가 사용자 수가 낮은 식당이 최상위로 과대 노출되지 않는지
- hidden gem 후보가 `evaluationCount 3~7` 범위에 존재하는지
- 개인화 추천 후보에 이미 보유한 식당이 섞이지 않는지
- 리스트 추천 후보에 현재 사용자의 리스트나 비공개 리스트가 섞이지 않는지

### 12-1. 편향 방지 체크
아래 항목 중 하나라도 깨지면 mock 데이터를 다시 생성한다.

| 항목 | 실패 기준 |
| --- | --- |
| 식당 유형 분포 | S/A 또는 인기 맛집이 전체의 30%를 초과 |
| 카테고리 분포 | 한 카테고리가 전체 식당의 45%를 초과 |
| 지역 분포 | 메인 권역 식당이 전체의 45% 미만이거나 55% 초과 |
| 주요 도시 분포 | 서울/부산/대구/광주/대전 등 주요 도시 보조 식당이 15% 미만이거나 25% 초과 |
| 보조 지역 분포 | 보조 지역에 fallback 후보가 거의 없거나 기타 전국 보조 데이터가 과도하게 많음 |
| 사용자 분포 | 특정 페르소나가 전체 사용자의 25%를 초과 |
| 공개 리스트 | 공개 리스트 비율이 68% 미만 또는 72% 초과 |
| owner 분산 | 한 owner의 공개 리스트가 추천 상위권을 반복 독식 |
| 점수 분포 | 평균 `autoScore`가 85 이상인 row가 전체의 40% 초과 |
| overlap | demo user와 모든 후보 리스트의 overlap이 0 또는 과도하게 동일 |

### 12-2. API 결과 검증 기준
mock 생성 후 API 결과는 사람이 봐도 설명 가능해야 한다.

랭킹:
- 상위권은 평가 수와 점수가 모두 높은 식당이어야 한다.
- 평가 수 1~2개 식당이 최상위권을 독식하면 실패다.

식당 추천:
- 이미 내 리스트에 있는 식당이 나오면 실패다.
- 추천 4개 중 최소 3개는 사용자의 지역/카테고리/항목 선호로 설명 가능해야 한다.
- 같은 지역 후보가 충분한데 fallback 후보가 먼저 나오면 실패다.

리스트 추천:
- 식당 5개 미만 리스트가 나오면 실패다.
- 내 리스트 또는 비공개 리스트가 나오면 실패다.
- 제목, 지역, 카테고리, 포함 식당이 서로 맞지 않으면 실패다.
- 상위 리스트는 내가 좋아한 식당 1~2개 또는 카테고리/점수 스타일 유사성을 가져야 한다.

숨은 맛집:
- 상위권은 `evaluationCount 3~7`, `averageAutoScore 88+` 중심이어야 한다.
- 평가 수가 너무 많은 인기 맛집은 제외되어야 한다.
- 점수 낮은 식당이 단지 평가 수가 적다는 이유로 올라오면 실패다.

### 12-3. 상위권 식당 검증 SQL 기준
mock 데이터 생성 후 상위권 식당은 아래 형태의 쿼리로 검증한다. 실제 검증 SQL은 테스트 데이터의 prefix나 region 조건에 맞게 `WHERE`를 추가한다.

```sql
WITH user_restaurant_best AS (
    SELECT
        ul.user_id,
        lr.restaurant_id,
        MAX(lr.auto_score) AS best_auto_score
    FROM list_restaurants lr
    JOIN user_lists ul ON ul.id = lr.list_id
    JOIN users u ON u.id = ul.user_id
    JOIN restaurants r ON r.id = lr.restaurant_id
    WHERE ul.is_deleted = false
      AND ul.is_hidden = false
      AND u.is_deleted = false
      AND u.is_hidden = false
      AND r.is_deleted = false
      AND r.is_hidden = false
    GROUP BY ul.user_id, lr.restaurant_id
),
global_average AS (
    SELECT AVG(best_auto_score) AS global_avg
    FROM user_restaurant_best
),
restaurant_scores AS (
    SELECT
        r.id AS restaurant_id,
        r.name AS restaurant_name,
        r.region_name,
        r.category_name,
        COUNT(*) AS evaluation_count,
        ROUND(AVG(urb.best_auto_score), 1) AS average_auto_score,
        ROUND(
            ((COUNT(*) * AVG(urb.best_auto_score)) + (5 * ga.global_avg))
            / (COUNT(*) + 5),
            2
        ) AS adjusted_score
    FROM user_restaurant_best urb
    JOIN restaurants r ON r.id = urb.restaurant_id
    CROSS JOIN global_average ga
    GROUP BY r.id, r.name, r.region_name, r.category_name, ga.global_avg
)
SELECT *
FROM restaurant_scores
WHERE evaluation_count >= 6
  AND average_auto_score >= 88.0
  AND adjusted_score >= 85.0
ORDER BY adjusted_score DESC, evaluation_count DESC, average_auto_score DESC, restaurant_id ASC;
```

이 결과에 포함된 식당 중 동일 지역 또는 동일 지역+카테고리 범위 상위 10%에 들어가는 식당을 최종 S/A 고득점 식당으로 확정한다.

## 13. 다른 문서와의 관계
- `score-policy.md`: `autoScore` 계산식의 원천 기준
- `ranking-policy.md`: 상위권 식당 판정의 원천 기준
- `recommendation-policy.md`: 식당 추천 검증 시나리오 기준
- `list-recommendation-policy.md`: 리스트 추천 검증 시나리오 기준
- `seed-import.md`: enrich 완료 식당 seed import 기준
- `community-restaurant-name-collector/README.md`: enrich 전 상호명 후보 생성 기준
