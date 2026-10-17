import assert from "node:assert/strict";
import { readFile, readdir } from "node:fs/promises";
import { extname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const root = new URL("../", import.meta.url);
const rootPath = fileURLToPath(root);
const requiredFiles = [
  "api/health.js",
  "api/ops/status.js",
  "api/account.js",
  "api/places/index.js",
  "api/places/[id].js",
  "api/recommendations.js",
  "api/route.js",
  "api/cron/tour-sync.js",
  "api/cron/tour-catalog-sync.js",
  "api/cron/tour-intro-sync.js",
  "migrations/001_initial.sql",
  "migrations/002_detail_sync_state.sql",
  "migrations/003_user_accounts.sql",
  "migrations/004_tour_enrichment.sql",
  "migrations/005_sync_runtime_safety.sql",
  "migrations/006_gate_1b_data_trust.sql",
  "migrations/007_tour_detail_info.sql",
  "migrations/008_tour_congestion_forecasts.sql",
  "migrations/009_weather_forecast_cache.sql",
  "migrations/010_tour_accessibility.sql",
  "migrations/011_public_parking_lots.sql",
  "migrations/012_provider_usage_sources.sql",
  "scripts/verify-user-rls.js",
  "scripts/export-place-curations.mjs",
  "scripts/apply-tour-curation-research.mjs",
  "scripts/validate-place-curations.mjs",
  "scripts/import-place-curations.mjs",
  "lib/supabase-auth.js",
  "lib/curation-csv.js",
  "lib/curation-source.js",
  "lib/curation-tour-research.js",
  "lib/fetch-policy.js",
  "lib/sync-lease.js",
  "lib/tour-sync.js",
  "lib/tour-accessibility.js",
  "lib/tour-accessibility-sync.js",
  "lib/public-parking.js",
  "privacy.html",
  "account-deletion.html",
  ".env.example",
  "vercel.json"
];

for (const file of requiredFiles) {
  await readFile(new URL(file, root), "utf8");
}

const envExample = await readFile(new URL(".env.example", root), "utf8");
assert.match(envExample, /^TOUR_PRESENTATION_SYNC_BATCH_SIZE=\d+$/m);
assert.match(envExample, /^TOUR_ACCESSIBILITY_SYNC_BATCH_SIZE=\d+$/m);
assert.match(envExample, /^PUBLIC_PARKING_API_SERVICE_KEY=$/m);
const vercelConfig = JSON.parse(await readFile(new URL("vercel.json", root), "utf8"));
assert.ok(
  vercelConfig.crons?.some(
    (cron) => cron.path === "/api/cron/tour-intro-sync?stage=presentation"
  ),
  "TourAPI presentation Cron 설정이 필요합니다."
);

async function collectFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    if (["node_modules", ".git", ".vercel"].includes(entry.name)) continue;
    const path = join(directory, entry.name);
    if (entry.isDirectory()) files.push(...(await collectFiles(path)));
    else files.push(path);
  }
  return files;
}

const files = await collectFiles(rootPath);
for (const file of files) {
  if ([".apk", ".sha256"].includes(extname(file))) continue;
  const text = await readFile(file, "utf8");
  assert.equal(
    /(?<![a-f0-9])[a-f0-9]{64}(?![a-f0-9])/i.test(text),
    false,
    `64자리 인증키로 보이는 값이 포함됨: ${relative(rootPath, file)}`
  );
}

console.log(`Project check passed: ${files.length} files`);
