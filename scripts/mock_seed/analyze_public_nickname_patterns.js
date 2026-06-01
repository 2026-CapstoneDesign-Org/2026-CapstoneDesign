const fs = require("fs");
const https = require("https");
const path = require("path");

const sourceCsv = path.resolve(
  process.env.DININGCODE_MENTIONS_CSV ||
    "../community-restaurant-name-collector/input/diningcode/diningcode_mentions.csv"
);
const outputPath = path.resolve(
  process.env.NICKNAME_PATTERN_OUTPUT ||
    "tmp/mock_seed_import/nickname_pattern_stats.json"
);
const sampleLimit = Number(process.env.NICKNAME_PATTERN_SAMPLE_LIMIT || 60);
const delayMs = Number(process.env.NICKNAME_PATTERN_DELAY_MS || 350);

function parseCsvLine(line) {
  const result = [];
  let value = "";
  let quoted = false;
  for (let i = 0; i < line.length; i += 1) {
    const ch = line[i];
    if (quoted) {
      if (ch === '"' && line[i + 1] === '"') {
        value += '"';
        i += 1;
      } else if (ch === '"') {
        quoted = false;
      } else {
        value += ch;
      }
    } else if (ch === '"') {
      quoted = true;
    } else if (ch === ",") {
      result.push(value);
      value = "";
    } else {
      value += ch;
    }
  }
  result.push(value);
  return result;
}

function readDiningcodeUrls(filePath) {
  const text = fs.readFileSync(filePath, "utf8");
  const lines = text.split(/\r?\n/).filter(Boolean);
  const header = parseCsvLine(lines[0]);
  const urlIndex = header.includes("url") ? header.indexOf("url") : header.indexOf("source_url");
  if (urlIndex < 0) {
    throw new Error(`url column not found in ${filePath}`);
  }
  return lines
    .slice(1)
    .map((line) => parseCsvLine(line)[urlIndex])
    .filter((url) => /^https:\/\/www\.diningcode\.com\/profile\.php\?rid=/.test(url));
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function fetchText(url) {
  return new Promise((resolve, reject) => {
    const req = https.get(
      url,
      {
        headers: {
          "User-Agent":
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126 Safari/537.36",
          Accept: "text/html,application/xhtml+xml",
        },
        timeout: 20000,
      },
      (res) => {
        let body = "";
        res.setEncoding("utf8");
        res.on("data", (chunk) => {
          body += chunk;
        });
        res.on("end", () => {
          if (res.statusCode >= 200 && res.statusCode < 300) {
            resolve(body);
          } else {
            reject(new Error(`HTTP ${res.statusCode}`));
          }
        });
      }
    );
    req.on("timeout", () => {
      req.destroy(new Error("timeout"));
    });
    req.on("error", reject);
  });
}

function decodeHtml(value) {
  return value
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&nbsp;/g, " ")
    .trim();
}

function extractNicknames(html) {
  const names = new Set();
  const personGradePattern = /<p class="person-grade">([\s\S]*?)<\/p>/g;
  let match;
  while ((match = personGradePattern.exec(html))) {
    const strong = match[1].match(/<strong>([\s\S]*?)<\/strong>/);
    if (!strong) continue;
    const nickname = decodeHtml(strong[1].replace(/<[^>]+>/g, ""));
    if (nickname && nickname.length <= 30) {
      names.add(nickname);
    }
  }
  return [...names];
}

function increment(object, key) {
  object[key] = (object[key] || 0) + 1;
}

function classifyNickname(value) {
  const nickname = value.trim();
  const hasHangul = /[가-힣]/.test(nickname);
  const hasEnglish = /[A-Za-z]/.test(nickname);
  const hasDigit = /\d/.test(nickname);
  const hasSeparator = /[_\-.]/.test(nickname);
  const hangulOnly = /^[가-힣]+$/.test(nickname);
  const englishOnly = /^[A-Za-z]+$/.test(nickname);
  const englishDigit = /^[A-Za-z]+[0-9]*$/.test(nickname);
  const foodWord = /(밥|한끼|맛|먹|냠|커피|카페|디저트|국밥|면|빵|라떼|식사|점심|저녁)/.test(nickname);
  const familyPetWord = /(엄마|아빠|언니|누나|형|오빠|집사|콩|두부|초코|모카|토리|나나|보리)/.test(nickname);
  const localWord = /(서울|용인|역북|명지|처인|수지|동네|로컬|시장|골목|근처)/.test(nickname);
  const personLike = hangulOnly && nickname.length >= 2 && nickname.length <= 4 && !foodWord && !familyPetWord && !localWord;

  if (personLike) return "short_hangul_person_or_alias";
  if (foodWord && localWord) return "local_food_phrase";
  if (foodWord) return "food_or_daily_phrase";
  if (familyPetWord) return "family_or_pet_handle";
  if (localWord) return "local_handle";
  if (englishOnly || englishDigit) return hasDigit ? "english_with_digits" : "english";
  if (hasEnglish && hasSeparator) return "english_separator";
  if (hasHangul && hasDigit) return "hangul_with_digits";
  if (hasHangul && hasEnglish) return "mixed_script";
  return "other";
}

async function main() {
  const urls = readDiningcodeUrls(sourceCsv);
  const sampled = urls.filter((_, index) => index % 3 === 0).slice(0, sampleLimit);
  const stats = {
    source: "diningcode_public_profile_pages",
    sourceCsv,
    sampledPages: 0,
    failedPages: 0,
    extractedNicknameCount: 0,
    rawNicknamesStored: false,
    crawledAt: new Date().toISOString(),
    patternCounts: {},
    lengthCounts: {},
    scriptCounts: {},
    digitCount: 0,
  };

  for (const url of sampled) {
    try {
      const html = await fetchText(url);
      stats.sampledPages += 1;
      for (const nickname of extractNicknames(html)) {
        stats.extractedNicknameCount += 1;
        increment(stats.patternCounts, classifyNickname(nickname));
        increment(stats.lengthCounts, String(Array.from(nickname).length));
        const script = /^[가-힣]+$/.test(nickname)
          ? "hangul_only"
          : /^[A-Za-z0-9_.-]+$/.test(nickname)
          ? "latin_digit_symbol"
          : /[가-힣]/.test(nickname) && /[A-Za-z]/.test(nickname)
          ? "mixed_hangul_latin"
          : "mixed_or_symbol";
        increment(stats.scriptCounts, script);
        if (/\d/.test(nickname)) stats.digitCount += 1;
      }
    } catch (error) {
      stats.failedPages += 1;
    }
    await sleep(delayMs);
  }

  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, `${JSON.stringify(stats, null, 2)}\n`, "utf8");
  console.log(JSON.stringify(stats, null, 2));
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
