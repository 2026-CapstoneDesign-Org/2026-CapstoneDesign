const fs = require("fs");
const path = require("path");
const { spawnSync } = require("child_process");

const rootDir = path.resolve(__dirname, "..", "..");
const outputDir = path.resolve(process.env.OUTPUT_DIR || path.join(rootDir, "tmp", "mock_seed_import"));
const pcmapJsonlPath = path.resolve(
  process.env.PCMAP_JSONL || path.join(rootDir, "..", "Naver_pcmap_api", "Naver_seed", "output", "diningcode-pcmap-enriched.jsonl")
);

const DB_CONTAINER = process.env.DB_CONTAINER || "postgres-dev";
const DB_USER = process.env.DB_USER || "dev_user";
const DB_NAME = process.env.DB_NAME || "dev_db";
const APPLY = process.argv.includes("--apply");

const TARGET_REGIONS = [
  { region: "용인시 수지구", lists: 10, note: "yongin-nearby" },
  { region: "용인시 기흥구", lists: 10, note: "yongin-nearby" },
  { region: "서울특별시 성북구", lists: 20, note: "major-city-empty" },
  { region: "서울특별시 마포구", lists: 20, note: "major-city-empty" },
  { region: "서울특별시 강남구", lists: 20, note: "major-city-empty" },
  { region: "서울특별시 송파구", lists: 20, note: "major-city-empty" },
  { region: "서울특별시 종로구", lists: 20, note: "major-city-empty" },
  { region: "서울특별시 성동구", lists: 20, note: "major-city-empty" },
  { region: "부산광역시 부산진구", lists: 12, note: "metro-support" },
  { region: "대구광역시 중구", lists: 12, note: "metro-support" },
  { region: "광주광역시 서구", lists: 12, note: "metro-support" },
  { region: "인천광역시 서구", lists: 12, note: "metro-support" },
];

const CATEGORY_LABELS = {
  korean: "한식",
  japanese: "일식",
  western: "양식",
  chinese: "중식",
  cafe: "카페",
  bakery: "디저트",
  meat: "고기",
  street: "분식",
  bar: "술집",
  noodle: "면요리",
  seafood: "해산물",
  fast: "간단한 식사",
  other: "맛집",
};

const REGION_TITLES = {
  "용인시 수지구": "수지구",
  "용인시 기흥구": "기흥구",
  "서울특별시 성북구": "성북구",
  "서울특별시 마포구": "마포구",
  "서울특별시 강남구": "강남구",
  "서울특별시 송파구": "송파구",
  "서울특별시 종로구": "종로구",
  "서울특별시 성동구": "성동구",
  "부산광역시 부산진구": "부산진구",
  "대구광역시 중구": "대구 중구",
  "광주광역시 서구": "광주 서구",
  "인천광역시 서구": "인천 서구",
};

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

