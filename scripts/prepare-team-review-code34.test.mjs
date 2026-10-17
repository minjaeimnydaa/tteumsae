import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { deflateRawSync } from 'node:zlib';
import { validateConfig, safeRelative, rewriteMarkdown, checkLinks, scanText, scanPptx } from './prepare-team-review-code34.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const base = JSON.parse(await fs.readFile(path.join(root, 'scripts/team-review-code34.config.json'), 'utf8'));
const ready = () => {
  const config = structuredClone(base);
  config.readyToBuild = true; config.allMarkdownAndPolicySourcesReviewed = true;
  config.optionalSummary = null; config.evidence = [];
  for (const item of config.artifacts) {
    item.reviewed = true; item.sha256 ??= 'A'.repeat(64);
    if (item.id.startsWith('screen')) item.captureVersion = 'code34 test fixture';
  }
  return config;
};

test('preparation config blocks generation before review approval', () => {
  const config = structuredClone(base); config.readyToBuild = false;
  assert.throws(() => validateConfig(config), /Not ready/);
});
test('ready explicit code34 allowlist is accepted', () => {
  assert.equal(validateConfig(ready()).length, 13);
});
test('wrong APK, source substitution, missing hash, and duplicate ID are rejected', () => {
  for (const change of [
    cfg => { cfg.artifacts[0].sha256 = 'B'.repeat(64); },
    cfg => { cfg.artifacts[1].source = 'output/presentations/old.pdf'; },
    cfg => { cfg.artifacts[1].sha256 = null; },
    cfg => { cfg.artifacts[1] = structuredClone(cfg.artifacts[0]); },
  ]) { const cfg = ready(); change(cfg); assert.throws(() => validateConfig(cfg)); }
});
test('screenshots require manual review and an actual capture version', () => {
  for (const change of [item => { item.reviewed = false; }, item => { item.captureVersion = null; }]) {
    const cfg = ready(); change(cfg.artifacts.find(item => item.id === 'screen1'));
    assert.throws(() => validateConfig(cfg));
  }
});
test('approved-store claim cannot be introduced in preparation packet', () => {
  const cfg = ready(); cfg.storePublicUrl = 'https://example.invalid/app';
  assert.throws(() => validateConfig(cfg), /approved store link/);
});
test('relative path guard rejects traversal, absolute paths, ADS and backslashes', () => {
  for (const value of ['../secret', '/tmp/private', 'C:/keys', 'docs/a:token.md', 'docs\\key.md', 'docs/./x.md', 'docs//x.md']) {
    assert.throws(() => safeRelative(value));
  }
  assert.doesNotThrow(() => safeRelative('04-review/29_SUBMISSION_FILE_MAP.md'));
});
test('unreviewed or unexpected optional summary/evidence is rejected', () => {
  const cfg = ready();
  cfg.evidence = [{id:'evidence1',source:'../private.png',destination:'06-evidence/photo.png',label:'photo',use:'internal',reviewed:true,sha256:'A'.repeat(64),captureVersion:'34'}];
  assert.throws(() => validateConfig(cfg));
  cfg.evidence = []; cfg.optionalSummary = {id:'summary',source:'output/pdf/old.pdf',destination:'04-review/old.pdf'};
  assert.throws(() => validateConfig(cfg));
});
test('included Markdown is remapped and omitted repo document becomes non-clickable reference', () => {
  const map = new Map([[path.resolve(root,'docs/25_CONTEST_FINAL_HANDOFF.md').toLowerCase(),'04-review/25_CONTEST_FINAL_HANDOFF.md']]);
  const result = rewriteMarkdown('[공모전](25_CONTEST_FINAL_HANDOFF.md#section) [QA](08_QA_AND_KNOWN_ISSUES.md)',
    'docs/23_TEAM_REVIEW_2026-09-13.md','04-review/23_TEAM_REVIEW_2026-09-13.md',map);
  assert.match(result.text, /\[공모전\]\(25_CONTEST_FINAL_HANDOFF\.md#section\)/);
  assert.match(result.text, /QA \(저장소 참고: 08_QA_AND_KNOWN_ISSUES\.md\)/);
  assert.equal(result.changes.length,2);
});
test('unbundled embedded image cannot silently lose its evidence', () => {
  assert.throws(() => rewriteMarkdown('![evidence](missing.png)','docs/x.md','04-review/x.md',new Map()), /Unbundled embedded/);
});
test('relative link validator accepts included targets and rejects missing or unsafe targets', () => {
  const all = new Set(['readme.md','04-review/x.md']);
  assert.doesNotThrow(() => checkLinks('[read](../README.md)','04-review/x.md',all));
  assert.doesNotThrow(() => checkLinks('<a href="https://example.invalid/">link</a>','00-START-HERE.html',all));
  assert.throws(() => checkLinks('[missing](missing.md)','04-review/x.md',all), /Missing local link/);
  assert.throws(() => checkLinks('<a href="javascript:alert(1)">x</a>','00-START-HERE.html',all), /Unsafe link/);
  assert.throws(() => checkLinks('[outside](../../secret.md)','04-review/x.md',all), /Unsafe package path/);
});
test('private patterns fail without echoing the matched value', () => {
  const fixture = 'gh' + 'p_' + 'x'.repeat(30);
  assert.throws(() => scanText(fixture,'fixture'), error => /Private-value/.test(error.message) && !error.message.includes(fixture));
  assert.doesNotThrow(() => scanText('신홍 / godburgundy@gmail.com / SHA256 ' + 'A'.repeat(64),'approved-public'));
});
test('PPTX XML scanner reads compressed notes without extracting files', () => {
  const name = Buffer.from('ppt/notesSlides/notesSlide1.xml');
  const text = Buffer.from('<a:t>public release notes</a:t>');
  const packed = deflateRawSync(text);
  const local = Buffer.alloc(30); local.writeUInt32LE(0x04034b50); local.writeUInt16LE(8,8); local.writeUInt16LE(name.length,26);
  const central = Buffer.alloc(46); central.writeUInt32LE(0x02014b50); central.writeUInt16LE(8,10);
  central.writeUInt32LE(packed.length,20); central.writeUInt32LE(text.length,24); central.writeUInt16LE(name.length,28);
  const body = Buffer.concat([local,name,packed]);
  const directory = Buffer.concat([central,name]);
  const end = Buffer.alloc(22); end.writeUInt32LE(0x06054b50); end.writeUInt16LE(1,10); end.writeUInt32LE(body.length,16);
  assert.equal(scanPptx(Buffer.concat([body,directory,end]),'fixture').textFiles,1);
});

// No test creates, copies, deletes, archives or uploads any artifact.
