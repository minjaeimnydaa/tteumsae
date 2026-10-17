import { readdirSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const testsDirectory = fileURLToPath(new URL("../tests/", import.meta.url));
const testFiles = readdirSync(testsDirectory)
  .filter((fileName) => fileName.endsWith(".test.js"))
  .sort()
  .map((fileName) => fileURLToPath(new URL(`../tests/${fileName}`, import.meta.url)));

if (testFiles.length === 0) {
  console.error("No backend test files found.");
  process.exit(1);
}

const result = spawnSync(process.execPath, ["--test", ...testFiles], {
  stdio: "inherit",
});

if (result.error) {
  throw result.error;
}

process.exit(result.status ?? 1);