function round1(value) {
  return Math.round(value * 10) / 10;
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function sqlQuote(value) {
  if (value === null || value === undefined) return "NULL";
  return `'${String(value).replace(/'/g, "''")}'`;
}

function sqlBool(value) {
  return value ? "true" : "false";
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

function parseCount(value) {
  if (value === null || value === undefined || value === "") return 0;
  const numeric = String(value).replace(/,/g, "").trim();
  const parsed = Number(numeric);
  return Number.isFinite(parsed) ? parsed : 0;
}

function loadPcmapSignals(filePath) {
  const signals = new Map();
  if (!fs.existsSync(filePath)) {
    return signals;
  }

  const lines = fs.readFileSync(filePath, "utf8").split(/\r?\n/);
  for (const line of lines) {
    if (!line.trim()) continue;
    let row;
    try {
      row = JSON.parse(line);
    } catch {
      continue;
    }
    if (row.status !== "MATCHED") continue;
    const detail = row.pcmap_detail || {};
    const candidate = row.pcmap_candidate || {};
    const placeId = String(detail.placeId || candidate.placeId || "").trim();
    if (!placeId) continue;
    const visitorReviewCount = Math.max(
      parseCount(candidate.visitorReviewCount),
      parseCount(candidate.raw?.visitorReviewCount),
      parseCount(detail.visitorReviewsTotal)
    );
    const blogReviewCount = Math.max(
      parseCount(candidate.blogCafeReviewCount),
      parseCount(candidate.raw?.blogCafeReviewCount),
      parseCount(detail.blogCafeReviewCount)
    );
    const imageCount = Math.max(parseCount(candidate.imageCount), parseCount(candidate.raw?.imageCount));
    const menuCount = Array.isArray(detail.menus) ? detail.menus.length : 0;
    const mentionCount = parseCount(row.mention_count);
    const sourceCount = parseCount(row.source_count);
    const matchScore = parseCount(row.match_score);
    const signalScore =
      Math.log1p(visitorReviewCount) * 14 +
      Math.log1p(blogReviewCount) * 8 +
      Math.min(imageCount, 50) * 0.35 +
      Math.min(menuCount, 12) * 1.2 +
      sourceCount * 2 +
      mentionCount * 0.8 +
      matchScore * 0.08;

    const current = signals.get(placeId);
    if (!current || signalScore > current.signalScore) {
      signals.set(placeId, {
        placeId,
        visitorReviewCount,
        blogReviewCount,
        imageCount,
        menuCount,
        mentionCount,
        sourceCount,
        matchScore,
        signalScore: round1(signalScore),
      });
    }
  }
  return signals;
}

function categoryKey(row) {
  const text = `${row.category_name || ""} ${row.primary_category_name || ""} ${row.name || ""}`;
  if (/카페|디저트|커피|브런치/.test(text)) return "cafe";
  if (/베이커리|빵|케이크|도넛/.test(text)) return "bakery";
  if (/고기|육류|삼겹|갈비|구이|소고기|돼지/.test(text)) return "meat";
  if (/중식|중국|짬뽕|짜장|마라/.test(text)) return "chinese";
  if (/일식|초밥|스시|돈가스|돈까스|라멘|우동|소바|카츠/.test(text)) return "japanese";
  if (/양식|파스타|피자|스테이크|버거/.test(text)) return "western";
  if (/분식|김밥|떡볶|튀김/.test(text)) return "street";
  if (/술집|주점|맥주|호프|이자카야|포차|와인/.test(text)) return "bar";
  if (/냉면|국수|칼국수|막국수|면/.test(text)) return "noodle";
  if (/회|해물|생선|수산|조개/.test(text)) return "seafood";
  if (/치킨|패스트|샌드위치|토스트/.test(text)) return "fast";
  if (/한식|국밥|찌개|백반|곰탕|순대|감자탕|밥/.test(text)) return "korean";
  return "other";
}

function tierBaseByRank(rankIndex, total, signal) {
  const ratio = total <= 1 ? 0 : rankIndex / (total - 1);
  const strongExternal = (signal.visitorReviewCount || 0) >= 500 || (signal.blogReviewCount || 0) >= 80;
  if (ratio <= 0.1) return strongExternal ? 79 : 77;
  if (ratio <= 0.3) return 74;
  if (ratio <= 0.72) return 70;
  if (ratio <= 0.9) return 60;
  return 51;
}

function scoreFor(row, listCategory, listIndex, itemIndex) {
  const key = row.categoryKey;
  const fit = key === listCategory ? 2.2 : key === "other" ? -1.5 : -0.6;
  const signalBonus = Math.min(2.0, Math.log1p(row.signal.visitorReviewCount || 0) * 0.15);
  const contrastPenalty = itemIndex >= 5 && fit < 0 ? 4.5 : 0;
  const jitter = ((hash(`score:${row.id}:${listIndex}:${itemIndex}`) % 45) - 22) / 10;
  const target = clamp(row.baseAuto + fit + signalBonus - contrastPenalty + jitter, 34, 96);
  const center = target / 10;

  let taste = center + (["korean", "japanese", "chinese", "meat", "noodle", "seafood"].includes(key) ? 0.25 : 0.05);
  let value = center + (["street", "fast", "korean", "noodle"].includes(key) ? 0.25 : -0.05);
  let mood = center + (["cafe", "bakery", "bar", "western"].includes(key) ? 0.35 : -0.05);

  taste += ((hash(`taste:${row.id}:${listIndex}`) % 21) - 10) / 25;
  value += ((hash(`value:${row.id}:${itemIndex}`) % 21) - 10) / 25;
  mood += ((hash(`mood:${row.id}:${listIndex}:${itemIndex}`) % 21) - 10) / 25;

  taste = round1(clamp(taste, 2.0, 9.9));
  value = round1(clamp(value, 2.0, 9.8));
  mood = round1(clamp(mood, 2.0, 9.8));
  const auto = round1((taste * 0.6 + value * 0.2 + mood * 0.2) * 10);
  return { taste, value, mood, auto };
}

function normalizeMenuName(value) {
  const text = String(value || "").replace(/\s+/g, " ").trim();
  if (!text) return "";
  if (/[0-9]{2,}|원|세트|대관|예약|쿠폰|이용권|개월|무료/.test(text)) return "";
  return text.length > 24 ? text.slice(0, 24) : text;
}

function reviewMenu(row, seed) {
  const menus = Array.isArray(row.menus) ? row.menus.map(normalizeMenuName).filter(Boolean) : [];
  if (menus.length > 0) return pick(menus, seed);
  return CATEGORY_LABELS[row.categoryKey] || "메뉴";
}

function josaTopic(value) {
  return /[가-힣]$/.test(value) ? `${value}은` : `${value}는`;
}

function josaSubject(value) {
  return /[가-힣]$/.test(value) ? `${value}이` : `${value}가`;
}

function reviewContent(row, score, seed) {
  const menu = reviewMenu(row, seed);
  const town = row.region_town_name || REGION_TITLES[row.region_name] || row.region_name;
  const categoryLabel = CATEGORY_LABELS[row.categoryKey] || "식사";
  const highNotes = {
    cafe: ["음료가 너무 달지 않고 공간도 차분해서 머물기 좋았습니다.", "디저트랑 커피 조합이 자연스러워서 다시 생각날 정도였습니다."],
    bakery: ["빵 식감이 괜찮고 포장해서 먹어도 맛이 크게 죽지 않았습니다.", "달기만 한 느낌이 아니라 커피랑 같이 먹기 좋았습니다."],
    meat: ["고기 굽는 상태가 안정적이고 같이 나온 반찬도 과하지 않았습니다.", "가격은 조금 있어도 고기 질 기준으로는 납득됐습니다."],
    bar: ["안주가 가볍지 않고 이야기하기 좋은 분위기라 약속 잡기 좋았습니다.", "술 종류와 안주 구성이 무난해서 2차로 가기 편했습니다."],
    street: ["간이 세지 않고 회전이 빨라서 가볍게 먹기 좋았습니다.", "양이 생각보다 괜찮고 혼자 들러도 부담이 적었습니다."],
  };
  const neutralNotes = [
    "기대치를 너무 높게 잡지 않으면 무난하게 들르기 좋습니다.",
    "가까운 동선에 있으면 후보로 넣을 만한 정도였습니다.",
    "맛은 안정적인 편이고 대기나 가격은 시간대에 따라 체감이 갈릴 것 같습니다.",
  ];
  const lowNotes = [
    "나쁘진 않았지만 일부 메뉴는 가격 대비 아쉬움이 남았습니다.",
    "근처라 편한 점은 있지만 일부러 찾아갈 정도인지는 조금 고민됩니다.",
    "기본은 하는데 맛이나 분위기 중 한쪽은 기대보다 평범했습니다.",
  ];
  const operation = pick(
    [
      "피크 시간에는 조금 기다릴 수 있겠어요.",
      "직원 응대는 과하지 않고 편했습니다.",
      "주차는 시간대에 따라 체감이 달라질 것 같습니다.",
      "메뉴 고르는 데 오래 걸리지 않아 좋았습니다.",
      "같이 간 사람들도 큰 불만 없이 먹었습니다.",
    ],
    `${seed}:op`
  );

  if (score >= 84) {
    const note = pick(highNotes[row.categoryKey] || [`${categoryLabel} 기준으로 맛과 동선이 잘 맞았습니다.`], seed);
    return `${town}에서 ${row.name} 다녀왔습니다. ${menu} 기준으로 만족도가 높았고 ${note} ${operation}`;
  }
  if (score >= 68) {
    return `${row.name}은 ${categoryLabel} 생각날 때 가볍게 넣어볼 만했습니다. ${josaTopic(menu)} 무난했고 ${pick(neutralNotes, seed)} ${operation}`;
  }
  return `${town} 근처라 ${row.name}에 들렀는데 ${josaSubject(menu)} 기대만큼 특별하진 않았습니다. ${pick(lowNotes, seed)} ${operation}`;
}

function listTitle(regionName, category, index) {
  const region = REGION_TITLES[regionName] || regionName;
  const label = CATEGORY_LABELS[category] || "맛집";
  const templates = [
    `${region} ${label} 다시 볼 곳`,
    `${region}에서 약속 잡을 때 보는 곳`,
    `${region} ${label} 후보 메모`,
    `${region} 점심과 저녁 후보`,
    `${label} 생각날 때 ${region}`,
    `${region}에서 부담 없이 고를 곳`,
  ];
  return templates[hash(`title:${regionName}:${category}:${index}`) % templates.length];
}

function listDescription(regionName, category, index) {
  const region = REGION_TITLES[regionName] || regionName;
  const label = CATEGORY_LABELS[category] || "메뉴";
  const descriptions = [
    `${region}에서 바로 고르기 쉽도록 메뉴, 이동 동선, 리뷰 수를 같이 보고 남긴 목록입니다.`,
    `${label}이 당길 때 가격대와 분위기를 같이 보려고 따로 모아둔 곳들입니다.`,
    `후기 수가 많은 곳만 넣지 않고, 사진과 메뉴가 괜찮아 보이는 곳도 같이 섞었습니다.`,
    `약속 잡기 전에 후보를 줄이려고 저장해둔 개인 메모에 가깝습니다.`,
    `무난한 곳과 조금 더 찾아갈 만한 곳을 같이 넣어 비교하기 좋게 만들었습니다.`,
  ];
  return descriptions[hash(`desc:${regionName}:${category}:${index}`) % descriptions.length];
}

function buildInsert(table, columns, rows, conflictClause = "") {
  if (rows.length === 0) return "";
  const chunks = [];
  for (let start = 0; start < rows.length; start += 400) {
    const slice = rows.slice(start, start + 400);
    chunks.push(
      `INSERT INTO ${table} (${columns.join(", ")}) VALUES\n` +
        slice.map((row) => `  (${columns.map((column) => row[column]).join(", ")})`).join(",\n") +
        `${conflictClause};`
    );
  }
  return chunks.join("\n");
}

function rowsToMap(rows, key) {
  const map = new Map();
  for (const row of rows) map.set(row[key], row);
  return map;
}

fs.mkdirSync(outputDir, { recursive: true });

const targetRegionNames = TARGET_REGIONS.map((target) => target.region);
const targetRegionSql = targetRegionNames.map(sqlQuote).join(", ");

const beforeStats = queryJson(`
  SELECT r.region_name,
         COUNT(DISTINCT r.id)::int AS restaurants,
         COUNT(lr.id)::int AS list_rows,
         COUNT(v.id)::int AS reviews
  FROM restaurants r
  LEFT JOIN list_restaurants lr ON lr.restaurant_id = r.id
  LEFT JOIN reviews v ON v.restaurant_id = r.id
  WHERE r.region_name IN (${targetRegionSql})
  GROUP BY r.region_name
  ORDER BY r.region_name
`);

const users = queryJson(`
  SELECT id::int, nickname
  FROM users
  WHERE is_deleted = false AND is_hidden = false
  ORDER BY id
`);

const restaurants = queryJson(`
  SELECT r.id::int,
         r.name,
         r.region_name,
         COALESCE(r.region_town_name, '') AS region_town_name,
         COALESCE(r.category_name, '') AS category_name,
         COALESCE(r.primary_category_name, '') AS primary_category_name,
         COALESCE(r.pcmap_place_id, '') AS pcmap_place_id,
         COALESCE(COUNT(DISTINCT mi.id), 0)::int AS db_menu_count,
         COALESCE(COUNT(DISTINCT rp.id), 0)::int AS db_photo_count,
         COALESCE(json_agg(DISTINCT mi.menu_name) FILTER (WHERE mi.menu_name IS NOT NULL), '[]'::json) AS menus
  FROM restaurants r
  LEFT JOIN restaurant_menu_items mi ON mi.restaurant_id = r.id
  LEFT JOIN restaurant_photos rp ON rp.restaurant_id = r.id
  WHERE r.region_name IN (${targetRegionSql})
    AND r.is_deleted = false
    AND r.is_hidden = false
  GROUP BY r.id
  ORDER BY r.region_name, r.id
`);

const maxIds = queryJson(`
  SELECT
    (SELECT COALESCE(MAX(id), 0)::int FROM user_lists) AS user_lists,
    (SELECT COALESCE(MAX(id), 0)::int FROM list_restaurants) AS list_restaurants,
    (SELECT COALESCE(MAX(id), 0)::int FROM reviews) AS reviews,
    (SELECT COALESCE(MAX(id), 0)::int FROM review_votes) AS review_votes,
    (SELECT COALESCE(MAX(id), 0)::int FROM list_likes) AS list_likes
`)[0];

if (users.length === 0) {
  throw new Error("No visible users are available for regional supplement generation.");
}

const pcmapSignals = loadPcmapSignals(pcmapJsonlPath);
const byRegion = new Map();
for (const row of restaurants) {
  const signal = pcmapSignals.get(String(row.pcmap_place_id)) || {
    placeId: row.pcmap_place_id,
    visitorReviewCount: 0,
    blogReviewCount: 0,
    imageCount: row.db_photo_count || 0,
    menuCount: row.db_menu_count || 0,
    mentionCount: 0,
    sourceCount: 0,
    matchScore: 0,
    signalScore: round1((row.db_photo_count || 0) * 0.8 + (row.db_menu_count || 0) * 1.2),
  };
  const enriched = {
    ...row,
    categoryKey: categoryKey(row),
    signal,
  };
  if (!byRegion.has(row.region_name)) byRegion.set(row.region_name, []);
  byRegion.get(row.region_name).push(enriched);
}

for (const [region, rows] of byRegion.entries()) {
  rows.sort((left, right) => {
    if (right.signal.signalScore !== left.signal.signalScore) {
      return right.signal.signalScore - left.signal.signalScore;
    }
    return left.id - right.id;
  });
  rows.forEach((row, index) => {
    row.baseAuto = tierBaseByRank(index, rows.length, row.signal);
  });
}

let nextListId = maxIds.user_lists + 1;
let nextListRestaurantId = maxIds.list_restaurants + 1;
let nextReviewId = maxIds.reviews + 1;
let nextReviewVoteId = maxIds.review_votes + 1;
let nextListLikeId = maxIds.list_likes + 1;

const now = "2026-05-31T10:00:00+09:00";
const userLists = [];
const listRestaurants = [];
const reviews = [];
const reviewVotes = [];
const listLikes = [];
const regionReports = [];
const listUserRestaurantSeen = new Set();

for (let regionIndex = 0; regionIndex < TARGET_REGIONS.length; regionIndex += 1) {
  const target = TARGET_REGIONS[regionIndex];
  const regionRestaurants = byRegion.get(target.region) || [];
  if (regionRestaurants.length < 6) {
    regionReports.push({
      regionName: target.region,
      skipped: true,
      reason: `only ${regionRestaurants.length} restaurants`,
    });
    continue;
  }

  const categoryCounts = new Map();
  for (const row of regionRestaurants) {
    categoryCounts.set(row.categoryKey, (categoryCounts.get(row.categoryKey) || 0) + 1);
  }
  const categories = [...categoryCounts.entries()]
    .sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]))
    .map(([key]) => key)
    .filter((key) => key !== "other");
  if (!categories.includes("korean")) categories.push("korean");
  if (!categories.includes("cafe")) categories.push("cafe");
  if (categories.length === 0) categories.push("other");

  let createdLists = 0;
  let createdRows = 0;
  let createdReviews = 0;
  const usedRestaurants = new Set();

  for (let listIndex = 0; listIndex < target.lists; listIndex += 1) {
    const category = categories[listIndex % categories.length] || "other";
    const user = users[(regionIndex * 47 + listIndex * 13 + 31) % users.length];
    const listId = nextListId++;
    const isPublic = hash(`public:${target.region}:${listIndex}`) % 100 < 72;
    userLists.push({
      id: listId,
      user_id: user.id,
      title: listTitle(target.region, category, listIndex),
      description: listDescription(target.region, category, listIndex),
      region_name: target.region,
      is_public: isPublic,
      is_representative: false,
      is_hidden: false,
      is_deleted: false,
      created_at: now,
      updated_at: now,
    });
    createdLists += 1;

    const matching = regionRestaurants
      .filter((row) => row.categoryKey === category)
      .sort((left, right) => right.baseAuto - left.baseAuto || hash(`match:${listId}:${left.id}`) - hash(`match:${listId}:${right.id}`));
    const fallback = regionRestaurants
      .filter((row) => row.categoryKey !== category)
      .sort((left, right) => right.signal.signalScore - left.signal.signalScore || hash(`fallback:${listId}:${left.id}`) - hash(`fallback:${listId}:${right.id}`));
    const wantedSize = Math.min(regionRestaurants.length, 6 + (hash(`size:${target.region}:${listIndex}`) % 3));
    const selected = [];
    for (const candidate of [...matching, ...fallback]) {
      if (selected.some((row) => row.id === candidate.id)) continue;
      selected.push(candidate);
      if (selected.length >= wantedSize) break;
    }

    selected.forEach((restaurant, itemIndex) => {
      const score = scoreFor(restaurant, category, listIndex, itemIndex);
      const rowId = nextListRestaurantId++;
      listRestaurants.push({
        id: rowId,
        list_id: listId,
        restaurant_id: restaurant.id,
        taste_score: score.taste,
        value_score: score.value,
        mood_score: score.mood,
        auto_score: score.auto,
        created_at: now,
        updated_at: now,
      });
      createdRows += 1;
      usedRestaurants.add(restaurant.id);

      const reviewKey = `${user.id}:${restaurant.id}`;
      const shouldReview = (
        itemIndex === 0 ||
        (itemIndex === 1 && hash(`review-second:${listId}`) % 100 < 45) ||
        (itemIndex < 4 && score.auto >= 88) ||
        (itemIndex < 4 && score.auto < 58)
      ) && !listUserRestaurantSeen.has(reviewKey);
      listUserRestaurantSeen.add(reviewKey);
      if (shouldReview) {
        const reviewId = nextReviewId++;
        const content = reviewContent(restaurant, score.auto, `${listId}:${restaurant.id}:${score.auto}`);
        reviews.push({
          id: reviewId,
          user_id: user.id,
          restaurant_id: restaurant.id,
          content,
          is_hidden: false,
          is_deleted: false,
          created_at: now,
          updated_at: now,
        });
        createdReviews += 1;

        const voteCount = score.auto >= 84 ? 3 : score.auto >= 68 ? 2 : 1;
        for (let voteIndex = 0; voteIndex < voteCount; voteIndex += 1) {
          const voter = users[(regionIndex * 53 + listIndex * 17 + itemIndex * 7 + voteIndex + 5) % users.length];
          if (voter.id === user.id) continue;
          reviewVotes.push({
            id: nextReviewVoteId++,
            user_id: voter.id,
            review_id: reviewId,
            vote_type: score.auto < 58 && hash(`dislike:${reviewId}:${voteIndex}`) % 100 < 35 ? "DISLIKE" : "LIKE",
            created_at: now,
          });
        }
      }
    });

    if (isPublic) {
      const likeCount = 2 + (hash(`likes:${target.region}:${listIndex}`) % 4);
      for (let likeIndex = 0; likeIndex < likeCount; likeIndex += 1) {
        const liker = users[(regionIndex * 41 + listIndex * 19 + likeIndex * 3 + 9) % users.length];
        if (liker.id === user.id) continue;
        listLikes.push({
          id: nextListLikeId++,
          user_id: liker.id,
          list_id: listId,
          created_at: now,
        });
      }
    }
  }

  regionReports.push({
    regionName: target.region,
    note: target.note,
    restaurantCount: regionRestaurants.length,
    createdLists,
    createdListRestaurants: createdRows,
    createdReviews,
    touchedRestaurants: usedRestaurants.size,
    categories: [...categoryCounts.entries()].sort((left, right) => right[1] - left[1]).slice(0, 6),
    topExternalSignals: regionRestaurants.slice(0, 5).map((row) => ({
      id: row.id,
      name: row.name,
      category: row.primary_category_name || row.category_name,
      visitorReviewCount: row.signal.visitorReviewCount,
      blogReviewCount: row.signal.blogReviewCount,
      signalScore: row.signal.signalScore,
      baseAuto: row.baseAuto,
    })),
  });
}

