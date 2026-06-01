#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const restaurantsPath = path.resolve(
  process.env.RESTAURANTS_CSV || "tmp/mock_seed_import/mock_restaurants_current.csv"
);
const menusPath = path.resolve(
  process.env.MENUS_CSV || "tmp/mock_seed_import/mock_menu_items_current.csv"
);
const outputDir = path.resolve(process.env.OUTPUT_DIR || "tmp/mock_seed_import");
const now = process.env.MOCK_NOW || new Date().toISOString();

if (!fs.existsSync(restaurantsPath)) {
  throw new Error(`RESTAURANTS_CSV not found: ${restaurantsPath}`);
}
if (!fs.existsSync(menusPath)) {
  throw new Error(`MENUS_CSV not found: ${menusPath}`);
}

function parseCsv(text) {
  const rows = [];
  let row = [];
  let field = "";
  let inQuotes = false;
  for (let i = 0; i < text.length; i += 1) {
    const char = text[i];
    if (inQuotes) {
      if (char === '"' && text[i + 1] === '"') {
        field += '"';
        i += 1;
      } else if (char === '"') {
        inQuotes = false;
      } else {
        field += char;
      }
      continue;
    }
    if (char === '"') {
      inQuotes = true;
    } else if (char === ",") {
      row.push(field);
      field = "";
    } else if (char === "\n") {
      row.push(field);
      rows.push(row);
      row = [];
      field = "";
    } else if (char !== "\r") {
      field += char;
    }
  }
  if (field.length > 0 || row.length > 0) {
    row.push(field);
    rows.push(row);
  }
  const headers = rows.shift().map((header) => header.replace(/^\uFEFF/, ""));
  return rows
    .filter((values) => values.length === headers.length)
    .map((values) => Object.fromEntries(headers.map((header, index) => [header, values[index]])));
}

