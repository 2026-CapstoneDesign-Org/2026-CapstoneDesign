const fs = require("fs");
const path = require("path");

const restaurantsCsvPath = path.resolve(
  process.env.RESTAURANTS_CSV || "tmp/mock_seed_import/mock_restaurants_current.csv"
);
const outputSqlPath = path.resolve(
  process.env.REGION_UPDATE_SQL || "tmp/mock_seed_import/restaurant_region_updates.sql"
);
const outputReportPath = path.resolve(
  process.env.REGION_AUDIT_REPORT || "tmp/mock_seed_import/restaurant_region_audit.json"
);

function parseCsv(text) {
  const rows = [];
  let row = [];
  let field = "";
  let quoted = false;
  for (let index = 0; index < text.length; index += 1) {
    const ch = text[index];
    if (quoted) {
      if (ch === '"' && text[index + 1] === '"') {
        field += '"';
        index += 1;
      } else if (ch === '"') {
        quoted = false;
      } else {
        field += ch;
      }
    } else if (ch === '"') {
      quoted = true;
    } else if (ch === ",") {
      row.push(field);
      field = "";
    } else if (ch === "\n") {
      row.push(field);
      rows.push(row);
      row = [];
      field = "";
    } else if (ch !== "\r") {
      field += ch;
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

function normalizeText(value) {
  if (value === null || value === undefined) return null;
  const text = String(value).trim().replace(/\s+/g, " ");
  return text.length > 0 ? text : null;
}

function normalizeSido(value) {
  const text = normalizeText(value);
  if (!text) return null;
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
  return (
    {
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
    }[text] || text
  );
}

function isTownToken(token) {
  const text = normalizeText(token);
  if (!text || !/^[가-힣]{1,8}$/.test(text)) return false;
  if (/(냉면|라면|쫄면|짜장면|짬뽕면|우동|만두|국밥|해장국)$/.test(text)) return false;
  return /(동|읍|면|가|리)$/.test(text);
}

function findTown(tokens, startIndex) {
  return tokens.slice(startIndex).find(isTownToken) || null;
}

function parseRegion(row) {
  const source = [row.road_address, row.address]
    .map(normalizeText)
    .find((value) => normalizeSido(String(value || "").split(/\s+/).filter(Boolean)[0]));
  if (!source) return null;
  const tokens = source.split(/\s+/).filter(Boolean);
  const townTokens = (normalizeText(row.address) || source).split(/\s+/).filter(Boolean);
  const sidoFull = normalizeSido(tokens[0]);
  const sidoShort = shortSido(sidoFull);
  if (!sidoFull) return null;

  if (/(특별시|광역시)$/.test(sidoFull)) {
    const district = normalizeText(tokens[1]);
    const town = findTown(townTokens, 2) || normalizeText(row.region_town_name);
    return {
      regionName: district ? `${sidoFull} ${district}` : sidoFull,
      city: sidoFull,
      district,
      town,
      filters: [
        sidoShort,
        sidoFull,
        district,
        town,
        district && sidoShort ? `${sidoShort} ${district}` : null,
        district ? `${sidoFull} ${district}` : sidoFull,
      ],
    };
  }

  if (sidoFull === "세종특별자치시") {
    const town = findTown(townTokens, 1) || normalizeText(row.region_town_name);
    return {
      regionName: sidoFull,
      city: sidoFull,
      district: null,
      town,
      filters: [sidoShort, sidoFull, town],
    };
  }

  const city = normalizeText(tokens[1]);
  if (!city) return null;
  const district = tokens[2] && tokens[2].endsWith("구") ? normalizeText(tokens[2]) : null;
  const town = findTown(townTokens, district ? 3 : 2) || normalizeText(row.region_town_name);
  const regionName = district ? `${city} ${district}` : `${sidoShort} ${city}`;
  return {
    regionName,
    city,
    district,
    town,
    filters: [
      sidoShort,
      sidoFull,
      city,
      district,
      town,
      district && sidoShort ? `${sidoShort} ${city} ${district}` : null,
      regionName,
    ],
  };
}

function unique(values) {
  return [...new Set(values.filter(Boolean))];
}

function sqlString(value) {
  if (value === null || value === undefined || value === "") return "NULL";
  return `'${String(value).replace(/'/g, "''")}'`;
}

function sqlJson(value) {
  return sqlString(JSON.stringify(unique(value)));
}

function main() {
  const rows = parseCsv(fs.readFileSync(restaurantsCsvPath, "utf8"));
  const updates = [];
  const samples = [];
  const regionCounts = new Map();
  let parseFailures = 0;

  for (const row of rows) {
    const parsed = parseRegion(row);
    if (!parsed) {
      parseFailures += 1;
      continue;
    }
    const next = {
      region_name: parsed.regionName,
      region_city_name: parsed.city,
      region_district_name: parsed.district,
      region_town_name: parsed.town,
      region_filter_names: JSON.stringify(unique(parsed.filters)),
    };
    regionCounts.set(parsed.regionName, (regionCounts.get(parsed.regionName) || 0) + 1);
    const changed = Object.entries(next).some(([key, value]) => normalizeText(row[key]) !== normalizeText(value));
    if (!changed) continue;

    updates.push(`UPDATE restaurants SET region_name = ${sqlString(next.region_name)}, region_city_name = ${sqlString(next.region_city_name)}, region_district_name = ${sqlString(next.region_district_name)}, region_county_name = NULL, region_town_name = ${sqlString(next.region_town_name)}, region_filter_names = ${sqlJson(parsed.filters)}, updated_at = updated_at WHERE id = ${Number(row.id)};`);
    if (samples.length < 30) {
      samples.push({
        id: Number(row.id),
        name: row.name,
        before: {
          region_name: row.region_name,
          region_city_name: row.region_city_name,
          region_district_name: row.region_district_name,
          region_town_name: row.region_town_name,
        },
        after: next,
        address: row.road_address || row.address,
      });
    }
  }

  const duplicateRegionNames = [...regionCounts.entries()]
    .filter(([, count]) => count > 1)
    .sort((left, right) => right[1] - left[1])
    .slice(0, 50);

  fs.mkdirSync(path.dirname(outputSqlPath), { recursive: true });
  fs.writeFileSync(outputSqlPath, ["BEGIN;", ...updates, "COMMIT;", ""].join("\n"), "utf8");
  fs.writeFileSync(
    outputReportPath,
    `${JSON.stringify(
      {
        restaurants: rows.length,
        updates: updates.length,
        parseFailures,
        duplicateRegionNames,
        samples,
      },
      null,
      2
    )}\n`,
    "utf8"
  );
  console.log(JSON.stringify({ restaurants: rows.length, updates: updates.length, parseFailures, sampleCount: samples.length }, null, 2));
}

main();
