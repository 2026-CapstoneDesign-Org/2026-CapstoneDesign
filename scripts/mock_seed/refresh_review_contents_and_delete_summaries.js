const fs = require("fs");
const path = require("path");
const { spawnSync } = require("child_process");

const rootDir = path.resolve(__dirname, "..", "..");
const outputDir = path.resolve(process.env.OUTPUT_DIR || path.join(rootDir, "tmp", "mock_seed_import"));
const DB_CONTAINER = process.env.DB_CONTAINER || "postgres-dev";
const DB_USER = process.env.DB_USER || "dev_user";
const DB_NAME = process.env.DB_NAME || "dev_db";
const APPLY = process.argv.includes("--apply");
const now = "2026-05-31T14:00:00+09:00";

function hash(input) {
  let h = 2166136261;
  const text = String(input);
  for (let i = 0; i < text.length; i += 1) {
    h ^= text.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

function pick(array, seed) {
  return array[hash(seed) % array.length];
}

function sqlQuote(value) {
  if (value === null || value === undefined) return "NULL";
  return `'${String(value).replace(/'/g, "''")}'`;
}

function psql(sql) {
  const result = spawnSync(
    "docker",
    ["exec", "-i", DB_CONTAINER, "psql", "-U", DB_USER, "-d", DB_NAME, "-v", "ON_ERROR_STOP=1", "-t", "-A"],
    { input: sql, encoding: "utf8", maxBuffer: 1024 * 1024 * 200 }
  );
  if (result.status !== 0) {
    throw new Error(`psql failed\n${result.stdout}\n${result.stderr}`);
  }
  return result.stdout.trim();
}

function queryJson(sql) {
  const wrapped = `SELECT COALESCE(json_agg(row_to_json(t)), '[]'::json) FROM (${sql}) t;`;
  const stdout = psql(wrapped);
  return JSON.parse(stdout || "[]");
}

function categoryKey(row) {
  const text = `${row.category_name || ""} ${row.primary_category_name || ""} ${row.restaurant_name || ""}`;
  if (/카페|디저트|커피|브런치/.test(text)) return "cafe";
  if (/베이커리|빵|케이크|도넛|떡/.test(text)) return "bakery";
  if (/고기|육류|삼겹|갈비|구이|소고기|돼지|바베큐/.test(text)) return "meat";
  if (/중식|중국|짬뽕|짜장|마라|탕수육/.test(text)) return "chinese";
  if (/일식|초밥|스시|돈가스|돈까스|라멘|우동|소바|카츠|덮밥/.test(text)) return "japanese";
  if (/양식|파스타|피자|스테이크|버거|이탈리아/.test(text)) return "western";
  if (/분식|김밥|떡볶|튀김|호떡/.test(text)) return "street";
  if (/술집|주점|맥주|호프|이자카야|포차|와인|하이볼/.test(text)) return "bar";
  if (/냉면|국수|칼국수|막국수|면/.test(text)) return "noodle";
  if (/회|해물|생선|수산|조개|아구|아귀/.test(text)) return "seafood";
  if (/치킨|샌드위치|토스트|햄버거/.test(text)) return "fast";
  if (/한식|국밥|찌개|백반|곰탕|순대|감자탕|밥|샤브/.test(text)) return "korean";
  return "other";
}

const CATEGORY_LABELS = {
  cafe: "카페",
  bakery: "디저트",
  meat: "고기",
  chinese: "중식",
  japanese: "일식",
  western: "양식",
  street: "분식",
  bar: "술집",
  noodle: "면요리",
  seafood: "해산물",
  fast: "간단한 식사",
  korean: "한식",
  other: "식사",
};

function normalizeMenuName(value) {
  return String(value || "")
    .replace(/\s+/g, " ")
    .replace(/\s*\([^)]*\)/g, "")
    .replace(/\s*(HOT|ICE|Hot|Ice|hot|ice)\s*/g, " ")
    .replace(/\s*\d{1,3}(,\d{3})*원.*$/g, "")
    .replace(/\s*(대표|추천|BEST|Best|best)\s*/g, " ")
    .trim();
}

function isReviewableMenu(value) {
  const text = normalizeMenuName(value);
  if (text.length < 2 || text.length > 28) return false;
  if (/^\d+$/.test(text)) return false;
  return !/(개월|무료|쿠폰|혜택|생일|이벤트|리뷰|영수증|예약|대관|문의|변동|시가|추가|옵션|포장|배달|주차|어린이|미만|이용권|멤버십|콜키지|서비스|원산지|인분추가|테이블)/.test(text);
}

function menuFor(row) {
  const menus = Array.isArray(row.menus) ? row.menus.map(normalizeMenuName).filter(isReviewableMenu) : [];
  if (menus.length > 0) return pick(menus, `menu:${row.id}:${row.restaurant_id}`);
  return defaultMenu(row.category_key);
}

function locationLabel(row) {
  if (row.region_town_name && row.region_town_name.trim()) return row.region_town_name.trim();
  return row.region_name || "근처";
}

function hasBatchim(text) {
  const last = String(text || "").trim().charCodeAt(String(text || "").trim().length - 1);
  if (last < 0xac00 || last > 0xd7a3) return false;
  return (last - 0xac00) % 28 !== 0;
}

function objectPhrase(text) {
  return `${text}${hasBatchim(text) ? "을" : "를"}`;
}

function topicPhrase(text) {
  return `${text}${hasBatchim(text) ? "은" : "는"}`;
}

function defaultMenu(category) {
  const defaults = {
    cafe: "아메리카노",
    bakery: "대표 디저트",
    meat: "고기 메뉴",
    chinese: "식사 메뉴",
    japanese: "식사 메뉴",
    western: "식사 메뉴",
    street: "분식 메뉴",
    bar: "안주 메뉴",
    noodle: "면 메뉴",
    seafood: "해산물 메뉴",
    fast: "기본 메뉴",
    korean: "식사 메뉴",
    other: "메인 메뉴",
  };
  return defaults[category] || "메인 메뉴";
}

function visitNeed(category) {
  const needs = {
    cafe: "카페 갈 곳",
    bakery: "디저트 먹을 곳",
    meat: "고기 먹을 곳",
    chinese: "중식 먹을 곳",
    japanese: "일식 먹을 곳",
    western: "양식 먹을 곳",
    street: "분식 먹을 곳",
    bar: "가볍게 한잔할 곳",
    noodle: "면요리 먹을 곳",
    seafood: "해산물 먹을 곳",
    fast: "간단히 먹을 곳",
    korean: "한식 먹을 곳",
    other: "식사할 곳",
  };
  return needs[category] || "식사할 곳";
}

function scoreBand(score) {
  if (score >= 86) return "high";
  if (score >= 74) return "good";
  if (score >= 62) return "mid";
  return "low";
}

function effectiveCategory(category, menu) {
  const text = String(menu || "");
  if (/커피|라떼|아메리카노|에이드|스무디|차|티|케이크|쿠키|마카롱|디저트|빙수|크림|베이글/.test(text)) return "cafe";
  if (/빵|식빵|깜빠뉴|소금빵|페이스트리|크루아상|타르트/.test(text)) return "bakery";
  if (/삼겹|목살|갈비|스테이크|불고기|고기|바베큐|구이/.test(text)) return "meat";
  if (/짬뽕|짜장|탕수육|마라|멘보샤|깐풍|중화/.test(text)) return "chinese";
  if (/돈까스|돈가스|카츠|초밥|스시|라멘|우동|소바|덮밥|규동|가츠/.test(text)) return "japanese";
  if (/파스타|피자|리조또|버거|샐러드|브런치/.test(text)) return "western";
  if (/김밥|떡볶|튀김|순대|어묵|호떡|와플/.test(text)) return "street";
  if (/맥주|하이볼|소주|안주|나베|전골/.test(text) && category === "bar") return "bar";
  if (/냉면|국수|칼국수|막국수|면/.test(text)) return "noodle";
  if (/회|아구|아귀|해물|조개|생선|새우/.test(text)) return "seafood";
  if (/치킨|토스트|샌드위치|핫도그/.test(text)) return "fast";
  if (/국밥|찌개|백반|탕|국수|밥|전|샤브|죽/.test(text)) return "korean";
  return category;
}

function categoryDetail(category, band, seed) {
  const byCategory = {
    cafe: {
      high: [
        "커피 맛이 튀지 않고 디저트랑 같이 먹었을 때 밸런스가 좋았어요.",
        "자리 분위기가 차분해서 오래 이야기하기에도 괜찮았습니다.",
        "음료 단맛이 과하지 않고 마무리가 깔끔해서 기억에 남습니다.",
      ],
      good: [
        "음료 맛은 안정적이고 좌석도 크게 불편하지 않았어요.",
        "사진 보고 기대한 정도는 했고, 잠깐 쉬어가기 좋은 쪽입니다.",
        "디저트는 무난했고 커피랑 같이 먹기에는 괜찮았습니다.",
      ],
      mid: [
        "음료는 무난했는데 공간이나 가격에서 약간 호불호가 있을 것 같아요.",
        "가까우면 들를 만하지만 일부러 찾아갈 정도까지는 아니었습니다.",
        "디저트는 괜찮았고 커피는 평범한 편이라 기대치를 낮추면 좋겠습니다.",
      ],
      low: [
        "분위기는 나쁘지 않았지만 음료 맛은 기대보다 평범했습니다.",
        "가격을 생각하면 디저트나 좌석 만족도가 조금 애매했어요.",
        "근처라 들르긴 편하지만 다시 찾을지는 조금 고민됩니다.",
      ],
    },
    korean: {
      high: [
        "간이 과하지 않고 밥이랑 같이 먹기 좋아서 한 끼 만족도가 높았습니다.",
        "국물이나 반찬이 따로 놀지 않고 전체적으로 안정적인 맛이었어요.",
        "집밥 느낌은 있는데 너무 심심하지 않아서 재방문 생각이 납니다.",
      ],
      good: [
        "맛은 크게 튀지 않지만 편하게 먹기 좋고 양도 무난했습니다.",
        "반찬 구성이 과하지 않고 점심 후보로 넣기 괜찮아요.",
        "간이 제 입맛에는 잘 맞았고 회전도 나쁘지 않았습니다.",
      ],
      mid: [
        "기본은 하지만 특별히 기억에 남는 맛까지는 아니었습니다.",
        "가까우면 한 번쯤 갈 만하고, 멀리서 찾아갈 정도는 아닌 느낌이에요.",
        "양과 가격은 무난한데 맛은 취향을 조금 탈 수 있겠습니다.",
      ],
      low: [
        "간이 제 입맛에는 살짝 아쉬웠고 재방문은 고민됩니다.",
        "편하게 먹을 수는 있지만 가격 대비 만족도는 높지 않았어요.",
        "기대한 것보다는 평범해서 근처가 아니면 다시 찾진 않을 것 같습니다.",
      ],
    },
    meat: {
      high: [
        "고기 질이 괜찮고 잡내가 거의 없어서 굽는 내내 만족스러웠습니다.",
        "사이드와 반찬까지 같이 먹으니 구성이 꽤 좋았어요.",
        "가격은 조금 있어도 고기 상태를 생각하면 납득되는 편입니다.",
      ],
      good: [
        "고기는 무난하게 괜찮고 반찬 구성도 크게 빠지는 느낌은 없었습니다.",
        "여럿이 가기 좋은 분위기고 주문 흐름도 답답하지 않았어요.",
        "구이류 기준으로 맛은 안정적이고 양도 예상 범위였습니다.",
      ],
      mid: [
        "고기 자체는 나쁘지 않은데 가격이나 분위기에서 살짝 애매했습니다.",
        "같이 간 사람들은 무난하다고 했고, 저는 재방문은 상황에 따라 다를 듯합니다.",
        "반찬은 괜찮았지만 메인 메뉴 임팩트는 조금 약했습니다.",
      ],
      low: [
        "고기 질이나 굽는 상태가 기대만큼은 아니라 아쉬움이 남았습니다.",
        "가격을 생각하면 만족도가 높진 않았고 재방문은 고민됩니다.",
        "사이드는 괜찮았지만 메인에서 확 끌리는 느낌은 부족했습니다.",
      ],
    },
    japanese: {
      high: [
        "재료가 깔끔하고 소스가 과하지 않아서 끝까지 물리지 않았습니다.",
        "혼밥으로도 부담 없고 메뉴 마감이 생각보다 괜찮았어요.",
        "밥이나 면 상태가 안정적이라 다음에도 같은 메뉴를 시킬 것 같습니다.",
      ],
      good: [
        "메뉴 구성이 단순해서 고르기 쉽고 맛도 무난하게 좋았습니다.",
        "간이 강하지 않아서 가볍게 먹기 괜찮았어요.",
        "대기만 길지 않으면 점심 후보로 넣기 좋겠습니다.",
      ],
      mid: [
        "맛은 무난했지만 기대했던 만큼의 인상은 아니었습니다.",
        "가까우면 들를 만하고, 일부러 찾아갈 정도는 아닌 느낌입니다.",
        "소스나 밥 상태는 괜찮았는데 가격 대비로는 조금 애매했어요.",
      ],
      low: [
        "메뉴가 나쁘진 않았지만 전체적으로 평범해서 아쉬웠습니다.",
        "제 입맛에는 간이나 식감이 조금 맞지 않았어요.",
        "근처에서 급하게 먹기엔 괜찮지만 재방문은 고민됩니다.",
      ],
    },
  };

  const fallback = {
    high: [
      "메뉴 완성도가 생각보다 좋아서 다음에도 후보에 넣을 만했습니다.",
      "맛과 분위기 모두 크게 거슬리는 점이 없어서 만족스러웠습니다.",
      "같이 간 사람들도 반응이 좋아서 재방문 의사가 있습니다.",
    ],
    good: [
      "전반적으로 무난하고 가격대도 예상 범위라 부담이 적었습니다.",
      "특별히 튀진 않지만 실패 확률이 낮은 선택지에 가깝습니다.",
      "메뉴 구성이 어렵지 않고 식사 흐름도 편했습니다.",
    ],
    mid: [
      "장점과 아쉬운 점이 같이 있어서 기대치를 조절하면 괜찮습니다.",
      "가까운 동선이면 들를 만하지만 강하게 추천할 정도는 아닙니다.",
      "맛은 기본을 하지만 분위기나 가격에서 취향이 갈릴 수 있어요.",
    ],
    low: [
      "기대했던 것보다는 평범했고 재방문은 조금 고민됩니다.",
      "접근성은 괜찮지만 맛이나 가격 만족도는 아쉬운 편이었습니다.",
      "급하게 먹기에는 가능하지만 일부러 찾아갈 정도는 아니었습니다.",
    ],
  };

  return pick((byCategory[category] || fallback)[band] || fallback[band], seed);
}

function operationNote(band, seed) {
  const highOrGood = [
    "직원 응대는 과하지 않고 편했습니다.",
    "피크 시간에는 대기가 있을 수 있지만 회전은 나쁘지 않아 보였어요.",
    "주차는 시간대에 따라 다를 것 같아서 미리 확인하면 좋겠습니다.",
    "테이블 간격은 넓진 않아도 식사하는 데 크게 불편하진 않았습니다.",
    "메뉴 나오는 속도도 답답하지 않았고 정리도 깔끔했습니다.",
  ];
  const midOrLow = [
    "다만 피크 시간에는 기다림이 길어질 수 있겠다는 생각이 들었습니다.",
    "좌석이나 동선은 조금 좁게 느껴질 수 있습니다.",
    "주차나 대기까지 생각하면 방문 시간대를 잘 잡는 게 좋겠습니다.",
    "응대는 무난했지만 매장이 바쁠 때는 조금 정신없을 수 있어요.",
    "가격대는 사람마다 체감이 갈릴 것 같습니다.",
  ];
  return pick(band === "high" || band === "good" ? highOrGood : midOrLow, seed);
}

function conclusion(band, seed) {
  const choices = {
    high: [
      "근처에 오면 다시 들를 생각입니다.",
      "다음에는 다른 메뉴도 한번 먹어보고 싶어요.",
      "약속 장소 후보로 다시 넣어둘 만했습니다.",
    ],
    good: [
      "가까운 동선이면 재방문할 것 같습니다.",
      "큰 기대보다는 무난한 선택지로 보면 좋겠습니다.",
      "다음에 근처에서 메뉴 고민되면 다시 생각날 정도입니다.",
    ],
    mid: [
      "재방문은 그날 상황과 동선에 따라 달라질 것 같습니다.",
      "가까운 곳에서 바로 먹을 곳 찾을 때는 후보가 될 수 있어요.",
      "호불호가 있을 수 있어서 취향 맞는 사람에게 더 괜찮겠습니다.",
    ],
    low: [
      "다음에는 다른 메뉴를 먹어보거나 다른 곳도 비교해볼 것 같습니다.",
      "가까워서 들를 수는 있지만 우선순위가 높진 않습니다.",
      "한 번 경험한 정도로 만족하고 재방문은 조금 더 생각해보려고요.",
    ],
  };
  return pick(choices[band], seed);
}

function buildReview(row) {
  const score = Number(row.score || 74);
  const band = scoreBand(score);
  const category = row.category_key;
  const menu = menuFor(row);
  const detailCategory = effectiveCategory(category, menu);
  const location = locationLabel(row);
  const categoryLabel = CATEGORY_LABELS[category] || "식사";
  const seed = `${row.id}:${row.restaurant_id}:${score}`;
  const contexts = [
    "평일 점심에",
    "저녁 약속 전에",
    "주말에 근처 들른 김에",
    "혼자 밥 먹을 곳 찾다가",
    "친구랑 메뉴 고르다가",
    "근처에서 일정 끝나고",
    "사람 많지 않은 시간에",
  ];
  const openerTemplates = [
    `${pick(contexts, `${seed}:ctx`)} ${row.restaurant_name}에 다녀왔습니다.`,
    `${location} 쪽에서 ${visitNeed(category)} 찾다가 ${row.restaurant_name}에 들렀어요.`,
    `${topicPhrase(row.restaurant_name)} 예전부터 저장해뒀다가 이번에 방문했습니다.`,
    `${location} 근처 약속이 있어서 ${objectPhrase(row.restaurant_name)} 골라봤습니다.`,
  ];
  const menuTemplates = [
    `${menu} 기준으로 보면 ${categoryDetail(detailCategory, band, `${seed}:detail1`)}`,
    `${objectPhrase(menu)} 먹어봤는데 ${categoryDetail(detailCategory, band, `${seed}:detail2`)}`,
    `대표 메뉴로 보이는 ${objectPhrase(menu)} 시켰고, ${categoryDetail(detailCategory, band, `${seed}:detail3`)}`,
    `${menu} 쪽이 궁금해서 주문했는데 ${categoryDetail(detailCategory, band, `${seed}:detail4`)}`,
  ];
  const priceNotes = {
    high: [
      "가격은 아주 싸진 않지만 만족도까지 생각하면 납득됐습니다.",
      "양과 맛을 같이 보면 돈이 아깝다는 느낌은 적었어요.",
      "요즘 물가 기준으로는 크게 부담스럽지 않은 편입니다.",
    ],
    good: [
      "가격은 예상한 정도였고 양도 크게 부족하진 않았습니다.",
      "가성비가 엄청나다기보다는 안정적인 선택지에 가깝습니다.",
      "메뉴 가격과 만족도는 대체로 균형이 맞는 편이었어요.",
    ],
    mid: [
      "가격을 생각하면 조금 더 기대하게 되는 부분은 있었습니다.",
      "양은 무난했지만 가격 대비로는 사람마다 다르게 느낄 수 있겠어요.",
      "주문한 메뉴 기준으로는 만족도가 중간 정도였습니다.",
    ],
    low: [
      "가격까지 생각하면 아쉬움이 조금 더 크게 느껴졌습니다.",
      "양이나 맛 중 하나는 기대보다 약해서 만족도가 높진 않았어요.",
      "비슷한 가격대의 다른 선택지도 비교해볼 것 같습니다.",
    ],
  };

  return [
    pick(openerTemplates, `${seed}:open`),
    pick(menuTemplates, `${seed}:menu`),
    pick(priceNotes[band], `${seed}:price`),
    operationNote(band, `${seed}:op`),
    conclusion(band, `${seed}:end`),
  ].join(" ");
}

fs.mkdirSync(outputDir, { recursive: true });

const rows = queryJson(`
  WITH user_restaurant_best AS (
    SELECT ul.user_id, lr.restaurant_id, MAX(lr.auto_score) AS best_auto_score
    FROM user_lists ul
    JOIN list_restaurants lr ON lr.list_id = ul.id
    GROUP BY ul.user_id, lr.restaurant_id
  )
  SELECT v.id::int,
         v.user_id::int,
         v.restaurant_id::int,
         r.name AS restaurant_name,
         r.region_name,
         COALESCE(r.region_town_name, '') AS region_town_name,
         COALESCE(r.category_name, '') AS category_name,
         COALESCE(r.primary_category_name, '') AS primary_category_name,
         COALESCE(urb.best_auto_score, 74)::float AS score,
         COALESCE(json_agg(mi.menu_name ORDER BY mi.display_order, mi.id) FILTER (WHERE mi.menu_name IS NOT NULL), '[]'::json) AS menus
  FROM reviews v
  JOIN restaurants r ON r.id = v.restaurant_id
  LEFT JOIN user_restaurant_best urb ON urb.user_id = v.user_id AND urb.restaurant_id = v.restaurant_id
  LEFT JOIN restaurant_menu_items mi ON mi.restaurant_id = r.id
  WHERE v.is_deleted = false
  GROUP BY v.id, r.id, urb.best_auto_score
  ORDER BY v.id
`);

const updates = [];
const samples = [];
const lengthStats = [];
for (const row of rows) {
  row.category_key = categoryKey(row);
  const content = buildReview(row);
  lengthStats.push(content.length);
  if (samples.length < 20) {
    samples.push({
      id: row.id,
      restaurant: row.restaurant_name,
      region: row.region_name,
      category: row.primary_category_name || row.category_name,
      score: row.score,
      content,
    });
  }
  updates.push(`UPDATE reviews SET content = ${sqlQuote(content)}, updated_at = ${sqlQuote(now)} WHERE id = ${row.id};`);
}

const sql = [
  "BEGIN;",
  "TRUNCATE TABLE review_summaries RESTART IDENTITY;",
  ...updates,
  "COMMIT;",
].join("\n") + "\n";

const sqlPath = path.join(outputDir, "refresh_review_contents_and_delete_summaries.sql");
const reportPath = path.join(outputDir, "refresh_review_contents_and_delete_summaries_report.json");
fs.writeFileSync(sqlPath, sql, "utf8");

const sortedLengths = [...lengthStats].sort((a, b) => a - b);
const percentile = (p) => sortedLengths[Math.min(sortedLengths.length - 1, Math.floor((sortedLengths.length - 1) * p))] || 0;
const report = {
  generatedAt: new Date().toISOString(),
  applied: false,
  reviewCount: rows.length,
  deleteReviewSummaries: true,
  length: {
    min: sortedLengths[0] || 0,
    p50: percentile(0.5),
    p90: percentile(0.9),
    max: sortedLengths[sortedLengths.length - 1] || 0,
    avg: Number((lengthStats.reduce((sum, value) => sum + value, 0) / Math.max(1, lengthStats.length)).toFixed(1)),
  },
  samples,
  sqlPath,
};

if (APPLY) {
  psql(sql);
  report.applied = true;
  report.after = queryJson(`
    SELECT
      (SELECT COUNT(*)::int FROM review_summaries) AS review_summaries,
      (SELECT COUNT(*)::int FROM reviews) AS reviews,
      (SELECT COUNT(DISTINCT content)::int FROM reviews) AS distinct_contents,
      (SELECT ROUND(AVG(length(content))::numeric, 1)::float FROM reviews) AS avg_review_length,
      (SELECT MIN(length(content))::int FROM reviews) AS min_review_length,
      (SELECT MAX(length(content))::int FROM reviews) AS max_review_length
  `)[0];
}

fs.writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
console.log(JSON.stringify({
  applied: report.applied,
  reviewCount: report.reviewCount,
  length: report.length,
  after: report.after || null,
  sqlPath,
  reportPath,
}, null, 2));
