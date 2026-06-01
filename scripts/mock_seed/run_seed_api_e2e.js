#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const baseUrl = process.env.E2E_BASE_URL || "http://localhost:18080";
const outputDir = path.resolve(process.env.E2E_OUTPUT_DIR || "tmp/e2e_api_run");
const keyPath = path.resolve(process.env.APP_KEY_YML || "Capstone/src/main/resources/application-key.yml");

function readJwtSecret() {
  if (process.env.JWT_SECRET) return process.env.JWT_SECRET;
  const keyYaml = fs.readFileSync(keyPath, "utf8");
  const secret = keyYaml.match(/jwt:\s*\n\s+secret:\s*([^\r\n]+)/)?.[1]?.trim();
  if (!secret) throw new Error("jwt.secret not found");
  return secret;
}

function base64Url(value) {
  return Buffer.from(value).toString("base64url");
}

function signAccessToken(secret, userId) {
  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const payload = base64Url(
    JSON.stringify({
      sub: String(userId),
      role: "USER",
      type: "access",
      iat: now,
      exp: now + 3600,
    })
  );
  const signature = crypto.createHmac("sha256", secret).update(`${header}.${payload}`).digest("base64url");
  return `${header}.${payload}.${signature}`;
}

function encodeQuery(value) {
  return encodeURIComponent(value);
}

async function request(secret, name, pathAndQuery, userId = 1) {
  const started = Date.now();
  const response = await fetch(`${baseUrl}${pathAndQuery}`, {
    headers: { Authorization: `Bearer ${signAccessToken(secret, userId)}` },
  });
  const text = await response.text();
  let body;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = text;
  }
  return {
    name,
    userId,
    method: "GET",
    path: pathAndQuery,
    status: response.status,
    ok: response.ok,
    elapsedMs: Date.now() - started,
    body,
  };
}

function summarizeSearch(item) {
  const body = item.body || {};
  return {
    status: item.status,
    primaryType: body.primaryType,
    restaurantCount: body.restaurantCount,
    userCount: body.userCount,
    regionCount: body.regionCount,
    interpretation: body.interpretation,
    restaurants: (body.restaurants || []).slice(0, 5).map((restaurant) => ({
      id: restaurant.restaurantId,
      name: restaurant.restaurantName,
      region: restaurant.regionName,
      category: restaurant.primaryCategoryName,
      matchedBy: restaurant.matchedBy,
      source: restaurant.source,
    })),
    regions: (body.regions || []).slice(0, 5),
  };
}

function summarizeRanking(item) {
  const body = item.body || {};
  return {
    status: item.status,
    scope: body.scope,
    regionName: body.regionName,
    category: body.category,
    count: (body.items || []).length,
    items: (body.items || []).slice(0, 10).map((restaurant) => ({
      rank: restaurant.rank,
      id: restaurant.restaurantId,
      name: restaurant.restaurantName,
      region: restaurant.regionName,
      categories: restaurant.categories,
      adjustedScore: restaurant.adjustedScore,
      averageAutoScore: restaurant.averageAutoScore,
      evaluationCount: restaurant.evaluationCount,
    })),
  };
}

function summarizeRecommendation(item, user) {
  const body = item.body || {};
  return {
    status: item.status,
    userId: item.userId,
    persona: user?.persona,
    baseRegionName: body.baseRegionName,
    count: (body.items || []).length,
    fallbackCount: (body.items || []).filter((restaurant) => restaurant.fallbackRegion).length,
    items: (body.items || []).slice(0, 8).map((restaurant) => ({
      rank: restaurant.rank,
      id: restaurant.restaurantId,
      name: restaurant.restaurantName,
      region: restaurant.regionName,
      categories: restaurant.categories,
      finalScore: restaurant.finalScore,
      userPreferenceScore: restaurant.userPreferenceScore,
      categoryFitScore: restaurant.categoryFitScore,
      collaborativeScore: restaurant.collaborativeScore,
      regionScore: restaurant.regionScore,
      fallbackRegion: restaurant.fallbackRegion,
    })),
  };
}

function summarizeHiddenGem(item) {
  const body = item.body || {};
  return {
    status: item.status,
    regionTownName: body.regionTownName,
    count: (body.items || []).length,
    items: (body.items || []).slice(0, 8).map((restaurant) => ({
      rank: restaurant.rank,
      id: restaurant.restaurantId,
      name: restaurant.restaurantName,
      region: restaurant.regionName,
      town: restaurant.regionTownName,
      score: restaurant.recommendationScore,
      saves: restaurant.evaluationCount,
    })),
  };
}

