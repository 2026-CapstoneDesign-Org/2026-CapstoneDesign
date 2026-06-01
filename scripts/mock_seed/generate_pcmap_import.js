#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const enrichedPath = path.resolve(process.env.ENRICHED_JSONL || "");
const existingPcmapIdsPath = path.resolve(process.env.EXISTING_PCMAP_IDS || "");
const outputDir = path.resolve(process.env.OUTPUT_DIR || "tmp/mock_seed_import");
const restaurantIdStart = Number(process.env.RESTAURANT_ID_START || 1);
const menuIdStart = Number(process.env.MENU_ID_START || 1);

if (!enrichedPath || !fs.existsSync(enrichedPath)) {
  throw new Error(`ENRICHED_JSONL not found: ${enrichedPath}`);
}

function readExistingPcmapIds() {
  if (!existingPcmapIdsPath || !fs.existsSync(existingPcmapIdsPath)) {
    return new Set();
  }
  return new Set(
    fs
      .readFileSync(existingPcmapIdsPath, "utf8")
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean)
  );
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
  const text = String(value || "").trim();
  return text || null;
}

function normalizeName(value) {
  return String(value || "")
    .normalize("NFKC")
    .replace(/\s+/g, "")
    .replace(/[^\p{L}\p{N}]/gu, "")
    .toLowerCase();
}

function parsePriceValue(priceText) {
  const priceToken = String(priceText || "").match(/\d{1,3}(?:,\d{3})+|\d{4,}/)?.[0];
  if (!priceToken) {
    return null;
  }
  const value = Number(priceToken.replace(/,/g, ""));
  return Number.isFinite(value) && value < 10_000_000 ? value : null;
}

function compactJson(value) {
  if (!value || (Array.isArray(value) && value.length === 0)) {
    return null;
  }
  return JSON.stringify(value);
}

function chooseDetail(row) {
  return row.pcmap_detail && Object.keys(row.pcmap_detail).length > 0
    ? row.pcmap_detail
    : row.pcmap_candidate || {};
}

function isTownToken(token) {
  const text = normalizeText(token);
  if (!text || !/^[가-힣]{1,8}$/.test(text)) return false;
  if (/(냉면|라면|쫄면|짜장면|짬뽕면|우동|만두|국밥|해장국)$/.test(text)) return false;
  return /(동|읍|면|가|리)$/.test(text);
}

function extractTown(addressLike, sigunguName) {
  const tokens = String(addressLike || "").split(/\s+/).filter(Boolean);
  const sigunguTokens = String(sigunguName || "").split(/\s+/).filter(Boolean);
  const sigunguStart =
    sigunguTokens.length > 0
      ? tokens.findIndex((token, index) =>
          sigunguTokens.every((part, offset) => tokens[index + offset] === part)
        )
      : -1;
  const searchStart = sigunguStart >= 0 ? sigunguStart + sigunguTokens.length : 0;
  return tokens.slice(searchStart).find(isTownToken) || tokens.find(isTownToken) || null;
}

function normalizeSido(value) {
  const text = normalizeText(value);
  if (!text) {
    return null;
  }
  const aliases = {
    서울: "서울특별시",
    부산: "부산광역시",
    대구: "대구광역시",
    인천: "인천광역시",
    광주: "광주광역시",
    대전: "대전광역시",
    울산: "울산광역시",
    세종: "세종특별자치시",
    경기: "경기도",
    강원: "강원특별자치도",
    충북: "충청북도",
    충남: "충청남도",
    전북: "전북특별자치도",
    전남: "전라남도",
    경북: "경상북도",
    경남: "경상남도",
    제주: "제주특별자치도",
  };
  const fullNames = new Set(Object.values(aliases));
  return aliases[text] || (fullNames.has(text) ? text : null);
}

function shortSido(value) {
  const text = normalizeSido(value);
  if (!text) return null;
  return {
    서울특별시: "서울",
    부산광역시: "부산",
    대구광역시: "대구",
    인천광역시: "인천",
    광주광역시: "광주",
    대전광역시: "대전",
    울산광역시: "울산",
    세종특별자치시: "세종",
    경기도: "경기",
    강원특별자치도: "강원",
    충청북도: "충북",
    충청남도: "충남",
    전북특별자치도: "전북",
    전라남도: "전남",
    경상북도: "경북",
    경상남도: "경남",
    제주특별자치도: "제주",
  }[text] || text;
}