const sqlParts = [];
sqlParts.push("BEGIN;");
sqlParts.push(buildInsert(
  "user_lists",
  ["id", "user_id", "title", "description", "region_name", "is_public", "is_representative", "is_hidden", "is_deleted", "created_at", "updated_at"],
  userLists.map((row) => ({
    id: row.id,
    user_id: row.user_id,
    title: sqlQuote(row.title),
    description: sqlQuote(row.description),
    region_name: sqlQuote(row.region_name),
    is_public: sqlBool(row.is_public),
    is_representative: sqlBool(row.is_representative),
    is_hidden: sqlBool(row.is_hidden),
    is_deleted: sqlBool(row.is_deleted),
    created_at: sqlQuote(row.created_at),
    updated_at: sqlQuote(row.updated_at),
  }))
));
sqlParts.push(buildInsert(
  "list_restaurants",
  ["id", "list_id", "restaurant_id", "taste_score", "value_score", "mood_score", "auto_score", "created_at", "updated_at"],
  listRestaurants.map((row) => ({
    id: row.id,
    list_id: row.list_id,
    restaurant_id: row.restaurant_id,
    taste_score: row.taste_score,
    value_score: row.value_score,
    mood_score: row.mood_score,
    auto_score: row.auto_score,
    created_at: sqlQuote(row.created_at),
    updated_at: sqlQuote(row.updated_at),
  }))
));
sqlParts.push(buildInsert(
  "reviews",
  ["id", "user_id", "restaurant_id", "content", "is_hidden", "is_deleted", "created_at", "updated_at"],
  reviews.map((row) => ({
    id: row.id,
    user_id: row.user_id,
    restaurant_id: row.restaurant_id,
    content: sqlQuote(row.content),
    is_hidden: sqlBool(row.is_hidden),
    is_deleted: sqlBool(row.is_deleted),
    created_at: sqlQuote(row.created_at),
    updated_at: sqlQuote(row.updated_at),
  }))
));
sqlParts.push(buildInsert(
  "review_votes",
  ["id", "user_id", "review_id", "vote_type", "created_at"],
  reviewVotes.map((row) => ({
    id: row.id,
    user_id: row.user_id,
    review_id: row.review_id,
    vote_type: sqlQuote(row.vote_type),
    created_at: sqlQuote(row.created_at),
  })),
  " ON CONFLICT (user_id, review_id) DO NOTHING"
));
sqlParts.push(buildInsert(
  "list_likes",
  ["id", "user_id", "list_id", "created_at"],
  listLikes.map((row) => ({
    id: row.id,
    user_id: row.user_id,
    list_id: row.list_id,
    created_at: sqlQuote(row.created_at),
  })),
  " ON CONFLICT (user_id, list_id) DO NOTHING"
));
sqlParts.push("SELECT setval(pg_get_serial_sequence('user_lists','id'), (SELECT MAX(id) FROM user_lists));");
sqlParts.push("SELECT setval(pg_get_serial_sequence('list_restaurants','id'), (SELECT MAX(id) FROM list_restaurants));");
sqlParts.push("SELECT setval(pg_get_serial_sequence('reviews','id'), (SELECT MAX(id) FROM reviews));");
sqlParts.push("SELECT setval(pg_get_serial_sequence('review_votes','id'), (SELECT MAX(id) FROM review_votes));");
sqlParts.push("SELECT setval(pg_get_serial_sequence('list_likes','id'), (SELECT MAX(id) FROM list_likes));");
sqlParts.push("COMMIT;");