function csvCell(value) {
  if (value === null || value === undefined) return "";
  const text = String(value).replace(/\r?\n/g, " ");
  if (/[",\n\r]/.test(text)) return `"${text.replace(/"/g, '""')}"`;
  return text;
}

function writeCsv(fileName, headers, rows) {
  fs.mkdirSync(outputDir, { recursive: true });
  const body = [
    headers.join(","),
    ...rows.map((row) => headers.map((header) => csvCell(row[header])).join(",")),
  ].join("\n");
  fs.writeFileSync(path.join(outputDir, fileName), `${body}\n`, "utf8");
}

function normalizeText(value) {
  return String(value || "").normalize("NFKC").trim().replace(/\s+/g, " ");
}

function compactKey(value) {
  return normalizeText(value)
    .replace(/[(){}\[\]'"`.,]/g, "")
    .replace(/[\/&·ㆍ]/g, "")
    .replace(/\s+/g, "");
}

function parseJsonArray(value) {
  const text = normalizeText(value);
  if (!text) return [];
  try {
    const parsed = JSON.parse(text);
    return Array.isArray(parsed) ? parsed.map(normalizeText).filter(Boolean) : [];
  } catch {
    return text
      .replace(/^\[|\]$/g, "")
      .split(/[;,|]/)
      .map((item) => normalizeText(item.replace(/^"|"$/g, "")))
      .filter(Boolean);
  }
}

function includesAny(text, patterns) {
  return patterns.some((pattern) => pattern.test(text));
}

function tagKey(scope, name) {
  return `${scope}:${compactKey(name)}`;
}

const broadCategories = [
  { name: "카페/디저트", patterns: [/카페|디저트|베이커리|커피|빵|케이크|브런치/] },
  { name: "해산물", patterns: [/해산물|생선|물회|회\b|장어|조개|굴|복어|낙지|쭈꾸미|주꾸미|아구|아귀|코다리/] },
  { name: "한식", patterns: [/한식|국밥|백반|찌개|냉면|칼국수|해장국|곰탕|설렁탕|오리|족발|보쌈|낙지|쭈꾸미|장어|삼계탕|백숙/] },
  { name: "고기", patterns: [/고기|구이|삼겹|갈비|한우|소고기|돼지고기|육류|곱창|막창|양꼬치|닭갈비/] },
  { name: "일식", patterns: [/일식|초밥|스시|라멘|돈까스|돈가스|우동|소바|오마카세|이자카야/] },
  { name: "중식", patterns: [/중식|중국|짜장|짬뽕|마라|탕수육|양꼬치|훠궈/] },
  { name: "양식", patterns: [/양식|파스타|스테이크|피자|이탈리안|프렌치|리조또|브런치/] },
  { name: "분식", patterns: [/분식|김밥|떡볶|튀김|순대|만두|라면/] },
  { name: "치킨/피자/버거", patterns: [/치킨|닭강정|피자|버거|햄버거|패스트/] },
  { name: "술집", patterns: [/술집|주점|포차|호프|맥주|이자카야|와인|바\b|bar/i] },
  { name: "건강식", patterns: [/샐러드|포케|건강|비건|채식|그릭|요거트/] },
];

const menuDefinitions = [
  ["물냉면", "냉면", [/물냉면/]],
  ["비빔냉면", "냉면", [/비빔냉면/]],
  ["냉면", null, [/냉면/]],
  ["잔치국수", "국수", [/잔치국수/]],
  ["비빔국수", "국수", [/비빔국수/]],
  ["콩국수", "국수", [/콩국수/]],
  ["장칼국수", "국수", [/장칼국수/]],
  ["칼국수", "국수", [/칼국수/]],
  ["막국수", "국수", [/막국수/]],
  ["쫄면", "국수", [/쫄면/]],
  ["쌀국수", "국수", [/쌀국수/]],
  ["국수", null, [/국수/]],
  ["라멘", "면요리", [/라멘|라면/]],
  ["우동", "면요리", [/우동/]],
  ["소바", "면요리", [/소바/]],
  ["파스타", "양식", [/파스타|스파게티/]],
  ["까르보나라", "파스타", [/까르보나라/]],
  ["리조또", "양식", [/리조또/]],
  ["국밥", null, [/국밥/]],
  ["순대국", "국밥", [/순대국|순댓국/]],
  ["돼지국밥", "국밥", [/돼지국밥/]],
  ["해장국", "국밥", [/해장국/]],
  ["갈비탕", "국밥", [/갈비탕/]],
  ["설렁탕", "국밥", [/설렁탕/]],
  ["곰탕", "국밥", [/곰탕/]],
  ["육개장", "국밥", [/육개장/]],
  ["찌개", null, [/찌개/]],
  ["김치찌개", "찌개", [/김치찌개/]],
  ["된장찌개", "찌개", [/된장찌개/]],
  ["부대찌개", "찌개", [/부대찌개/]],
  ["순두부", "찌개", [/순두부/]],
  ["청국장", "찌개", [/청국장/]],
  ["백반", "한식", [/백반|정식/]],
  ["비빔밥", "한식", [/비빔밥/]],
  ["제육", "한식", [/제육/]],
  ["보쌈", "한식", [/보쌈/]],
  ["족발", "한식", [/족발/]],
  ["수육", "한식", [/수육/]],
  ["낙지", "해산물", [/낙지/]],
  ["쭈꾸미", "해산물", [/쭈꾸미|주꾸미/]],
  ["아구찜", "해산물", [/아구찜|아귀찜/]],
  ["코다리", "해산물", [/코다리/]],
  ["물회", "해산물", [/물회/]],
  ["장어", "해산물", [/장어/]],
  ["오리", "한식", [/오리/]],
  ["백숙", "한식", [/백숙/]],
  ["삼계탕", "한식", [/삼계탕/]],
  ["삼겹살", "고기구이", [/삼겹살|오겹살/]],
  ["소고기", "고기구이", [/소고기|한우|등심|안심|차돌/]],
  ["갈비", "고기구이", [/갈비/]],
  ["양꼬치", "고기구이", [/양꼬치/]],
  ["곱창", "고기구이", [/곱창/]],
  ["막창", "고기구이", [/막창/]],
  ["스테이크", "양식", [/스테이크/]],
  ["닭갈비", "고기구이", [/닭갈비/]],
  ["닭발", "술안주", [/닭발/]],
  ["짜장면", "중식", [/짜장|자장/]],
  ["간짜장", "짜장면", [/간짜장/]],
  ["짬뽕", "중식", [/짬뽕/]],
  ["볶음밥", "중식", [/볶음밥/]],
  ["김치볶음밥", "볶음밥", [/김치볶음밥/]],
  ["새우볶음밥", "볶음밥", [/새우볶음밥/]],
  ["탕수육", "중식", [/탕수육/]],
  ["꿔바로우", "중식", [/꿔바로우/]],
  ["깐풍기", "중식", [/깐풍기/]],
  ["유린기", "중식", [/유린기/]],
  ["양장피", "중식", [/양장피/]],
  ["마라탕", "중식", [/마라탕/]],
  ["마라샹궈", "중식", [/마라샹궈/]],
  ["잡채밥", "중식", [/잡채밥/]],
  ["초밥", "일식", [/초밥|스시/]],
  ["사케동", "덮밥", [/사케동/]],
  ["텐동", "덮밥", [/텐동/]],
  ["덮밥", "일식", [/덮밥|동\b/]],
  ["돈까스", "카츠", [/돈까스|돈가스/]],
  ["카츠", "일식", [/카츠/]],
  ["카츠동", "카츠", [/카츠동/]],
  ["규동", "덮밥", [/규동/]],
  ["오므라이스", "일식", [/오므라이스/]],
  ["김밥", "분식", [/김밥/]],
  ["떡볶이", "분식", [/떡볶/]],
  ["순대", "분식", [/순대/]],
  ["튀김", "분식", [/튀김/]],
  ["만두", "분식", [/만두/]],
  ["군만두", "만두", [/군만두/]],
  ["왕만두", "만두", [/왕만두/]],
  ["어묵탕", "술안주", [/어묵탕/]],
  ["주먹밥", "분식", [/주먹밥/]],
  ["치킨", "패스트푸드", [/치킨|후라이드|양념치킨/]],
  ["닭강정", "치킨", [/닭강정/]],
  ["피자", "패스트푸드", [/피자/]],
  ["버거", "패스트푸드", [/버거|햄버거/]],
  ["샐러드", "건강식", [/샐러드/]],
  ["샌드위치", "브런치", [/샌드위치/]],
  ["브런치", "카페/디저트", [/브런치/]],
  ["아메리카노", "커피", [/아메리카노/]],
  ["카페라떼", "라떼", [/카페\s?라떼/]],
  ["바닐라라떼", "라떼", [/바닐라\s?라떼/]],
  ["돌체라떼", "라떼", [/돌체\s?라떼/]],
  ["말차라떼", "라떼", [/말차\s?라떼/]],
  ["딸기라떼", "라떼", [/딸기\s?라떼/]],
  ["초코라떼", "라떼", [/초코\s?라떼/]],
  ["카페모카", "커피", [/카페\s?모카/]],
  ["카푸치노", "커피", [/카푸치노/]],
  ["에스프레소", "커피", [/에스프레소/]],
  ["아인슈페너", "커피", [/아인슈페너|아인슈패너/]],
  ["콜드브루", "커피", [/콜드\s?브루/]],
  ["플랫화이트", "커피", [/플랫\s?화이트/]],
  ["라떼", "커피", [/라떼/]],
  ["커피", null, [/커피/]],
  ["에이드", "음료", [/에이드/]],
  ["레몬에이드", "에이드", [/레몬에이드/]],
  ["자몽에이드", "에이드", [/자몽에이드/]],
  ["아이스티", "음료", [/아이스티/]],
  ["주스", "음료", [/주스|쥬스/]],
  ["스무디", "음료", [/스무디/]],
  ["차", "음료", [/대추차|유자차|생강차|허브티|홍차|녹차/]],
  ["소금빵", "베이커리", [/소금빵/]],
  ["크루아상", "베이커리", [/크루아상|크로와상/]],
  ["베이글", "베이커리", [/베이글/]],
  ["케이크", "디저트", [/케이크/]],
  ["쿠키", "디저트", [/쿠키/]],
  ["마카롱", "디저트", [/마카롱/]],
  ["휘낭시에", "디저트", [/휘낭시에/]],
  ["타르트", "디저트", [/타르트/]],
  ["에그타르트", "타르트", [/에그타르트/]],
  ["스콘", "디저트", [/스콘/]],
  ["단팥빵", "베이커리", [/단팥빵/]],
  ["고로케", "베이커리", [/고로케/]],
  ["도넛", "디저트", [/도넛|도너츠/]],
  ["와플", "디저트", [/와플/]],
  ["빙수", "디저트", [/빙수/]],
  ["소주", "주류", [/소주/]],
  ["맥주", "주류", [/맥주/]],
  ["하이볼", "주류", [/하이볼/]],
  ["막걸리", "주류", [/막걸리/]],
  ["청하", "주류", [/청하/]],
  ["와인", "주류", [/와인/]],
  ["칵테일", "주류", [/칵테일/]],
  ["파전", "술안주", [/파전/]],
  ["해물파전", "파전", [/해물파전/]],
].map(([name, parent, patterns]) => ({ name, parent, patterns }));

menuDefinitions.sort((left, right) => right.name.length - left.name.length || left.name.localeCompare(right.name, "ko"));

const featureDefinitions = [
  ["주차 가능", [/주차/], 68],
  ["포장 가능", [/포장/], 64],
  ["배달 가능", [/배달/], 63],
  ["예약 가능", [/예약/], 62],
  ["단체 가능", [/단체/], 61],
  ["무선 인터넷", [/무선 인터넷|와이파이|wifi/i], 58],
  ["유아의자", [/유아의자/], 57],
  ["대기공간", [/대기공간/], 56],
  ["반려동물 동반", [/반려동물|애견|펫/], 55],
  ["노키즈존", [/노키즈존/], 50],
  ["간편결제", [/간편결제|제로페이|네이버페이/], 46],
  ["남녀 화장실", [/남\/녀 화장실|화장실 구분/], 36],
].map(([name, patterns, priority]) => ({ name, patterns, priority }));

const tagsByKey = new Map();
const categoryParentNames = new Set(broadCategories.map((category) => category.name));

function ensureTag(scope, name, parentTagKey = null) {
  const key = tagKey(scope, name);
  if (!tagsByKey.has(key)) {
    tagsByKey.set(key, {
      id: tagsByKey.size + 1,
      tag_key: key,
      tag_name: normalizeText(name),
      parent_tag_key: parentTagKey,
      is_active: true,
      created_at: now,
      updated_at: now,
    });
  }
  return tagsByKey.get(key);
}

function ensureParentMenu(name) {
  return ensureTag("menu", name).tag_key;
}

function parentTagKeyForMenuDefinition(definition) {
  if (!definition.parent) return null;
  if (categoryParentNames.has(definition.parent)) {
    return ensureTag("category", definition.parent).tag_key;
  }
  return ensureParentMenu(definition.parent);
}

function evidenceKey(tag, source) {
  return `${tag.tag_key}:${source}`;
}

function addEvidence(evidence, tag, matchedMenuCount, isPrimary, priority, source = "default") {
  const key = tag.tag_key;
  const existing = evidence.get(key);
  if (existing) {
    existing.matched_menu_count += matchedMenuCount;
    existing.is_primary = existing.is_primary || isPrimary;
    existing.priority = Math.max(existing.priority, priority);
    existing.sources.add(source);
    return;
  }
  evidence.set(key, {
    tag,
    matched_menu_count: matchedMenuCount,
    is_primary: Boolean(isPrimary),
    priority,
    sources: new Set([source]),
  });
}

function broadCategoryFor(text) {
  const source = normalizeText(text);
  return broadCategories.find((category) => includesAny(source, category.patterns)) || null;
}

function cleanedCategoryParts(row) {
  const values = [row.category_name, row.primary_category_name]
    .flatMap((value) => normalizeText(value).split(/[>,/|]/))
    .map((value) => value.trim())
    .filter(Boolean)
    .filter((value, index, values) => values.indexOf(value) === index);
  return values.filter(
    (value) =>
      value.length <= 20 &&
      !/^\d+$/.test(value) &&
      !/^(기타|기업|음식점|일반음식점|전문점)$/.test(value)
  );
}

function categoryTagName(value, broadName) {
  const text = normalizeText(value).replace(/전문점?$/, "");
  if (!text) return null;
  if (broadName && compactKey(text) === compactKey(broadName)) return broadName;
  return `${text} 전문`;
}

function matchedMenuDefinitions(menuName) {
  const source = normalizeText(menuName);
  if (!source || /(무료|쿠폰|혜택|생일|이벤트|리뷰|영수증|예약|대관|문의|변동|시가|추가|옵션|어린이|미만|이용권|멤버십|콜키지|서비스)/.test(source)) {
    return [];
  }
  return menuDefinitions.filter((definition) => includesAny(source, definition.patterns));
}

const restaurants = parseCsv(fs.readFileSync(restaurantsPath, "utf8"));
const menus = parseCsv(fs.readFileSync(menusPath, "utf8"));
const menusByRestaurant = new Map();
const menuTagKeyRows = [];

for (const menu of menus) {
  const restaurantId = Number(menu.restaurant_id);
  if (!menusByRestaurant.has(restaurantId)) menusByRestaurant.set(restaurantId, []);
  menusByRestaurant.get(restaurantId).push(menu);

  const matches = matchedMenuDefinitions(menu.menu_name);
  const primary = matches[0];
  menuTagKeyRows.push({
    id: Number(menu.id),
    menu_tag_key: primary ? tagKey("menu", primary.name) : null,
  });
}

const restaurantTagRows = [];
let nextRestaurantTagId = 1;
let taggedRestaurantCount = 0;
let maxTagsPerRestaurant = 0;

for (const restaurant of restaurants) {
  const restaurantId = Number(restaurant.id);
  const evidence = new Map();
  const categoryText = `${restaurant.category_name || ""} ${restaurant.primary_category_name || ""} ${restaurant.name || ""}`;
  const broad = broadCategoryFor(categoryText);
  if (broad) {
    const categoryTag = ensureTag("category", broad.name);
    addEvidence(evidence, categoryTag, 0, true, 100, "broad-category");
  }

  for (const part of cleanedCategoryParts(restaurant)) {
    const partBroad = broadCategoryFor(part) || broad;
    const parent = partBroad ? ensureTag("category", partBroad.name).tag_key : null;
    const tagName = categoryTagName(part, partBroad?.name);
    if (!tagName) continue;
    const categoryTag = ensureTag("category", tagName, parent);
    addEvidence(evidence, categoryTag, 0, !broad, 86, "category");
  }

  const menuMatchCounts = new Map();
  for (const menu of menusByRestaurant.get(restaurantId) || []) {
    for (const definition of matchedMenuDefinitions(menu.menu_name)) {
      const key = tagKey("menu", definition.name);
      menuMatchCounts.set(key, {
        definition,
        count: (menuMatchCounts.get(key)?.count || 0) + 1,
      });
    }
  }

  for (const { definition, count } of menuMatchCounts.values()) {
    const parentKey = parentTagKeyForMenuDefinition(definition);
    const menuTag = ensureTag("menu", definition.name, parentKey);
    addEvidence(evidence, menuTag, count, false, 76, "menu");
    if (definition.parent && !categoryParentNames.has(definition.parent)) {
      const parentName = definition.parent;
      const parentTag = ensureTag("menu", parentName);
      addEvidence(evidence, parentTag, Math.max(1, Math.floor(count / 2)), false, 68, "menu-parent");
    }
  }

  const conveniences = parseJsonArray(restaurant.conveniences).join(" ");
  for (const feature of featureDefinitions) {
    if (includesAny(conveniences, feature.patterns)) {
      addEvidence(evidence, ensureTag("feature", feature.name), 0, false, feature.priority, "feature");
    }
  }

  const text = `${categoryText} ${(menusByRestaurant.get(restaurantId) || []).map((menu) => menu.menu_name).join(" ")} ${conveniences}`;
  const hasConvenience = (pattern) => pattern.test(conveniences);
  const hasBroadCategory = (name) => broad?.name === name || text.includes(name);
  const styleEvidence = [
    ["혼밥", /김밥|국밥|덮밥|돈까스|돈가스|라멘|우동|냉면|분식|버거|샌드위치/.test(text)],
    [
      "빠른 식사",
      broad?.name !== "술집" &&
        (hasBroadCategory("분식") || hasBroadCategory("치킨/피자/버거") || /김밥|버거|샌드위치|떡볶|라면/.test(text)),
    ],
    [
      "가족외식",
      broad?.name !== "술집" &&
        (hasConvenience(/단체/) || hasConvenience(/주차/)) &&
        /갈비|고기|오리|백숙|삼계탕|한식|샤브|구이/.test(text),
    ],
    ["데이트", hasBroadCategory("카페/디저트") || hasBroadCategory("양식") || hasBroadCategory("일식")],
    ["카페 작업", hasBroadCategory("카페/디저트") && hasConvenience(/무선 인터넷|와이파이|wifi/i)],
    ["술자리", hasBroadCategory("술집") || /맥주|소주|하이볼|막걸리|안주|닭발|파전/.test(text)],
  ];
  for (const [name, matched] of styleEvidence) {
    if (matched) addEvidence(evidence, ensureTag("style", name), 0, false, 46, "style");
  }

  const tasteRules = [
    ["매운맛", [/마라|매운|불닭|닭발|짬뽕|낙지|쭈꾸미|주꾸미|아구찜|아귀찜/]],
    ["든든한 한끼", [/국밥|해장국|백반|찌개|고기|갈비|탕|정식|덮밥/]],
    ["면요리", [/냉면|국수|칼국수|라멘|우동|소바|쌀국수|파스타|막국수|쫄면/]],
    ["고기구이", [/삼겹|갈비|한우|소고기|돼지고기|곱창|막창|양꼬치|구이/]],
    ["해산물", [/해물|물회|회\b|초밥|장어|아구|아귀|낙지|쭈꾸미|주꾸미|코다리/]],
    ["가벼운 식사", [/샐러드|샌드위치|브런치|김밥|포케|요거트/]],
    ["디저트", [/케이크|쿠키|마카롱|휘낭시에|타르트|와플|빙수|빵|베이커리/]],
  ];
  for (const [name, patterns] of tasteRules) {
    if (includesAny(text, patterns)) {
      addEvidence(evidence, ensureTag("taste", name), 0, false, 52, "taste");
    }
  }

  const sortedEvidence = [...evidence.values()].sort((left, right) => {
    if (left.is_primary !== right.is_primary) return left.is_primary ? -1 : 1;
    return (
      right.priority - left.priority ||
      right.matched_menu_count - left.matched_menu_count ||
      left.tag.tag_name.localeCompare(right.tag.tag_name, "ko")
    );
  });
  const selectedByKey = new Map();
  const selectedNames = new Set();
  const addSelected = (items, limit) => {
    if (limit <= 0) return;
    let added = 0;
    for (const item of items) {
      if (selectedByKey.has(item.tag.tag_key)) continue;
      if (selectedNames.has(item.tag.tag_name)) continue;
      selectedByKey.set(item.tag.tag_key, item);
      selectedNames.add(item.tag.tag_name);
      added += 1;
      if (added >= limit) break;
    }
  };
  const byPrefix = (prefix) => sortedEvidence.filter((item) => item.tag.tag_key.startsWith(`${prefix}:`));
  addSelected(byPrefix("category"), 2);
  addSelected(byPrefix("menu"), 3);
  addSelected(byPrefix("feature"), 1);
  addSelected(byPrefix("taste"), 1);
  addSelected(byPrefix("style"), 1);
  addSelected(sortedEvidence, 8 - selectedByKey.size);
  const selected = [...selectedByKey.values()].slice(0, 8);

  if (selected.length > 0) {
    taggedRestaurantCount += 1;
    maxTagsPerRestaurant = Math.max(maxTagsPerRestaurant, selected.length);
  }

  for (const item of selected) {
    restaurantTagRows.push({
      id: nextRestaurantTagId,
      restaurant_id: restaurantId,
      tag_id: item.tag.id,
      matched_menu_count: item.matched_menu_count,
      is_primary: item.is_primary,
      created_at: now,
      updated_at: now,
    });
    nextRestaurantTagId += 1;
  }
}

const tagRows = [...tagsByKey.values()].sort((left, right) => left.id - right.id);

writeCsv(
  "tags_diverse.csv",
  ["id", "tag_key", "tag_name", "parent_tag_key", "is_active", "created_at", "updated_at"],
  tagRows
);
writeCsv(
  "restaurant_tags_diverse.csv",
  ["id", "restaurant_id", "tag_id", "matched_menu_count", "is_primary", "created_at", "updated_at"],
  restaurantTagRows
);
writeCsv("restaurant_menu_item_tag_keys.csv", ["id", "menu_tag_key"], menuTagKeyRows);

const prefixCounts = tagRows.reduce((acc, row) => {
  const prefix = row.tag_key.split(":")[0];
  acc[prefix] = (acc[prefix] || 0) + 1;
  return acc;
}, {});
const summary = {
  restaurants: restaurants.length,
  taggedRestaurants: taggedRestaurantCount,
  tags: tagRows.length,
  restaurantTags: restaurantTagRows.length,
  menuItems: menus.length,
  menuItemsWithTagKey: menuTagKeyRows.filter((row) => row.menu_tag_key).length,
  maxTagsPerRestaurant,
  prefixCounts,
  generatedAt: now,
};

fs.writeFileSync(path.join(outputDir, "tag_generation_summary.json"), JSON.stringify(summary, null, 2), "utf8");
console.log(JSON.stringify(summary, null, 2));
