import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { parseArgs } from "node:util";
import {
  parseCurationCsv,
  validateCurationRows
} from "../lib/curation-csv.js";
import {
  listGangneungCurationCandidates,
  upsertPlaceCurations
} from "../lib/database.js";

const defaultInput = fileURLToPath(
  new URL("../data/gangneung-core-place-curations.csv", import.meta.url)
);

async function main() {
  const { values } = parseArgs({
    options: { input: { type: "string", default: defaultInput } }
  });
  const input = resolve(values.input);
  const rows = validateCurationRows(
    parseCurationCsv(await readFile(input, "utf8")),
    { expectedCount: 100 }
  );
  const activeIds = new Set(
    (await listGangneungCurationCandidates()).map((place) => String(place.content_id))
  );
  const missing = rows
    .map((row) => row.content_id)
    .filter((contentId) => !activeIds.has(contentId));
  if (missing.length > 0) {
    throw new Error(`활성 강릉 장소가 아닌 content_id: ${missing.join(", ")}`);
  }
  await upsertPlaceCurations(rows);
  console.log(`Imported ${rows.length} reviewed Gangneung curations`);
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exitCode = 1;
});