function extractSidoFromAddress(addressLike) {
  return normalizeSido(String(addressLike || "").split(/\s+/).filter(Boolean)[0]);
}

function buildRegionPartsFromAddress(addressLike, fallbackSidoName, fallbackSigunguName) {
  const tokens = String(addressLike || "").split(/\s+/).filter(Boolean);
  const sido = normalizeSido(tokens[0]) || normalizeSido(fallbackSidoName);
  const sidoShort = shortSido(sido);
  const fallbackSigungu = normalizeText(fallbackSigunguName);

  if (!sido && !fallbackSigungu) {
    return {
      sidoFull: null,
      sidoShort: null,
      city: null,
      district: null,
      regionName: null,
    };
  }

  if (sido && /(특별시|광역시)$/.test(sido)) {
    const district = tokens[1] || fallbackSigungu;
    return {
      sidoFull: sido,
      sidoShort,
      city: sido,
      district,
      regionName: district ? `${sido} ${district}` : sido,
    };
  }

  if (sido === "세종특별자치시") {
    return {
      sidoFull: sido,
      sidoShort,
      city: sido,
      district: null,
      regionName: sido,
    };
  }

  const city = tokens[1] || fallbackSigungu;
  const district = tokens[2] && tokens[2].endsWith("구") ? tokens[2] : null;
  if (city && district) {
    return {
      sidoFull: sido,
      sidoShort,
      city,
      district,
      regionName: `${city} ${district}`,
    };
  }
  if (city) {
    return {
      sidoFull: sido,
      sidoShort,
      city,
      district: null,
      regionName: sidoShort && !/(특별시|광역시|특별자치시)$/.test(sido) ? `${sidoShort} ${city}` : city,
    };
  }
  return {
    sidoFull: sido,
    sidoShort,
    city: fallbackSigungu || sido,
    district: null,
    regionName: fallbackSigungu || sido,
  };
}

function extractRegion(row, detail) {
  const addressLike =
    [
      detail.address,
      detail.roadAddress,
      row.pcmap_candidate?.fullAddress,
      row.pcmap_candidate?.commonAddress,
      row.region_name,
    ]
      .map(normalizeText)
      .find((value) => normalizeSido(String(value || "").split(/\s+/).filter(Boolean)[0])) || row.region_name;
  const parts = buildRegionPartsFromAddress(
    addressLike,
    row.sido_name || extractSidoFromAddress(addressLike),
    row.sigungu_name || row.region_name
  );
  const sigunguAnchor = parts.district ? `${parts.city} ${parts.district}` : parts.city;
  const town =
    normalizeText(row.town_name) ||
    extractTown(detail.address, sigunguAnchor) ||
    extractTown(detail.roadAddress, sigunguAnchor) ||
    extractTown(row.pcmap_candidate?.commonAddress, sigunguAnchor) ||
    extractTown(row.pcmap_candidate?.fullAddress, sigunguAnchor) ||
    normalizeText(row.town_name);
  return {
    sidoFull: parts.sidoFull,
    sidoShort: parts.sidoShort,
    city: parts.city || normalizeText(row.sido_name) || "기타",
    district: parts.district,
    town,
    regionName: parts.regionName || normalizeText(row.region_name) || parts.city || "기타",
  };
}

function primaryCategory(category) {
  const text = String(category || "");
  if (text.includes(",")) {
    return text.split(",")[0].trim();
  }
  return normalizeText(text);
}

const existingPcmapIds = readExistingPcmapIds();
const byPlaceId = new Map();
for (const line of fs.readFileSync(enrichedPath, "utf8").split(/\r?\n/)) {
  if (!line.trim()) {
    continue;
  }
  const row = JSON.parse(line);
  const placeId = normalizeText(row.pcmap_candidate?.placeId || row.pcmap_detail?.placeId);
  if (row.status !== "MATCHED" || !placeId || existingPcmapIds.has(placeId)) {
    continue;
  }
  const current = byPlaceId.get(placeId);
  if (!current || Number(row.match_score || 0) > Number(current.match_score || 0)) {
    byPlaceId.set(placeId, row);
  }
}

const now = new Date().toISOString();
const restaurantRows = [];
const menuRows = [];
let nextRestaurantId = restaurantIdStart;
let nextMenuId = menuIdStart;

