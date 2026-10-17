import { mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { parseArgs } from "node:util";
import {
  selectCurationCandidates,
  serializeCurationCsv
} from "../lib/curation-csv.js";
import { listCurationCandidatesFromApi } from "../lib/curation-source.js";
import { listGangneungCurationCandidates } from "../lib/database.js";

const defaultOutput = fileURLToPath(
  new URL("../data/gangneung-core-place-curations.csv", import.meta.url)
);

async function main() {
  const { values } = parseArgs({
    options: {
      output: { type: "string", default: defaultOutput },
      "api-base": { type: "string" }
    }
  });
  const output = resolve(values.output);
  const sourceCandidates = values["api-base"]
    ? await listCurationCandidatesFromApi(values["api-base"])
    : await listGangneungCurationCandidates();
  const candidates = selectCurationCandidates(
    sourceCandidates,
    100
  );
  if (candidates.length !== 100) {
    throw new Error(`활성 강릉 검수 후보가 100개 필요하지만 ${candidates.length}개입니다.`);
  }
  const rows = candidates.map((place) => ({
    content_id: place.content_id,
    name: place.name,
    category: place.category,
    operating_info_status: "UNKNOWN",
    opening_hours: "",
    closed_days: "",
    last_admission: "",
    admission_info_status: "UNKNOWN",
    parking_info: "",
    parking_info_status: "UNKNOWN",
    source_urls: "[]",
    source_checked_at: "",
    reviewed_at: "",
    review_note: ""
  }));
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, serializeCurationCsv(rows), "utf8");
  console.log(`Exported ${rows.length} Gangneung candidates to ${output}`);
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exitCode = 1;
});
