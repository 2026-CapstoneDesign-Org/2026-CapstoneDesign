#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const restaurantsPath = path.resolve(process.env.RESTAURANTS_CSV || "");
const menusPath = path.resolve(process.env.MENUS_CSV || "");
const outputDir = path.resolve(process.env.OUTPUT_DIR || "tmp/mock_seed_import");
const now = process.env.MOCK_NOW || new Date().toISOString();

if (!restaurantsPath || !fs.existsSync(restaurantsPath)) {
  throw new Error(`RESTAURANTS_CSV not found: ${restaurantsPath}`);
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
  if (value === null || value === undefined) {
    return "";
  }
  const text = String(value).replace(/\r?\n/g, " ");
  if (/[",\n\r]/.test(text)) {
    return `"${text.replace(/"/g, '""')}"`;
  }
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
  return String(value || "").trim().replace(/\s+/g, " ");
}

function loadMenusByRestaurant(filePath) {
  const byRestaurant = new Map();
  if (!filePath || !fs.existsSync(filePath)) {
    return byRestaurant;
  }
  for (const row of parseCsv(fs.readFileSync(filePath, "utf8"))) {
    const restaurantId = Number(row.restaurant_id);
    const name = String(row.menu_name || "").trim();
    if (!restaurantId || !name || name.length < 2 || name.length > 35) {
      continue;
    }
    if (!/[가-힣A-Za-z0-9]/.test(name)) {
      continue;
    }
    if (!byRestaurant.has(restaurantId)) {
      byRestaurant.set(restaurantId, []);
    }
    const menus = byRestaurant.get(restaurantId);
    if (!menus.some((menu) => menu.name === name)) {
      menus.push({
        name,
        priceText: String(row.price_text || "").trim() || null,
      });
    }
  }
  return byRestaurant;
}

function hash(value) {
  let result = 2166136261;
  for (const char of String(value)) {
    result ^= char.codePointAt(0);
    result = Math.imul(result, 16777619);
  }
  return result >>> 0;
}

function createRng(seed) {
  let state = seed >>> 0;
  return () => {
    state = Math.imul(1664525, state) + 1013904223;
    return (state >>> 0) / 4294967296;
  };
}

const rng = createRng(20260525);
const menusByRestaurant = loadMenusByRestaurant(menusPath);

function pick(items) {
  return items[Math.floor(rng() * items.length)];
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function round1(value) {
  return Math.round(value * 10) / 10;
}

function hashRatio(value) {
  return (hash(value) % 10000) / 10000;
}

function centeredHash(value, span = 1) {
  return (hashRatio(value) * 2 - 1) * span;
}

function weightedByHash(options, key) {
  const total = options.reduce((sum, option) => sum + option.weight, 0);
  let ticket = hashRatio(key) * total;
  for (const option of options) {
    ticket -= option.weight;
    if (ticket <= 0) return option.value;
  }
  return options[options.length - 1].value;
}

function hasFinalConsonant(value) {
  const chars = Array.from(String(value || "").trim());
  const last = chars[chars.length - 1];
  if (!last) {
    return false;
  }
  const code = last.charCodeAt(0) - 0xac00;
  return code >= 0 && code <= 11171 && code % 28 !== 0;
}

function topic(value) {
  return `${value}${hasFinalConsonant(value) ? "은" : "는"}`;
}

function subject(value) {
  return `${value}${hasFinalConsonant(value) ? "이" : "가"}`;
}

function normalizeCategory(row) {
  const text = `${row.category_name || ""} ${row.primary_category_name || ""} ${row.name || ""}`;
  if (/카페|디저트|베이커리|커피|빵|브런치/.test(text)) return "cafe";
  if (/중식|중국|짬뽕|짜장|마라/.test(text)) return "chinese";
  if (/일식|초밥|스시|라멘|돈까스|돈가스|우동|오마카세/.test(text)) return "japanese";
  if (/고기|삼겹|갈비|한우|양꼬치|바베큐|곱창|닭갈비|족발|보쌈/.test(text)) return "meat";
  if (/분식|떡볶|김밥|튀김|만두/.test(text)) return "street";
  if (/치킨|피자|버거|햄버거|패스트/.test(text)) return "fast";
  if (/술|호프|맥주|포차|이자카야|와인/.test(text)) return "bar";
  if (/샐러드|비건|건강|쌀국수|포케/.test(text)) return "healthy";
  if (/양식|파스타|스테이크|레스토랑/.test(text)) return "western";
  return "korean";
}

function roleForRestaurant(row, index) {
  if (
    /메가MGC|스타벅스|이디야|커피빈|컴포즈|빽다방|투썸|파리바게뜨|던킨|배스킨|맘스터치|롯데리아|버거킹|맥도날드|써브웨이|파파존스|교촌|굽네|BBQ|BHC/i.test(
      row.name || ""
    )
  ) {
    return "standard_chain";
  }
  const bucket = hash(`${row.id}:${row.name}`) % 100;
  if (bucket < 10) return "popular_elite";
  if (bucket < 22) return "hidden_gem";
  if (bucket < 35) return "value_specialist";
  if (bucket < 48) return "mood_specialist";
  if (bucket < 61) return "taste_specialist";
  if (bucket < 73) return "normal";
  if (bucket < 83) return "standard_chain";
  if (bucket < 92) return "overexposed";
  return index % 2 === 0 ? "weak" : "noisy";
}

function qualityForRole(role, category, id) {
  const noise = ((hash(`q:${id}`) % 100) - 50) / 100;
  const base = {
    popular_elite: [9.0, 8.2, 8.4],
    hidden_gem: [9.2, 8.4, 8.1],
    value_specialist: [8.0, 9.2, 7.3],
    mood_specialist: [7.8, 7.4, 9.2],
    taste_specialist: [9.1, 7.2, 7.4],
    normal: [6.8, 6.9, 6.6],
    standard_chain: [6.5, 6.9, 6.4],
    overexposed: [5.8, 5.7, 5.9],
    weak: [4.2, 4.7, 4.3],
    noisy: [6.4, 6.1, 6.3],
  }[role];
  const adjusted = [...base];
  if (category === "cafe") adjusted[2] += 0.4;
  if (category === "street") adjusted[1] += 0.4;
  if (category === "meat" || category === "japanese") adjusted[0] += 0.3;
  return {
    taste: clamp(adjusted[0] + noise, 2.5, 9.8),
    value: clamp(adjusted[1] - noise / 2, 2.5, 9.8),
    mood: clamp(adjusted[2] + noise / 3, 2.5, 9.8),
  };
}

const restaurants = parseCsv(fs.readFileSync(restaurantsPath, "utf8"))
  .map((row, index) => {
    const categoryKey = normalizeCategory(row);
    const role = roleForRestaurant(row, index);
    const quality = qualityForRole(role, categoryKey, row.id);
    const exposure =
      role === "popular_elite" || role === "standard_chain" || role === "overexposed"
        ? "popular"
        : role === "hidden_gem"
        ? "hidden"
        : "normal";
    const maxEval =
      role === "popular_elite" ? 45 : role === "hidden_gem" ? 6 : role === "weak" ? 18 : role === "noisy" ? 18 : 24;
    return {
      id: Number(row.id),
      name: row.name,
      regionName: row.region_name || "용인시 처인구",
      town: row.region_town_name || "역북동",
      categoryName: row.category_name || row.primary_category_name || "",
      categoryKey,
      menus: menusByRestaurant.get(Number(row.id)) || [],
      role,
      exposure,
      quality,
      maxEval,
    };
  })
  .filter((row) => row.id && row.regionName);

const byId = new Map(restaurants.map((restaurant) => [restaurant.id, restaurant]));
const evalCount = new Map(restaurants.map((restaurant) => [restaurant.id, 0]));

const personas = [
  ["지역 거주 점심형", 65, ["korean", "street", "chinese"], "value_first", "neutral", "safe_choice"],
  ["직장인 점심형", 55, ["korean", "chinese", "japanese"], "taste_value", "strict", "safe_choice"],
  ["맛 우선 한식러", 50, ["korean", "meat"], "taste_first", "neutral", "popular_first"],
  ["카페 작업형", 45, ["cafe", "brunch"], "mood_value", "generous", "hidden_gem_finder"],
  ["데이트 외식형", 40, ["western", "japanese", "cafe"], "taste_mood", "neutral", "review_sensitive"],
  ["술집 탐색형", 35, ["bar", "meat"], "mood_first", "generous", "new_place_explorer"],
  ["가족 외식형", 35, ["korean", "meat", "western"], "balanced", "neutral", "popular_first"],
  ["가성비 엄격형", 30, ["street", "korean", "fast"], "value_first", "very_strict", "safe_choice"],
  ["프리미엄 외식형", 30, ["japanese", "western", "meat"], "taste_mood", "strict", "review_sensitive"],
  ["건강식 선호형", 30, ["healthy", "brunch", "korean"], "balanced", "strict", "safe_choice"],
  ["카테고리 탐색형", 25, ["korean", "cafe", "japanese", "western", "chinese"], "category_sensitive", "neutral", "new_place_explorer"],
  ["숨은 맛집 수집형", 25, ["korean", "cafe", "bar"], "adventurous", "generous", "hidden_gem_finder"],
  ["리뷰 신뢰형", 20, ["korean", "japanese", "cafe", "meat"], "low_variance", "strict", "review_sensitive"],
  ["무난 안전형", 20, ["korean", "cafe", "street", "chinese"], "balanced", "neutral", "safe_choice"],
  ["광역 방문형", 15, ["japanese", "western", "cafe"], "taste_first", "generous", "popular_first"],
];

const categoryLabels = {
  korean: "한식",
  street: "분식",
  chinese: "중식",
  japanese: "일식",
  meat: "고기",
  cafe: "카페",
  western: "양식",
  bar: "술집",
  fast: "간편식",
  healthy: "건강식",
  brunch: "브런치",
};

function base36Hash(value, length = 6) {
  return hash(value).toString(36).padStart(length, "0").slice(0, length);
}

function providerForUser(id) {
  const bucket = hash(`provider:${id}`) % 100;
  if (bucket < 52) return "KAKAO";
  if (bucket < 85) return "NAVER";
  return "GOOGLE";
}

function providerUserIdFor(provider, id) {
  if (provider === "KAKAO") {
    return String(1000000000 + (hash(`kakao:${id}`) % 8999999999));
  }
  if (provider === "NAVER") {
    return `nv${base36Hash(`naver:${id}`, 12)}`;
  }
  return `${100000000000000000000n + BigInt(hash(`google-a:${id}`)) * 100000000n + BigInt(hash(`google-b:${id}`))}`;
}

const nicknameBases = [
  "오늘도한끼",
  "점심고민끝",
  "퇴근후밥상",
  "주말외식러",
  "동네입맛",
  "소소한미식",
  "단골찾는중",
  "밥심충전",
  "한식파",
  "국밥한그릇",
  "면좋아하는사람",
  "분식은못참지",
  "카페인필요",
  "라떼메모",
  "디저트먼저",
  "브런치기록",
  "가성비먼저",
  "반찬좋아",
  "매운맛연습",
  "혼밥기록",
  "약속장소찾기",
  "재방문각",
  "입맛보통",
  "맛있는하루",
  "밥먹고걷기",
  "따뜻한밥상",
  "오늘의메뉴",
  "소금빵찾는중",
  "커피한잔만",
  "점심엔김밥",
  "국물파",
  "고기굽는날",
  "샐러드도좋아",
  "저녁은든든히",
  "맛집메모장",
  "내돈내먹",
  "친구랑한끼",
  "회사근처밥",
  "가끔은양식",
  "동네식탁",
];

const shortAliasHandles = [
  "한끼식사",
  "밥친구",
  "맛기록",
  "냠냠로그",
  "두입만",
  "소복소복",
  "도란밥상",
  "몽글한끼",
  "라온식탁",
  "단짠단짠",
  "후루룩",
  "바삭노트",
  "고소한날",
  "든든하게",
  "입짧은편",
  "잘먹는편",
  "소금한꼬집",
  "국물한입",
  "밥길따라",
  "오늘뭐먹지",
  "조용한입맛",
  "메뉴고민중",
  "기록하는입맛",
  "동네한바퀴",
  "맛있는순간",
  "한숟가락",
  "면발좋아",
  "볶음밥파",
  "빵순기록",
  "커피메모",
  "디저트산책",
  "맛집수첩",
  "퇴근길한끼",
  "주말밥상",
  "소소한입맛",
  "다시갈지도",
  "오늘의식탁",
  "든든한끼록",
];

const placeNicknameBases = [
  "역북맛노트",
  "명지대밥친구",
  "처인한끼",
  "김량장점심",
  "남동밥기록",
  "삼가동카페",
  "용인밥상",
  "명지앞맛집",
  "처인맛로그",
  "역북카페로그",
  "용인점심기록",
  "중앙시장메모",
  "유방동한끼",
  "고림동밥상",
  "양지외식노트",
  "포곡맛기록",
  "모현브런치",
  "원삼밥친구",
];

const familyPetHandles = [
  "콩이엄마",
  "두부아빠",
  "모카언니",
  "토리누나",
  "보리집사",
  "나나언니",
  "초코형아",
  "라떼집사",
  "망고엄마",
  "호두아빠",
  "구름누나",
  "단추집사",
  "밤비언니",
  "쿠키엄마",
  "복순이누나",
  "하루집사",
  "감자아빠",
  "만두엄마",
  "설기누나",
  "루피집사",
];

const englishNicknames = [
  "today_meal",
  "local_table",
  "cafe_log",
  "lunch_note",
  "taste_memo",
  "plate_diary",
  "daily_bite",
  "table_route",
  "yongin_bite",
  "cafe_route",
  "meal_note",
  "lunch_route",
];

const koreanNameParts = [
  "민지",
  "수현",
  "지훈",
  "서연",
  "도윤",
  "하은",
  "준호",
  "지우",
  "현우",
  "예린",
  "성민",
  "유진",
  "다은",
  "시우",
  "나영",
  "태민",
  "소연",
  "재원",
  "은지",
  "민석",
  "하린",
  "지민",
  "서준",
  "예나",
  "윤아",
  "건우",
];

const nameSuffixes = [
  "맛노트",
  "밥상",
  "한끼",
  "먹로그",
  "카페",
  "점심",
  "디저트",
  "국밥",
  "브런치",
  "맛메모",
  "밥친구",
  "외식",
  "분식",
];

const moodWords = [
  "소소한",
  "든든한",
  "조용한",
  "바삭한",
  "따뜻한",
  "느긋한",
  "가벼운",
  "고소한",
  "달달한",
  "깔끔한",
  "푸짐한",
  "새콤한",
  "매콤한",
  "산뜻한",
  "평범한",
  "정직한",
  "작은",
  "동네",
  "주말",
  "퇴근길",
];

const handleObjects = [
  "밥상",
  "한끼",
  "맛기록",
  "식탁",
  "입맛",
  "맛노트",
  "카페노트",
  "디저트북",
  "점심노트",
  "국물로그",
  "면기록",
  "빵수첩",
  "외식메모",
  "메뉴수첩",
  "동네밥",
];

const personNicknameBases = [
  "민지맛노트",
  "수현밥상",
  "지훈한끼",
  "서연맛집록",
  "도윤점심",
  "하은카페",
  "준호밥기록",
  "지우디저트",
  "현우국밥",
  "예린브런치",
  "성민외식",
  "유진맛메모",
  "다은한끼",
  "시우밥상",
  "나영카페인",
  "태민고기",
  "소연분식",
  "재원맛로그",
  "은지점심",
  "민석밥친구",
];

function nicknameForUser(id, usedNicknames) {
  const style = weightedByHash(
    [
      { value: "short_alias", weight: 34 },
      { value: "food_daily", weight: 18 },
      { value: "person_suffix", weight: 15 },
      { value: "english", weight: 13 },
      { value: "family_pet", weight: 8 },
      { value: "local", weight: 7 },
      { value: "digit_variant", weight: 5 },
    ],
    `nick-style:${id}`
  );
  const base = nicknameBases[hash(`nick-base:${id}`) % nicknameBases.length];
  const shortAlias = shortAliasHandles[hash(`nick-short:${id}`) % shortAliasHandles.length];
  const place = placeNicknameBases[hash(`nick-place:${id}`) % placeNicknameBases.length];
  const family = familyPetHandles[hash(`nick-family:${id}`) % familyPetHandles.length];
  const person = personNicknameBases[hash(`nick-person:${id}`) % personNicknameBases.length];
  const name = koreanNameParts[hash(`nick-name:${id}`) % koreanNameParts.length];
  const suffix = nameSuffixes[hash(`nick-suffix:${id}`) % nameSuffixes.length];
  const english = englishNicknames[hash(`nick-en:${id}`) % englishNicknames.length];
  const number = (hash(`nick-num:${id}`) % 89) + 10;
  const moodHandle = `${moodWords[hash(`nick-mood:${id}`) % moodWords.length]}${handleObjects[hash(`nick-object:${id}`) % handleObjects.length]}`;
  const altMoodHandle = `${moodWords[hash(`nick-mood2:${id}`) % moodWords.length]}${handleObjects[hash(`nick-object2:${id}`) % handleObjects.length]}`;

  const styleCandidates = {
    short_alias: [
      shortAlias,
      moodHandle,
      altMoodHandle,
      `${shortAliasHandles[hash(`nick-short2:${id}`) % shortAliasHandles.length]}${(hash(`nick-num2:${id}`) % 89) + 10}`,
    ],
    food_daily: [
      base,
      moodHandle,
      altMoodHandle,
      `${nicknameBases[hash(`nick-base2:${id}`) % nicknameBases.length]}${(hash(`nick-num3:${id}`) % 89) + 10}`,
    ],
    person_suffix: [
      `${name}${suffix}`,
      person,
      `${koreanNameParts[hash(`nick-name2:${id}`) % koreanNameParts.length]}${nameSuffixes[hash(`nick-suffix3:${id}`) % nameSuffixes.length]}`,
    ],
    english: [
      english,
      `${english}${number}`,
      `${englishNicknames[hash(`nick-en2:${id}`) % englishNicknames.length]}_${number}`,
    ],
    family_pet: [
      family,
      `${family}${number}`,
      `${familyPetHandles[hash(`nick-family2:${id}`) % familyPetHandles.length]}${number}`,
    ],
    local: [
      place,
      `${place}${number}`,
      `${placeNicknameBases[hash(`nick-place2:${id}`) % placeNicknameBases.length]}${number}`,
    ],
    digit_variant: [
      `${base}${number}`,
      `${shortAlias}${number}`,
      `${place}${number}`,
    ],
  }[style];

  const candidates = [
    ...styleCandidates,
    shortAlias,
    base,
    place,
    person,
    `${name}${suffix}`,
    family,
    moodHandle,
    altMoodHandle,
    `${english}${number}`,
  ];

  for (const candidate of candidates.map((value) => value.slice(0, 30))) {
    if (candidate && !usedNicknames.has(candidate)) {
      usedNicknames.add(candidate);
      return candidate;
    }
  }

  let fallbackSuffix = 1;
  while (fallbackSuffix < 1000) {
    const fallback = `${nicknameBases[id % nicknameBases.length]}${fallbackSuffix}`.slice(0, 30);
    if (!usedNicknames.has(fallback)) {
      usedNicknames.add(fallback);
      return fallback;
    }
    fallbackSuffix += 1;
  }
  throw new Error(`failed to generate unique nickname for user ${id}`);
}

function daysInMonth(year, month) {
  return new Date(year, month, 0).getDate();
}

function birthProfileForUser(id) {
  const visibility = hash(`birth-visibility:${id}`) % 100;
  if (visibility < 12) {
    return { birth_year: null, birth_month: null, birth_day: null };
  }

  const ageRange = weightedByHash(
    [
      { value: [18, 24], weight: 22 },
      { value: [25, 34], weight: 38 },
      { value: [35, 44], weight: 24 },
      { value: [45, 54], weight: 10 },
      { value: [55, 64], weight: 5 },
      { value: [65, 69], weight: 1 },
    ],
    `age-range:${id}`
  );
  const age = ageRange[0] + (hash(`age:${id}`) % (ageRange[1] - ageRange[0] + 1));
  const birthYear = 2026 - age;
  const birthMonth = 1 + (hash(`month:${id}`) % 12);
  const birthDay = 1 + (hash(`day:${id}`) % daysInMonth(birthYear, birthMonth));

  if (visibility < 22) {
    return { birth_year: birthYear, birth_month: null, birth_day: null };
  }
  if (visibility < 30) {
    return { birth_year: birthYear, birth_month: birthMonth, birth_day: null };
  }
  return { birth_year: birthYear, birth_month: birthMonth, birth_day: birthDay };
}

function genderForUser(id) {
  const bucket = hash(`gender:${id}`) % 100;
  if (bucket < 31) return "FEMALE";
  if (bucket < 62) return "MALE";
  return null;
}

const users = [];
let userId = 1;
const usedNicknames = new Set();
for (const [name, count, categories, aspect, strictness, discovery] of personas) {
  for (let i = 0; i < count; i += 1) {
    const provider = providerForUser(userId);
    const birth = birthProfileForUser(userId);
    users.push({
      id: userId,
      persona: name,
      categories,
      aspect,
      strictness,
      discovery,
      provider,
      provider_user_id: providerUserIdFor(provider, userId),
      nickname: nicknameForUser(userId, usedNicknames),
      birth_year: birth.birth_year,
      birth_month: birth.birth_month,
      birth_day: birth.birth_day,
      gender: genderForUser(userId),
      profile_image_url: null,
      role: "USER",
      is_hidden: false,
      is_deleted: false,
      created_at: now,
      updated_at: now,
      deleted_at: null,
    });
    userId += 1;
  }
}

function categoryFit(user, restaurant) {
  return user.categories.includes(restaurant.categoryKey) ? 1 : 0;
}

function townFit(user, restaurant) {
  if (!isCheoinRestaurant(restaurant)) {
    return user.persona.includes("광역") || user.persona.includes("프리미엄") ? 0.2 : 0.05;
  }
  if (restaurant.town === "역북동" || restaurant.town === "남동") return 1;
  if (restaurant.town === "김량장동" || restaurant.town === "삼가동") return 0.6;
  return 0.25;
}

function isCheoinRestaurant(restaurant) {
  return String(restaurant.regionName || "") === "용인시 처인구";
}

function isYonginRestaurant(restaurant) {
  return String(restaurant.regionName || "").startsWith("용인시 ");
}

function candidatePoolForUser(user) {
  const cheoin = restaurants.filter(isCheoinRestaurant);
  if (user.persona.includes("광역") || user.persona.includes("프리미엄")) {
    const yongin = restaurants.filter(isYonginRestaurant);
    return yongin.length >= 80 ? yongin : cheoin;
  }
  return cheoin.length >= 80 ? cheoin : restaurants;
}

function restaurantWeight(user, restaurant) {
  const q = restaurant.quality.taste * 0.6 + restaurant.quality.value * 0.2 + restaurant.quality.mood * 0.2;
  let weight = 1 + q * 0.45 + categoryFit(user, restaurant) * 4 + townFit(user, restaurant) * 2;
  if (restaurant.exposure === "popular" && user.discovery === "popular_first") weight += 2.2;
  if (restaurant.exposure === "hidden" && user.discovery === "hidden_gem_finder") weight += 4.0;
  if (restaurant.role === "overexposed" && user.discovery !== "popular_first") weight -= 1.2;
  if (restaurant.role === "weak") weight -= 0.4;
  if (restaurant.role === "noisy") weight += 0.4;
  const count = evalCount.get(restaurant.id) || 0;
  if (count >= restaurant.maxEval) return 0;
  if (restaurant.role === "hidden_gem" && user.discovery !== "hidden_gem_finder" && count >= 3) return 0;
  return Math.max(0.05, weight);
}

function weightedSample(user, size, exclude = new Set()) {
  const selected = [];
  const pool = candidatePoolForUser(user);
  let guard = 0;
  while (selected.length < size && guard < 10000) {
    guard += 1;
    const exploratory = rng() < 0.18;
    const baseCandidates = pool.filter((restaurant) => !exclude.has(restaurant.id));
    const exploratoryCandidates = baseCandidates.filter(
      (restaurant) =>
        restaurant.role === "weak" ||
        restaurant.role === "noisy" ||
        restaurant.role === "overexposed" ||
        !categoryFit(user, restaurant)
    );
    const candidates = exploratory && exploratoryCandidates.length >= 10 ? exploratoryCandidates : baseCandidates;
    const weights = candidates.map((restaurant) => {
      if (!exploratory) return restaurantWeight(user, restaurant);
      const count = evalCount.get(restaurant.id) || 0;
      if (count >= restaurant.maxEval) return 0;
      let weight = 0.8;
      if (restaurant.role === "weak") weight += 2.8;
      if (restaurant.role === "noisy") weight += 2.0;
      if (restaurant.role === "overexposed") weight += 1.3;
      if (!categoryFit(user, restaurant)) weight += 1.0;
      return weight;
    });
    const total = weights.reduce((sum, value) => sum + value, 0);
    if (total <= 0) break;
    let ticket = rng() * total;
    let chosen = candidates[candidates.length - 1];
    for (let i = 0; i < candidates.length; i += 1) {
      ticket -= weights[i];
      if (ticket <= 0) {
        chosen = candidates[i];
        break;
      }
    }
    selected.push(chosen);
    exclude.add(chosen.id);
  }
  return selected;
}

function listSize() {
  const value = rng();
  if (value < 0.52) return 5 + Math.floor(rng() * 3);
  if (value < 0.88) return 8 + Math.floor(rng() * 5);
  return 13 + Math.floor(rng() * 8);
}

function scoreRestaurant(user, restaurant) {
  const strictnessBias = {
    very_generous: 0.9,
    generous: 0.45,
    neutral: 0,
    strict: -0.7,
    very_strict: -1.05,
  }[user.strictness] || 0;
  const fit = categoryFit(user, restaurant) ? 0.55 : -0.75;
  const discovery =
    restaurant.exposure === "hidden" && user.discovery === "hidden_gem_finder"
      ? 0.45
      : restaurant.exposure === "popular" && user.discovery === "popular_first"
      ? 0.3
      : 0;
  const experience = hashRatio(`experience:${user.id}:${restaurant.id}`);
  const badVisitPenalty = experience < 0.04 ? -2.2 : experience < 0.14 ? -1.15 : experience > 0.97 ? 0.8 : 0;
  const townPenalty = townFit(user, restaurant) < 0.3 ? -0.25 : 0;
  let taste =
    restaurant.quality.taste +
    strictnessBias +
    fit +
    discovery +
    badVisitPenalty +
    townPenalty +
    centeredHash(`taste-noise:${user.id}:${restaurant.id}`, 0.9);
  let value =
    restaurant.quality.value +
    strictnessBias +
    fit / 2 +
    badVisitPenalty * 0.85 +
    centeredHash(`value-noise:${user.id}:${restaurant.id}`, 1.0);
  let mood =
    restaurant.quality.mood +
    strictnessBias +
    fit / 2 +
    badVisitPenalty * 0.75 +
    centeredHash(`mood-noise:${user.id}:${restaurant.id}`, 1.0);
  if (user.aspect === "taste_first") taste += 0.4;
  if (user.aspect === "value_first") value += 0.5;
  if (user.aspect === "mood_first" || user.aspect === "mood_value") mood += 0.5;
  if (user.aspect === "taste_value") {
    taste += 0.25;
    value += 0.25;
  }
  if (user.aspect === "taste_mood") {
    taste += 0.25;
    mood += 0.25;
  }
  if (restaurant.role === "weak") {
    taste -= 0.9;
    value -= 0.7;
    mood -= 0.6;
  }
  if (restaurant.role === "overexposed") {
    value -= 0.45;
  }
  if (restaurant.role === "noisy") {
    const axis = hash(`noisy-axis:${restaurant.id}:${user.id}`) % 3;
    if (axis === 0) taste -= 1.0;
    if (axis === 1) value -= 1.0;
    if (axis === 2) mood -= 1.0;
  }
  taste = round1(clamp(taste, 1, 10));
  value = round1(clamp(value, 1, 10));
  mood = round1(clamp(mood, 1, 10));
  const auto = round1(clamp((taste * 0.58 + value * 0.22 + mood * 0.2) * 10, 5, 99));
  return { taste, value, mood, auto };
}

function listTitleFor(titleTown, situation, categoryLabel, index) {
  const nearbyTown = titleTown.endsWith("근처") ? titleTown : `${titleTown} 근처`;
  const occasion =
    {
      점심: "점심 먹을 때",
      저녁: "저녁 약속 전",
      혼밥: "혼자 먹을 때",
      데이트: "데이트 잡을 때",
      가성비: "가격도 같이 볼 때",
      "첫 방문": "처음 갈 때",
      재방문: "다시 가기 전",
    }[situation] || `${situation} 때`;
  const templates = [
    `${titleTown}에서 갈 곳 찾을 때 보는 곳`,
    `${titleTown} ${categoryLabel} 후보`,
    `${nearbyTown} 다음에 갈 곳`,
    `${titleTown} ${situation} 후보`,
    `${titleTown} ${categoryLabel} 저장해둔 곳`,
    `${titleTown}에서 가볍게 가기 좋은 곳`,
    `${nearbyTown} 다시 볼 식당`,
    `${occasion} 보는 ${titleTown}`,
    `${titleTown} ${categoryLabel} 고를 때 참고`,
    `${nearbyTown} 무난한 선택지`,
    `${titleTown}에서 한번 더 가볼 곳`,
    `${occasion} 보기 좋은 ${categoryLabel}`,
    `${categoryLabel} 생각날 때 ${titleTown}`,
    `${titleTown}에서 부담 없이 고를 곳`,
    `요즘 보는 ${titleTown} ${categoryLabel}`,
  ];
  return templates[hash(`list-title:${index}:${titleTown}:${situation}:${categoryLabel}`) % templates.length];
}

function listDescriptionFor(titleTown, situation, categoryLabel) {
  const descriptions = [
    `${titleTown} 근처에서 너무 멀리 움직이기 애매할 때 보려고 저장했습니다.`,
    `약속 잡을 때 메뉴랑 위치를 같이 보려고 정리해뒀습니다.`,
    `${categoryLabel}이 당길 때 바로 고르기 쉽게 남겨둔 개인 메모입니다.`,
    `후기만 보지 않고 동선, 가격, 같이 가는 사람 취향까지 같이 봤습니다.`,
    `다 가본 곳은 아니고, 다음에 가볼 만한 곳과 재방문 후보를 섞었습니다.`,
    `친구에게 물어봤을 때 바로 공유하려고 괜찮아 보이는 곳만 모았습니다.`,
    `혼자 가도 부담 없는 곳과 여럿이 가기 좋은 곳을 같이 넣었습니다.`,
    `평이 좋아도 너무 멀거나 애매한 곳은 빼고 다시 볼 곳만 남겼습니다.`,
    `${titleTown}에서 식사 후보가 빨리 필요할 때 보려고 만든 리스트입니다.`,
    `사진, 메뉴 구성, 이동 시간을 같이 보면서 천천히 추린 곳들입니다.`,
    `${situation} 기준으로 고르기 편한 곳만 따로 모았습니다.`,
    `맛도 중요하지만 대기, 가격, 이동 시간을 같이 봐야 할 때 꺼내봅니다.`,
  ];
  return descriptions[hash(`list-description:${titleTown}:${situation}:${categoryLabel}:${rng()}`) % descriptions.length];
}

const userLists = [];
const listRestaurants = [];
let listId = 1;
let listRestaurantId = 1;
for (const user of users) {
  const count = user.id <= 20 ? 4 : 1 + (hash(`lists:${user.id}`) % 5);
  for (let i = 0; i < count; i += 1) {
    const primaryCategory = user.categories[i % user.categories.length];
    const size = listSize();
    const publicList = (listId % 10) < 7;
    const titleTown = i % 3 === 0 ? "역북동" : i % 3 === 1 ? "처인구" : "명지대 근처";
    const situation = pick(["점심", "저녁", "혼밥", "데이트", "가성비", "첫 방문", "재방문"]);
    const categoryLabel = categoryLabels[primaryCategory] || "맛집";
    const title = listTitleFor(titleTown, situation, categoryLabel, listId);
    userLists.push({
      id: listId,
      user_id: user.id,
      title,
      description: listDescriptionFor(titleTown, situation, categoryLabel),
      region_name: "용인시 처인구",
      is_public: publicList,
      is_representative: i === 0,
      is_hidden: false,
      is_deleted: false,
      created_at: now,
      updated_at: now,
      deleted_at: null,
    });

    const selected = weightedSample(user, size);
    for (const restaurant of selected) {
      const score = scoreRestaurant(user, restaurant);
      listRestaurants.push({
        id: listRestaurantId,
        list_id: listId,
        restaurant_id: restaurant.id,
        taste_score: score.taste,
        value_score: score.value,
        mood_score: score.mood,
        auto_score: score.auto,
        created_at: now,
        updated_at: now,
      });
      evalCount.set(restaurant.id, (evalCount.get(restaurant.id) || 0) + 1);
      listRestaurantId += 1;
    }
    listId += 1;
  }
}

const follows = [];
let followId = 1;
const followPairs = new Set();
for (const user of users) {
  const similar = users
    .filter((other) => other.id !== user.id && other.persona === user.persona)
    .slice(0, 8);
  const targets = [...similar, ...users.filter((other) => other.id !== user.id && other.id % 17 === user.id % 17)].slice(0, 8);
  for (const target of targets) {
    const key = `${user.id}:${target.id}`;
    if (followPairs.has(key)) continue;
    followPairs.add(key);
    follows.push({ id: followId, follower_id: user.id, following_id: target.id, created_at: now });
    followId += 1;
  }
}

const listLikes = [];
let listLikeId = 1;
const likePairs = new Set();
for (const list of userLists.filter((item) => item.is_public)) {
  const listRows = listRestaurants.filter((row) => row.list_id === list.id);
  const avg = listRows.reduce((sum, row) => sum + Number(row.auto_score), 0) / Math.max(1, listRows.length);
  const likeTarget =
    avg >= 86
      ? 12 + Math.floor(rng() * 14)
      : avg >= 76
      ? 5 + Math.floor(rng() * 12)
      : avg >= 64
      ? Math.floor(rng() * 8)
      : rng() < 0.55
      ? 0
      : 1 + Math.floor(rng() * 3);
  const owner = users.find((user) => user.id === list.user_id);
  const candidates = users
    .filter((user) => user.id !== list.user_id)
    .sort((left, right) => {
      const leftFit = left.categories.some((category) => owner.categories.includes(category)) ? 1 : 0;
      const rightFit = right.categories.some((category) => owner.categories.includes(category)) ? 1 : 0;
      return rightFit - leftFit || (hash(`${list.id}:${left.id}`) - hash(`${list.id}:${right.id}`));
    });
  for (const user of candidates.slice(0, likeTarget)) {
    const key = `${user.id}:${list.id}`;
    if (likePairs.has(key)) continue;
    likePairs.add(key);
    listLikes.push({ id: listLikeId, user_id: user.id, list_id: list.id, created_at: now });
    listLikeId += 1;
  }
}

const reviews = [];
let reviewId = 1;
const reviewPairs = new Set();
const sortedListRestaurants = [...listRestaurants].sort((left, right) => right.auto_score - left.auto_score);
const reviewCandidateOrder = [...listRestaurants].sort(
  (left, right) => hash(`review-order:${left.id}`) - hash(`review-order:${right.id}`)
);

function isReviewableMenu(menu) {
  const name = normalizeText(menu?.name);
  if (!name) return false;
  if (name.length < 2 || name.length > 32) return false;
  if (/^\d+$/.test(name)) return false;
  if (/(개월|무료|쿠폰|혜택|생일|이벤트|리뷰|영수증|예약|대관|문의|변동|시가|추가|옵션|포장|배달|주차|어린이|미만|이용권|멤버십|선불|후불|콜키지|서비스|발레코어)/.test(name)) {
    return false;
  }
  return true;
}

function reviewMenuFor(restaurant) {
  const reviewable = restaurant.menus.filter(isReviewableMenu);
  if (reviewable.length > 0) return pick(reviewable);
  return null;
}

function cleanMenuDisplayName(value) {
  return normalizeText(value)
    .replace(/\s*\([^)]*\)/g, "")
    .replace(/\s+(?:HOT|ICE|Hot|Ice|hot|ice)(?:\s*\/\s*(?:HOT|ICE|Hot|Ice|hot|ice))?/g, "")
    .replace(/\s*\d{1,3}(?:,\d{3})*원(?:부터)?(?:\s*~)?\s*$/g, "")
    .replace(/\s*\d{1,3}(?:,\d{3})*원.*$/g, "")
    .trim();
}

function reviewContentFor(restaurant, score) {
  const categoryLabel = categoryLabels[restaurant.categoryKey] || "식사";
  const town = restaurant.town || "동네";
  const menu = reviewMenuFor(restaurant);
  const cleanedMenuName = cleanMenuDisplayName(menu?.name);
  const menuName = cleanedMenuName || categoryLabel;
  const visitContext = pick(["점심에", "저녁에", "주말에", "근처 들른 김에", "약속 전에", "혼자 가볍게", "친구랑"]);
  const priceFeeling = pick([
    "가격은 예상한 정도였습니다.",
    "요즘 물가 생각하면 크게 부담되진 않았어요.",
    "가격만 보면 아주 싸진 않지만 납득은 됩니다.",
    "양까지 보면 아깝다는 느낌은 덜했습니다.",
  ]);
  const operationalNote = pick([
    "피크 시간에는 조금 기다릴 수 있겠어요.",
    "직원분 응대는 과하지 않고 편했습니다.",
    "테이블 간격은 넓진 않지만 오래 불편하진 않았습니다.",
    "매장 회전이 빨라서 오래 기다리진 않았습니다.",
    "포장 손님도 꽤 보였습니다.",
    "주차는 시간대에 따라 난이도가 달라질 것 같습니다.",
  ]);
  const categoryPros = {
    korean: ["반찬이 과하게 짜지 않고 밥이랑 잘 맞았습니다.", "국물이나 찌개류가 생각보다 깔끔했습니다.", "편하게 한 끼 먹기 좋은 집밥 느낌이 있습니다."],
    street: ["간단히 먹기 좋고 회전도 빠른 편이었습니다.", "양이 생각보다 넉넉해서 가볍게 들르기 좋았습니다.", "분식류는 같이 나눠 먹기 편한 구성이었습니다."],
    chinese: ["간이 분명한 편인데 부담스럽게 세진 않았습니다.", "면 식감이 괜찮고 소스가 따로 놀지 않았어요.", "여럿이 가서 나눠 먹기 좋은 메뉴가 보였습니다."],
    japanese: ["혼밥하기에도 크게 부담 없는 분위기였습니다.", "밥이나 면 메뉴 마감이 깔끔한 편이었습니다.", "소스와 재료 조합이 무난하게 잘 맞았습니다."],
    meat: ["고기 잡내가 거의 없고 굽는 동안 먹기 편했습니다.", "사이드까지 같이 시키면 구성이 더 괜찮아집니다.", "고기 질은 가격대 생각하면 납득되는 편입니다."],
    cafe: ["음료가 너무 달지 않아서 끝까지 마시기 좋았습니다.", "커피랑 디저트 조합이 무난하게 잘 맞았습니다.", "잠깐 앉아 이야기하기 좋은 분위기였습니다."],
    western: ["소스가 과하지 않고 전체적으로 깔끔했습니다.", "가볍게 외식할 때 고르기 좋은 분위기였습니다.", "플레이팅이 깔끔해서 첫인상이 괜찮았습니다."],
    bar: ["안주 구성이 무난해서 한잔하기 편했습니다.", "저녁 시간대 분위기가 너무 들뜨지 않아 좋았습니다.", "메뉴가 과하게 자극적이지 않아 오래 먹기 괜찮았습니다."],
    fast: ["빠르게 먹기 좋고 포장하기도 편해 보였습니다.", "기본 메뉴가 안정적인 편이라 크게 튀지 않습니다.", "간단한 한 끼로 부담이 크지 않았습니다."],
    healthy: ["재료가 가볍고 먹고 나서 속이 편했습니다.", "채소와 소스 밸런스가 생각보다 괜찮았습니다.", "부담 없이 먹을 수 있는 메뉴가 있어서 좋았습니다."],
  }[restaurant.categoryKey] || ["전체적으로 무난하게 먹기 좋았습니다."];
  const pro = pick(categoryPros);
  const highScoreTemplates = [
    `${visitContext} ${restaurant.name}에 들렀는데 ${subject(menuName)} 생각보다 괜찮았습니다. ${pro} 다음에도 근처 오면 다시 갈 것 같아요.`,
    `${menuName} 주문했는데 양이랑 간이 둘 다 맞았습니다. ${operationalNote} 같이 간 사람들도 별말 없이 잘 먹었습니다.`,
    `${topic(restaurant.name)} 메뉴 고르기가 어렵지 않아서 좋았습니다. ${menuName} 기준으로 맛이 안정적이고 ${priceFeeling}`,
    `${town} 근처에서 약속 잡을 때 후보로 넣기 좋겠습니다. ${pro} ${operationalNote}`,
    `큰 기대 없이 ${restaurant.name}에 갔는데 꽤 만족스러웠습니다. ${topic(menuName)} 다음에 가도 다시 시킬 것 같아요.`,
    `${menuName} 먹고 왔습니다. 간이 튀지 않고 마무리가 깔끔해서 기억에 남았습니다. ${operationalNote}`,
    `${topic(restaurant.name)} 재방문해도 괜찮겠다는 생각이 들었습니다. ${pro} ${priceFeeling}`,
  ];
  const neutralTemplates = [
    `${topic(restaurant.name)} 전반적으로 무난했습니다. ${topic(menuName)} 괜찮았고 가까우면 한 번 더 들를 정도입니다.`,
    `${categoryLabel} 선택지로 나쁘지 않습니다. 특별한 맛집이라기보다는 편하게 가기 좋은 쪽에 가깝습니다. ${operationalNote}`,
    `${town}에서 가볍게 먹기 좋았습니다. ${topic(menuName)} 무난했고 재방문은 그날 상황에 따라 달라질 것 같아요.`,
    `${topic(restaurant.name)} 장점과 아쉬움이 같이 있습니다. ${pro} 다만 일부 메뉴는 취향을 탈 수 있어요.`,
    `${menuName} 먹어봤는데 기대만큼 특별하진 않아도 기본은 합니다. 근처에서 바로 먹을 곳 찾을 때 괜찮습니다.`,
    `${visitContext} 들르기엔 괜찮았습니다. ${priceFeeling} 다만 멀리서 일부러 찾아갈 정도인지는 조금 애매합니다.`,
  ];
  const lowScoreTemplates = [
    `${topic(restaurant.name)} 기대했던 것보다는 평범했습니다. ${topic(menuName)} 나쁘진 않았지만 맛 기준으로는 호불호가 있을 것 같아요.`,
    `${subject(categoryLabel)} 생각날 때 대안은 되지만, ${topic(menuName)} 가격 대비 만족도가 높지는 않았습니다.`,
    `${town} 근처라 들르기 편한 점은 좋았습니다. 다만 ${topic(menuName)} 제 입맛에는 조금 아쉬워서 재방문은 고민됩니다.`,
    `${topic(restaurant.name)} 위치는 편한데 음식 인상은 무난한 정도였습니다. 대기나 응대는 방문 시간에 따라 차이가 있을 것 같아요.`,
    `${visitContext} 방문했는데 기대보다는 아쉬웠습니다. ${topic(menuName)} 나쁘진 않았지만 다시 생각날 정도는 아니었습니다.`,
    `${topic(restaurant.name)} 가까우면 들를 수는 있겠지만 일부러 찾아가진 않을 것 같습니다. ${priceFeeling}`,
  ];
  if (score >= 82) return pick(highScoreTemplates);
  if (score >= 70) return pick(neutralTemplates);
  return pick(lowScoreTemplates);
}

function addReviewFromListRestaurant(row) {
  const list = userLists[row.list_id - 1];
  const restaurant = byId.get(row.restaurant_id);
  const key = `${list.user_id}:${row.restaurant_id}`;
  if (reviewPairs.has(key)) return false;
  reviewPairs.add(key);
  reviews.push({
    id: reviewId,
    user_id: list.user_id,
    restaurant_id: row.restaurant_id,
    content: reviewContentFor(restaurant, row.auto_score),
    is_hidden: false,
    is_deleted: false,
    created_at: now,
    updated_at: now,
    deleted_at: null,
    score: row.auto_score,
  });
  reviewId += 1;
  return true;
}

for (const segment of [
  { min: 85, max: 100, target: 950 },
  { min: 72, max: 85, target: 760 },
  { min: 55, max: 72, target: 360 },
  { min: 0, max: 55, target: 130 },
]) {
  let added = 0;
  const rows = reviewCandidateOrder
    .filter((row) => row.auto_score >= segment.min && row.auto_score < segment.max)
    .sort((left, right) => right.auto_score - left.auto_score || left.id - right.id);
  for (const row of rows) {
    if (added >= segment.target || reviews.length >= 2200) break;
    if (addReviewFromListRestaurant(row)) added += 1;
  }
}

for (const row of sortedListRestaurants) {
  if (reviews.length >= 2200) break;
  addReviewFromListRestaurant(row);
}

const reviewVotes = [];
let reviewVoteId = 1;
const votePairs = new Set();
for (const review of reviews) {
  const voteCount = review.score >= 85 ? 5 : review.score >= 75 ? 3 : 1;
  const candidates = users.filter((user) => user.id !== review.user_id).slice(0, voteCount + 5);
  for (const voter of candidates.slice(0, voteCount)) {
    const key = `${voter.id}:${review.id}`;
    if (votePairs.has(key)) continue;
    votePairs.add(key);
    reviewVotes.push({
      id: reviewVoteId,
      user_id: voter.id,
      review_id: review.id,
      vote_type: review.score < 60 && rng() < 0.35 ? "DISLIKE" : "LIKE",
      created_at: now,
    });
    reviewVoteId += 1;
  }
}

const reliabilityScores = users.map((user, index) => {
  const activity = userLists.filter((list) => list.user_id === user.id).length * 8 +
    reviews.filter((review) => review.user_id === user.id).length * 3 +
    follows.filter((follow) => follow.follower_id === user.id).length;
  const strictBonus = user.strictness === "strict" || user.strictness === "very_strict" ? 4 : 0;
  const score = round1(clamp(55 + activity * 0.9 + strictBonus + (hash(`rel:${user.id}`) % 20), 35, 98));
  const grade = score >= 90 ? "S" : score >= 80 ? "A" : score >= 65 ? "B" : "C";
  return {
    id: index + 1,
    user_id: user.id,
    score,
    grade,
    honor_title: grade === "S" ? "동네 맛집 큐레이터" : grade === "A" ? "신뢰 리뷰어" : null,
    honor_period: grade === "S" || grade === "A" ? "2026-H1" : null,
    activity_index: round1(activity),
    last_activity_at: now,
    created_at: now,
    updated_at: now,
  };
});

const reports = [];
let reportId = 1;
for (const review of reviews.filter((item) => item.score < 62).slice(0, 45)) {
  reports.push({
    id: reportId,
    reporter_id: 1 + (reportId % users.length),
    target_type: "REVIEW",
    target_id: review.id,
    reason: "내용이 부정확하거나 과장된 리뷰로 보여요",
    is_auto: reportId % 3 === 0,
    status: reportId % 4 === 0 ? "RESOLVED" : "PENDING",
    created_at: now,
    updated_at: now,
  });
  reportId += 1;
}
while (reports.length < 36) {
  const targetIsUser = reports.length % 3 === 0;
  reports.push({
    id: reportId,
    reporter_id: 1 + ((reportId * 7) % users.length),
    target_type: targetIsUser ? "USER" : "LIST",
    target_id: targetIsUser ? 1 + ((reportId * 11) % users.length) : 1 + ((reportId * 13) % userLists.length),
    reason: targetIsUser ? "비정상적인 활동 패턴이 의심돼요" : "광고성 리스트로 보여요",
    is_auto: reportId % 2 === 0,
    status: reportId % 5 === 0 ? "RESOLVED" : "PENDING",
    created_at: now,
    updated_at: now,
  });
  reportId += 1;
}

writeCsv(
  "users.csv",
  ["id", "provider", "provider_user_id", "nickname", "birth_year", "birth_month", "birth_day", "gender", "profile_image_url", "role", "is_hidden", "is_deleted", "created_at", "updated_at", "deleted_at"],
  users
);
writeCsv(
  "reliability_scores.csv",
  ["id", "user_id", "score", "grade", "honor_title", "honor_period", "activity_index", "last_activity_at", "created_at", "updated_at"],
  reliabilityScores
);
writeCsv("user_follows.csv", ["id", "follower_id", "following_id", "created_at"], follows);
writeCsv(
  "user_lists.csv",
  ["id", "user_id", "title", "description", "region_name", "is_public", "is_representative", "is_hidden", "is_deleted", "created_at", "updated_at", "deleted_at"],
  userLists
);
writeCsv(
  "list_restaurants.csv",
  ["id", "list_id", "restaurant_id", "taste_score", "value_score", "mood_score", "auto_score", "created_at", "updated_at"],
  listRestaurants
);
writeCsv("list_likes.csv", ["id", "user_id", "list_id", "created_at"], listLikes);
writeCsv(
  "reviews.csv",
  ["id", "user_id", "restaurant_id", "content", "is_hidden", "is_deleted", "created_at", "updated_at", "deleted_at"],
  reviews
);
writeCsv("review_votes.csv", ["id", "user_id", "review_id", "vote_type", "created_at"], reviewVotes);
writeCsv(
  "reports.csv",
  ["id", "reporter_id", "target_type", "target_id", "reason", "is_auto", "status", "created_at", "updated_at"],
  reports
);

const roleCounts = restaurants.reduce((acc, restaurant) => {
  acc[restaurant.role] = (acc[restaurant.role] || 0) + 1;
  return acc;
}, {});

fs.writeFileSync(
  path.join(outputDir, "mock_generation_summary.json"),
  JSON.stringify(
    {
      restaurants: restaurants.length,
      restaurantRoles: roleCounts,
      users: users.length,
      lists: userLists.length,
      publicLists: userLists.filter((list) => list.is_public).length,
      listRestaurants: listRestaurants.length,
      listLikes: listLikes.length,
      follows: follows.length,
      reviews: reviews.length,
      reviewVotes: reviewVotes.length,
      reports: reports.length,
      generatedAt: now,
    },
    null,
    2
  ),
  "utf8"
);

console.log(
  `users=${users.length} lists=${userLists.length} listRestaurants=${listRestaurants.length} reviews=${reviews.length}`
);