for (const row of byPlaceId.values()) {
  const detail = chooseDetail(row);
  const candidate = row.pcmap_candidate || {};
  const region = extractRegion(row, detail);
  const restaurantId = nextRestaurantId;
  nextRestaurantId += 1;

  const category = normalizeText(detail.category || candidate.category);
  const imageUrl = normalizeText(detail.imageUrl || candidate.imageUrl);
  const address = normalizeText(detail.address || candidate.fullAddress || candidate.address || row.region_name);
  const roadAddress = normalizeText(detail.roadAddress || candidate.roadAddress);
  const conveniences = compactJson(detail.conveniences);
  const businessHours = compactJson(detail.businessHours || detail.newBusinessHours || candidate.newBusinessHours);
  const regionFilterNames = [
    region.sidoShort,
    region.sidoFull,
    region.city,
    region.district,
    region.town,
    region.district && region.sidoShort && region.city === region.sidoFull
      ? `${region.sidoShort} ${region.district}`
      : null,
    region.district && region.sidoShort && region.city !== region.sidoFull
      ? `${region.sidoShort} ${region.city} ${region.district}`
      : null,
    region.regionName,
  ].filter((value, index, values) => value && values.indexOf(value) === index);

  restaurantRows.push({
    id: restaurantId,
    name: normalizeText(detail.name || candidate.name || row.raw_name),
    address,
    region_name: region.regionName,
    lat: normalizeText(detail.y || candidate.y),
    lng: normalizeText(detail.x || candidate.x),
    image_url: imageUrl,
    is_hidden: false,
    is_deleted: false,
    created_at: now,
    updated_at: now,
    deleted_at: null,
    region_city_name: region.city,
    region_district_name: region.district,
    region_county_name: null,
    region_filter_names: JSON.stringify(regionFilterNames),
    pcmap_place_id: normalizeText(detail.placeId || candidate.placeId),
    menu_updated_at: now,
    category_name: category,
    road_address: roadAddress,
    region_town_name: region.town,
    business_hours_raw: businessHours,
    conveniences,
    phone_number: normalizeText(detail.phone || detail.virtualPhone || candidate.telephone),
    primary_category_name: primaryCategory(category),
  });

  const menus = Array.isArray(detail.menus) ? detail.menus.slice(0, 10) : [];
  menus.forEach((menu, index) => {
    const menuName = normalizeText(menu.name);
    const normalizedMenuName = normalizeName(menuName);
    if (!menuName || !normalizedMenuName) {
      return;
    }
    const priceText = normalizeText(menu.price || menu.priceText);
    menuRows.push({
      id: nextMenuId,
      restaurant_id: restaurantId,
      display_order: index,
      menu_name: menuName,
      normalized_menu_name: normalizedMenuName,
      menu_tag_key: null,
      price_text: priceText,
      price_value: parsePriceValue(priceText),
      description: normalizeText(menu.description),
      created_at: now,
      updated_at: now,
    });
    nextMenuId += 1;
  });
}

writeCsv(
  "restaurants_enriched.csv",
  [
    "id",
    "name",
    "address",
    "region_name",
    "lat",
    "lng",
    "image_url",
    "is_hidden",
    "is_deleted",
    "created_at",
    "updated_at",
    "deleted_at",
    "region_city_name",
    "region_district_name",
    "region_county_name",
    "region_filter_names",
    "pcmap_place_id",
    "menu_updated_at",
    "category_name",
    "road_address",
    "region_town_name",
    "business_hours_raw",
    "conveniences",
    "phone_number",
    "primary_category_name",
  ],
  restaurantRows
);

writeCsv(
  "restaurant_menu_items_enriched.csv",
  [
    "id",
    "restaurant_id",
    "display_order",
    "menu_name",
    "normalized_menu_name",
    "menu_tag_key",
    "price_text",
    "price_value",
    "description",
    "created_at",
    "updated_at",
  ],
  menuRows
);

fs.writeFileSync(
  path.join(outputDir, "pcmap_import_summary.json"),
  JSON.stringify(
    {
      enrichedRows: byPlaceId.size,
      restaurantIdStart,
      nextRestaurantId,
      menuRows: menuRows.length,
      menuIdStart,
      nextMenuId,
      generatedAt: now,
    },
    null,
    2
  ),
  "utf8"
);

console.log(
  `restaurants=${restaurantRows.length} menuItems=${menuRows.length} output=${outputDir}`
);
