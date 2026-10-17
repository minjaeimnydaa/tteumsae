import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { parseArgs } from "node:util";
import {
  parseCurationCsv,
  validateCurationRows
} from "../lib/curation-csv.js";

const defaultInput = fileURLToPath(
  new URL("../data/gangneung-core-place-curations.csv", import.meta.url)
);

async function main() {
  const { values } = parseArgs({
    options: {
      input: { type: "string", default: defaultInput },
      "allow-partial": { type: "boolean", default: false }
    }
  });
  const input = resolve(values.input);
  const rows = parseCurationCsv(await readFile(input, "utf8"));
  const reviewed = validateCurationRows(rows, {
    expectedCount: 100,
    allowPartial: values["allow-partial"]
  });
  const remaining = rows.length - reviewed.length;
  console.log(`Curation validation passed: reviewed=${reviewed.length}, remaining=${remaining}`);
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exitCode = 1;
});