function summarizeListRecommendation(item, user) {
  const body = item.body || {};
  return {
    status: item.status,
    userId: item.userId,
    persona: user?.persona,
    count: (body.items || []).length,
    items: (body.items || []).slice(0, 5).map((list) => ({
      rank: list.rank,
      listId: list.listId,
      title: list.title,
      regionName: list.regionName,
      recommendationScore: list.recommendationScore,
    })),
  };
}

function summarizeDetail(item, restaurantId) {
  const body = item.body || {};
  return {
    status: item.status,
    id: restaurantId,
    name: body.name || body.restaurantName,
    regionName: body.regionName,
    categories: body.categories,
    menuCount: (body.menus || body.menuItems || []).length,
    photoCount: (body.photos || []).length,
    tagCount: (body.additionalInfoTags || []).length,
    tags: (body.additionalInfoTags || []).slice(0, 8).map((tag) => tag.tagName),
  };
}

function buildSummary(report) {
  const failed = [];
  for (const group of ["search", "rankings", "recommendations", "listRecommendations", "hiddenGems", "details"]) {
    for (const item of report[group]) {
      if (!item.raw.ok) {
        failed.push({
          group,
          name: item.raw.name,
          status: item.raw.status,
          path: item.raw.path,
          body: item.raw.body,
        });
      }
    }
  }

  const recommendationTopNames = report.recommendations
    .map((recommendation) => recommendation.summary.items[0]?.name)
    .filter(Boolean);
  const recommendationRestaurantIds = new Set(
    report.recommendations.flatMap((recommendation) => recommendation.summary.items.map((item) => item.id))
  );
  const rankingRestaurantIds = new Set(report.rankings.flatMap((ranking) => ranking.summary.items.map((item) => item.id)));

  return {
    failedCount: failed.length,
    failed,
    searchCases: report.search.length,
    searchInternalTop5Count: report.search.reduce(
      (sum, search) => sum + search.summary.restaurants.filter((restaurant) => restaurant.source === "INTERNAL").length,
      0
    ),
    searchFallbackTop5Count: report.search.reduce(
      (sum, search) =>
        sum + search.summary.restaurants.filter((restaurant) => restaurant.source === "EXTERNAL_FALLBACK").length,
      0
    ),
    rankingCases: report.rankings.length,
    uniqueRestaurantsInRankingTop10: rankingRestaurantIds.size,
    recommendationUsers: report.users.length,
    uniqueRecommendationTop1: new Set(recommendationTopNames).size,
    uniqueRestaurantsInRecommendationTop8: recommendationRestaurantIds.size,
    recommendationFallbackTotal: report.recommendations.reduce(
      (sum, recommendation) => sum + recommendation.summary.fallbackCount,
      0
    ),
    hiddenGemCases: report.hiddenGems.length,
    detailCases: report.details.length,
  };
}