const sql = sqlParts.filter(Boolean).join("\n\n") + "\n";
const sqlPath = path.join(outputDir, "regional_engagement_supplement.sql");
const reportPath = path.join(outputDir, "regional_engagement_supplement_report.json");
fs.writeFileSync(sqlPath, sql, "utf8");

const report = {
  generatedAt: new Date().toISOString(),
  applied: false,
  policy: "supplement under-evaluated support regions while keeping 용인시 처인구 as the dominant main region",
  externalSignalSource: {
    type: "local_pcmap_seed",
    path: pcmapJsonlPath,
    fields: ["visitorReviewCount", "blogCafeReviewCount", "imageCount", "menuCount", "mention_count", "source_count"],
    copyrightNote: "No third-party review text is copied; synthetic reviews are generated from aggregate signals, actual menus, and categories.",
  },
  beforeStats,
  created: {
    userLists: userLists.length,
    listRestaurants: listRestaurants.length,
    reviews: reviews.length,
    reviewVotes: reviewVotes.length,
    listLikes: listLikes.length,
  },
  regionReports,
  sqlPath,
};

if (APPLY) {
  psql(sql);
  report.applied = true;
  report.afterStats = queryJson(`
    SELECT r.region_name,
           COUNT(DISTINCT r.id)::int AS restaurants,
           COUNT(lr.id)::int AS list_rows,
           COUNT(v.id)::int AS reviews
    FROM restaurants r
    LEFT JOIN list_restaurants lr ON lr.restaurant_id = r.id
    LEFT JOIN reviews v ON v.restaurant_id = r.id
    WHERE r.region_name IN (${targetRegionSql})
    GROUP BY r.region_name
    ORDER BY r.region_name
  `);
}

fs.writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
console.log(JSON.stringify({
  applied: report.applied,
  created: report.created,
  skippedRegions: regionReports.filter((row) => row.skipped).map((row) => row.regionName),
  sqlPath,
  reportPath,
}, null, 2));
