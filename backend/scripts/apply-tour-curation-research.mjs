import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { parseArgs } from "node:util";

import { parseCurationCsv, serializeCurationCsv } from "../lib/curation-csv.js";
import { applyTourResearch } from "../lib/curation-tour-research.js";

async function main() {
  const { values } = parseArgs({
    options: {
      input: { type: "string" },
      research: { type: "string" },
      output: { type: "string" },
      "checked-at": { type: "string" }
    }
  });
  if (!values.input || !values.research || !values.output || !values["checked-at"]) {
    throw new Error("input, research, output, checked-at 옵션이 모두 필요합니다.");
  }
  const rows = parseCurationCsv(await readFile(resolve(values.input), "utf8"));
  const researchItems = JSON.parse(await readFile(resolve(values.research), "utf8"));
  const reviewed = applyTourResearch(rows, researchItems, values["checked-at"]);
  await writeFile(resolve(values.output), serializeCurationCsv(reviewed), "utf8");
  console.log(`Applied official TourAPI research to ${reviewed.length} curation rows.`);
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exitCode = 1;
});