async function main() {
  fs.mkdirSync(outputDir, { recursive: true });
  const secret = readJwtSecret();
  const users = [
    { id: 1, persona: "지역 거주 점심형" },
    { id: 80, persona: "직장인 점심형" },
    { id: 130, persona: "맛 우선 한식러" },
    { id: 180, persona: "카페 작업형" },
    { id: 225, persona: "데이트 외식형" },
    { id: 265, persona: "술집 탐색형" },
    { id: 300, persona: "가족 외식형" },
    { id: 335, persona: "가성비 엄격형" },
    { id: 365, persona: "프리미엄 외식형" },
    { id: 395, persona: "건강식 선호형" },
    { id: 425, persona: "카테고리 탐색형" },
    { id: 450, persona: "숨은 맛집 수집형" },
    { id: 475, persona: "리뷰 신뢰형" },
    { id: 495, persona: "무난 안전형" },
    { id: 510, persona: "광역 방문형" },
  ];

  const searchCases = [
    ["search-yukbuk-naengmyeon", `/search?query=${encodeQuery("역북동 냉면")}`],
    ["search-cheoin-meat", `/search?query=${encodeQuery("처인구 고기")}`],
    ["search-mju-cafe", `/search?query=${encodeQuery("명지대 근처 카페")}`],
    ["search-seoul-donkatsu", `/search?query=${encodeQuery("서울 성북구 돈까스")}`],
    ["search-goseong-cafe", `/search?query=${encodeQuery("강원 고성군 카페")}`],
    ["search-honbab-gimbap", `/search?query=${encodeQuery("혼밥 김밥")}`],
    ["search-parking-cafe", `/search?query=${encodeQuery("주차 가능 카페")}`],
  ];
  const rankingCases = [
    ["ranking-overall", "/rankings/restaurants?limit=10"],
    ["ranking-yongin-cheoin", `/rankings/restaurants?regionName=${encodeQuery("용인시 처인구")}&limit=10`],
    ["ranking-yongin-suji", `/rankings/restaurants?regionName=${encodeQuery("용인시 수지구")}&limit=10`],
    ["ranking-seoul-seongbuk", `/rankings/restaurants?regionName=${encodeQuery("서울특별시 성북구")}&limit=10`],
    ["ranking-cafe", `/rankings/restaurants?category=${encodeQuery("카페")}&limit=10`],
    [
      "ranking-cheoin-cafe",
      `/rankings/restaurants?regionName=${encodeQuery("용인시 처인구")}&category=${encodeQuery("카페")}&limit=10`,
    ],
    [
      "ranking-cheoin-naengmyeon",
      `/rankings/restaurants?regionName=${encodeQuery("용인시 처인구")}&category=${encodeQuery("냉면")}&limit=10`,
    ],
  ];
  const hiddenCases = ["역북동", "김량장동", "삼가동"].map((town) => [
    `hidden-${town}`,
    `/recommendations/restaurants/hidden-gems?regionTownName=${encodeQuery(town)}`,
  ]);

  const report = {
    generatedAt: new Date().toISOString(),
    baseUrl,
    users,
    search: [],
    rankings: [],
    recommendations: [],
    listRecommendations: [],
    hiddenGems: [],
    details: [],
    summary: {},
  };

  for (const [name, requestPath] of searchCases) {
    const raw = await request(secret, name, requestPath, 1);
    report.search.push({ raw, summary: summarizeSearch(raw) });
  }
  for (const [name, requestPath] of rankingCases) {
    const raw = await request(secret, name, requestPath, 1);
    report.rankings.push({ raw, summary: summarizeRanking(raw) });
  }
  for (const [name, requestPath] of hiddenCases) {
    const raw = await request(secret, name, requestPath, 1);
    report.hiddenGems.push({ raw, summary: summarizeHiddenGem(raw) });
  }
  for (const user of users) {
    const recommendationRaw = await request(secret, `recommendations-restaurants-${user.id}`, "/recommendations/restaurants", user.id);
    report.recommendations.push({ raw: recommendationRaw, summary: summarizeRecommendation(recommendationRaw, user) });

    const listsRaw = await request(secret, `recommendations-lists-${user.id}`, "/recommendations/lists", user.id);
    report.listRecommendations.push({ raw: listsRaw, summary: summarizeListRecommendation(listsRaw, user) });
  }

  const detailIds = new Set();
  for (const ranking of report.rankings) {
    for (const item of ranking.summary.items.slice(0, 2)) detailIds.add(item.id);
  }
  for (const recommendation of report.recommendations) {
    for (const item of recommendation.summary.items.slice(0, 1)) detailIds.add(item.id);
  }
  for (const restaurantId of [...detailIds].filter(Boolean).slice(0, 20)) {
    const raw = await request(secret, `detail-${restaurantId}`, `/restaurants/${restaurantId}`, 1);
    report.details.push({ raw, summary: summarizeDetail(raw, restaurantId) });
  }

  report.summary = buildSummary(report);
  fs.writeFileSync(path.join(outputDir, "seed_api_e2e_report.json"), JSON.stringify(report, null, 2), "utf8");
  fs.writeFileSync(
    path.join(outputDir, "seed_api_e2e_summary.json"),
    JSON.stringify(
      {
        generatedAt: report.generatedAt,
        baseUrl,
        summary: report.summary,
        search: report.search.map((item) => ({ name: item.raw.name, ...item.summary })),
        rankings: report.rankings.map((item) => ({ name: item.raw.name, ...item.summary })),
        recommendations: report.recommendations.map((item) => ({ name: item.raw.name, ...item.summary })),
        listRecommendations: report.listRecommendations.map((item) => ({ name: item.raw.name, ...item.summary })),
        hiddenGems: report.hiddenGems.map((item) => ({ name: item.raw.name, ...item.summary })),
        details: report.details.map((item) => ({ name: item.raw.name, ...item.summary })),
      },
      null,
      2
    ),
    "utf8"
  );

  console.log(JSON.stringify(report.summary, null, 2));
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
