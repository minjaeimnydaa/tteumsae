import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../', import.meta.url));
const assetRoot = root + 'android/app/src/main/assets/licenses/';
test('offline notices include the font and every resolved runtime artifact', () => {
  const catalog = JSON.parse(readFileSync(assetRoot + 'catalog.json', 'utf8'));
  const model = readFileSync(root + 'android/app/build/intermediates/lint_vital_report_lint_model/release/generateReleaseLintVitalReportModel/release-artifact-dependencies.xml', 'utf8');
  const runtime = [...new Set(model.match(/<package\s+roots="([^"]+)"/s)[1].split(','))];
  assert.deepEqual(catalog.modules.map(m => m.coordinate).sort(), runtime.sort());
  assert.ok(catalog.documents.some(d => d.title.includes('Pretendard')));
  for (const doc of catalog.documents) {
    assert.match(doc.file, /^[a-zA-Z0-9_.-]+\.txt$/);
    assert.ok(!doc.file.includes('..'));
    assert.ok(existsSync(assetRoot + doc.file), doc.file);
    assert.ok(readFileSync(assetRoot + doc.file, 'utf8').length > 100);
  }
  for (const module of catalog.modules) {
    assert.ok(module.licenseNames.length > 0, module.coordinate);
    assert.ok(module.documents.length > 0, module.coordinate);
    for (const file of module.documents) assert.ok(catalog.documents.some(doc => doc.file === file), file);
  }
  assert.equal(readFileSync(assetRoot + 'pretendard-ofl.txt', 'utf8').replace(/\r\n/g, '\n').trim(), readFileSync(root + 'android/third_party_licenses/Pretendard-OFL.txt', 'utf8').replace(/\r\n/g, '\n').trim());
});
test('settings opens offline notices, without remote executable content', () => {
  const settings = readFileSync(root + 'android/app/src/main/java/com/tteumsae/app/ui/settings/SettingsScreen.kt', 'utf8');
  const dialog = readFileSync(root + 'android/app/src/main/java/com/tteumsae/app/ui/settings/LicenseNoticesDialog.kt', 'utf8');
  assert.match(settings, /오픈소스 라이선스/);
  assert.match(settings, /LicenseNoticesDialog/);
  assert.match(dialog, /assets\.open/);
  assert.doesNotMatch(dialog, /WebView|HttpURLConnection|javaScriptEnabled/);
});
