import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { inflateRawSync, inflateSync } from 'node:zlib';

// Deliberately separate from code33. No workspace archive, upload, or automatic ZIP.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const output = path.join(root, 'output', 'team-review-20260913-1100-code34');
const configPath = path.join(root, 'scripts', 'team-review-code34.config.json');
const APK_SHA = '30E05594A326CC79386EBE838FBB0F15283C7ABFF1C76E7EB60A320D06A88F96';
const APK_NAME = 'tteumsae-v0.13.1-34-manual-release.apk';
const DECK = 'tteumsae-contest-functions-0.13.1-code34-review-v4';
const RIGHTS = 'tteumsae-third-party-rights-0.13.1-code34.pdf';
const DOCS = ['23_TEAM_REVIEW_2026-09-13.md', '24_ONESTORE_FINAL_HANDOFF.md', '25_CONTEST_FINAL_HANDOFF.md',
  '26_RELEASE_REVIEW_RISKS.md', '27_RELEASE_UX_REFINEMENT.md', '28_CODE34_REVIEW_ADDENDUM.md', '29_SUBMISSION_FILE_MAP.md'];
const SCREENS = ['screen-01-home.png', 'screen-02-route-input.png', 'screen-03-tourist-results.png',
  'screen-04-tourist-map.png', 'screen-05-tourist-detail.png', 'screen-06-tourist-explore.png'];
const FIXED = new Map([
  ['apk', [`output/release-0.13.1/${APK_NAME}`, `01-app/${APK_NAME}`]],
  ['contestPdf', [`output/presentations/${DECK}.pdf`, `02-contest/${DECK}.pdf`]],
  ['contestPptx', [`output/presentations/${DECK}.pptx`, `02-contest/${DECK}.pptx`]],
  ['contestReadme', ['output/presentations/README-code34.md', '02-contest/README-code34.md']],
  ['rightsPdf', [`output/pdf/${RIGHTS}`, `03-onestore/${RIGHTS}`]],
  ...['icon-512.png', 'banner-1024x578.png', ...SCREENS].map((name, i) => [i === 0 ? 'icon' : i === 1 ? 'banner' : `screen${i - 1}`,
    [`output/release-0.13.1/store/${name}`, `03-onestore/images/${name}`]]),
]);
const HASH = /^[0-9A-F]{64}$/;
const SECRET = /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|sb_secret_[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|sk_live_[A-Za-z0-9]{12,}|postgres(?:ql)?:\/\/[^\s]+:[^\s]+@|\beyJ[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}\b|\b01[016789][ -]?\d{3,4}[ -]?\d{4}\b/i;
const SECRET_ASSIGNMENT = /(?:service[_-]?role[_-]?key|tourapi[_-]?service[_-]?key|(?:access|refresh)[_-]?token|signing[_-]?password)\s*[:=]\s*["']?[A-Za-z0-9_+/%=-]{16,}/i;
const sha256 = bytes => crypto.createHash('sha256').update(bytes).digest('hex').toUpperCase();
const slash = value => value.split(path.sep).join('/');
const escapeHtml = value => String(value).replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
const href = value => value.split('/').map(encodeURIComponent).join('/');
const relative = (from, to) => slash(path.relative(from, to));
const fail = message => { throw new Error(message); };

function inside(parent, target) {
  const rel = path.relative(parent, target);
  if (!rel || rel === '..' || rel.startsWith(`..${path.sep}`) || path.isAbsolute(rel)) fail('Unsafe package path');
}
function safeRelative(value) {
  if (typeof value !== 'string' || !value || value.includes('\\') || value.includes(':') || value.startsWith('/') ||
      value.split('/').some(part => !part || part === '..' || part === '.') || /[\x00-\x1f]/.test(value)) fail('Invalid relative path');
}
function scanText(text, label) {
  if (SECRET.test(text) || SECRET_ASSIGNMENT.test(text)) fail(`Private-value pattern detected in ${label}; values not printed`);
}
async function noSymlinks(target, allowMissing = false) {
  inside(root, target);
  const components = path.relative(root, target).split(path.sep);
  let cursor = root;
  for (const component of components) {
    cursor = path.join(cursor, component);
    let stat;
    try { stat = await fs.lstat(cursor); } catch (error) {
      if (allowMissing && error.code === 'ENOENT') return;
      throw error;
    }
    if (stat.isSymbolicLink()) fail('Symlink/reparse path rejected');
    if (cursor !== target && !stat.isDirectory()) fail('Non-directory path ancestor');
  }
}

function validateConfig(config) {
  if (config.schemaVersion !== 1 || config.readyToBuild !== true || config.allMarkdownAndPolicySourcesReviewed !== true) {
    fail('Not ready: review the config and source artifacts first. No output written');
  }
  if (!/^[a-f0-9]{7,40}$/i.test(config.appSourceCommit ?? '')) fail('Missing app source commit');
  if (!Number.isInteger(config.storeLastVerifiedApkCode) || ![32, 33, 34].includes(config.storeLastVerifiedApkCode)) fail('Invalid store version');
  if (config.storePublicUrl !== null) fail('This preparation packet must not claim an approved store link');
  if (!Array.isArray(config.remaining) || !config.remaining.length || config.remaining.some(item => typeof item !== 'string')) fail('Missing remaining tasks');
  if (!Array.isArray(config.artifacts) || config.artifacts.length !== FIXED.size) fail('Artifact allowlist mismatch');
  const ids = new Set();
  for (const item of config.artifacts) {
    const fixed = FIXED.get(item.id);
    if (!fixed || ids.has(item.id) || item.source !== fixed[0] || item.destination !== fixed[1]) fail('Artifact path/ID differs from code34 allowlist');
    ids.add(item.id);
    if (item.reviewed !== true || !HASH.test(item.sha256 ?? '')) fail(`Unreviewed or unhashed artifact: ${item.id}`);
    if (!item.label || !item.use) fail('Artifact needs a label and use');
    if (item.id.startsWith('screen') && (typeof item.captureVersion !== 'string' || !item.captureVersion.trim())) fail('Every screenshot needs its actual capture version');
  }
  if (config.artifacts.find(item => item.id === 'apk').sha256 !== APK_SHA) fail('APK must be the verified 30E0 release');
  const extras = [];
  if (config.optionalSummary !== null) {
    const item = config.optionalSummary;
    if (!/^output\/pdf\/tteumsae-team-review-[a-z0-9.-]*code34[a-z0-9.-]*\.pdf$/.test(item.source ?? '') ||
        item.destination !== `04-review/${path.posix.basename(item.source)}`) fail('Unexpected optional summary path');
    extras.push(item);
  }
  if (!Array.isArray(config.evidence) || config.evidence.length > 8) fail('At most 8 explicit evidence images');
  for (const item of config.evidence) {
    safeRelative(item.source); safeRelative(item.destination);
    if (!/^tmp\/(?:release-qa-final-20260913|ux-v34-20260913)\/.+\.png$/.test(item.source) ||
        !/^06-evidence\/[a-z0-9.-]+\.png$/.test(item.destination)) fail('Unexpected evidence image path');
    if (!item.captureVersion) fail('Evidence needs its capture version');
    extras.push(item);
  }
  for (const item of extras) {
    if (item.reviewed !== true || !HASH.test(item.sha256 ?? '') || !item.label || !item.use) fail('Unreviewed optional artifact');
    if (!/^(?:summary|evidence[a-zA-Z0-9_-]+)$/.test(item.id ?? '') || ids.has(item.id)) fail('Invalid optional artifact ID');
    ids.add(item.id);
  }
  scanText(JSON.stringify(config), 'config');
  return [...config.artifacts, ...extras];
}

// Read XML metadata/notes from the already reviewed PPTX without editing or extracting files.
function scanPptx(bytes, label) {
  const end = bytes.lastIndexOf(Buffer.from([0x50, 0x4b, 0x05, 0x06]));
  if (end < 0 || end + 22 > bytes.length) fail('Invalid PPTX ZIP');
  const count = bytes.readUInt16LE(end + 10);
  let cursor = bytes.readUInt32LE(end + 16), textFiles = 0;
  for (let i = 0; i < count; i++) {
    if (cursor + 46 > bytes.length || bytes.readUInt32LE(cursor) !== 0x02014b50) fail('Invalid PPTX directory');
    const flags = bytes.readUInt16LE(cursor + 8), method = bytes.readUInt16LE(cursor + 10);
    const size = bytes.readUInt32LE(cursor + 20), expanded = bytes.readUInt32LE(cursor + 24);
    const nameSize = bytes.readUInt16LE(cursor + 28), extraSize = bytes.readUInt16LE(cursor + 30), commentSize = bytes.readUInt16LE(cursor + 32);
    const local = bytes.readUInt32LE(cursor + 42);
    const name = bytes.subarray(cursor + 46, cursor + 46 + nameSize).toString('utf8');
    cursor += 46 + nameSize + extraSize + commentSize;
    if (!/\.(?:xml|rels)$/i.test(name)) continue;
    if (flags & 1 || expanded > 16 * 1024 * 1024 || local + 30 > bytes.length || bytes.readUInt32LE(local) !== 0x04034b50) fail('Unsupported PPTX entry');
    const dataStart = local + 30 + bytes.readUInt16LE(local + 26) + bytes.readUInt16LE(local + 28);
    if (dataStart + size > bytes.length) fail('Truncated PPTX entry');
    const packed = bytes.subarray(dataStart, dataStart + size);
    const text = method === 0 ? packed : method === 8 ? inflateRawSync(packed, { maxOutputLength: 16 * 1024 * 1024 }) : fail('Unsupported PPTX compression');
    scanText(text.toString('utf8'), `${label} XML`); textFiles++;
  }
  return { type: 'pptx-xml-and-notes', textFiles };
}
function scanArtifact(bytes, destination) {
  const ext = path.extname(destination);
  if (ext === '.apk') return { type: 'exact-reviewed-sha256', contentScan: 'opaque-apk-not-scanned' };
  if (ext === '.pptx') return scanPptx(bytes, destination);
  scanText(bytes.toString('utf8'), destination);
  if (ext === '.pdf') {
    const latin = bytes.toString('latin1'); let streams = 0, unscannedStreams = 0;
    for (const match of latin.matchAll(/<<[\s\S]{0,4096}?\/FlateDecode[\s\S]{0,1024}?>>\s*stream\r?\n/g)) {
      const start = match.index + match[0].length, end = latin.indexOf('endstream', start);
      if (end < start || end - start > 20 * 1024 * 1024) { unscannedStreams++; continue; }
      let unpacked;
      try { unpacked = inflateSync(bytes.subarray(start, end), { maxOutputLength: 32 * 1024 * 1024 }); }
      catch { unscannedStreams++; continue; }
      scanText(unpacked.toString('utf8'), `${destination} decoded stream`); streams++;
    }
    return { type: 'pdf-raw-and-readable-flate-streams', streams, unscannedStreams, visualReviewRequired: true };
  }
  return { type: ext === '.png' ? 'png-raw-text-patterns-no-ocr' : 'full-text-patterns', visualReviewRequired: ext === '.png' };
}

function rewriteMarkdown(text, source, destination, sourceMap) {
  const changes = [];
  const rewritten = text.replace(/(!?)\[([^\]]*)\]\(([^)\n]+)\)/g, (whole, image, label, rawTarget) => {
    const target = rawTarget.replace(/^<|>$/g, '');
    if (/^(?:https?:|mailto:|#)/i.test(target)) return whole;
    const anchorIndex = target.indexOf('#'), fragment = anchorIndex < 0 ? '' : target.slice(anchorIndex);
    const name = anchorIndex < 0 ? target : target.slice(0, anchorIndex);
    let decoded;
    try { decoded = decodeURIComponent(name); } catch { fail('Invalid Markdown link encoding'); }
    const fromSource = path.resolve(root, path.dirname(source), decoded);
    const mapped = sourceMap.get(fromSource.toLowerCase());
    if (mapped) {
      const next = relative(path.dirname(path.join(output, destination)), path.join(output, mapped));
      changes.push({ type: 'relative-link-remap', target: mapped });
      return `${image}[${label}](${href(next)}${fragment})`;
    }
    if (/\.(?:md|pdf|pptx|png|html|apk)$/i.test(decoded)) {
      if (image) fail(`Unbundled embedded image in ${destination}`);
      changes.push({ type: 'unbundled-local-link-to-reference', target: path.basename(decoded) });
      return `${label} (저장소 참고: ${path.basename(decoded)})`;
    }
    fail(`Unresolved local Markdown link in ${destination}`);
  });
  return { text: rewritten, changes };
}
function checkLinks(text, current, all) {
  const targets = current.endsWith('.md')
    ? [...text.matchAll(/\]\(([^)\n]+)\)/g)].map(match => match[1])
    : [...text.matchAll(/(?:href|src)\s*=\s*["']([^"']+)["']/gi)].map(match => match[1]);
  for (const target of targets) {
    if (/^(?:https?:|mailto:|#)/i.test(target)) continue;
    if (/^(?:data:|javascript:|file:|\/\/)/i.test(target)) fail(`Unsafe link scheme in ${current}`);
    let decoded;
    try { decoded = decodeURIComponent(target.split(/[?#]/)[0]); } catch { fail('Bad link encoding'); }
    const resolved = path.resolve(output, path.dirname(current), decoded);
    inside(output, resolved);
    if (!all.has(relative(output, resolved).toLowerCase())) fail(`Missing local link from ${current}: ${decoded}`);
  }
}
async function listFiles(directory) {
  const result = [];
  for (const entry of await fs.readdir(directory, { withFileTypes: true })) {
    const full = path.join(directory, entry.name);
    if (entry.isSymbolicLink()) fail('Output cannot contain links');
    if (entry.isDirectory()) result.push(...await listFiles(full));
    else if (entry.isFile()) result.push(relative(output, full));
    else fail('Unexpected filesystem entry');
  }
  return result;
}

function landing(config, files, preparedAt) {
  const link = (id, label) => { const item = files.find(file => file.id === id); return `<a href="${href(item.destination)}">${escapeHtml(label ?? item.label)}</a>`; };
  const rows = files.filter(file => !file.id.startsWith('screen')).map(item => `<tr><td><a href="${href(item.destination)}">${escapeHtml(item.label)}</a></td><td>${escapeHtml(item.use)}</td></tr>`).join('');
  const screens = files.filter(file => file.id.startsWith('screen')).map(item => `<figure><a href="${href(item.destination)}"><img src="${href(item.destination)}" alt="${escapeHtml(item.label)}" loading="lazy"></a><figcaption>${escapeHtml(item.label)}<small>${escapeHtml(item.captureVersion)}</small></figcaption></figure>`).join('');
  return `<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'"><title>틈새 code34 · 11시 팀 검토</title><style>
*{box-sizing:border-box}body{margin:0;background:#faf8f7;color:#272323;font:16px/1.7 system-ui,'Malgun Gothic',sans-serif}main{max-width:1100px;margin:auto;padding:36px 24px}header{border-top:6px solid #e80032;padding:22px 0}h1{font-size:32px;line-height:1.3;margin:8px 0}h2{font-size:23px;margin:36px 0 12px}p{margin:10px 0}a{color:#a90027;text-underline-offset:3px}a:focus-visible{outline:3px solid #e80032;outline-offset:3px}.notice{padding:16px 20px;border-left:4px solid #e80032;background:#fff0f2}.quick{display:flex;flex-wrap:wrap;gap:12px;margin:20px 0}.quick a{padding:10px 15px;background:white;border:1px solid #dbd4d4;border-radius:8px;text-decoration:none}table{border-collapse:collapse;width:100%;background:#fff}th,td{text-align:left;padding:12px 14px;border-bottom:1px solid #e8e2e2;vertical-align:top}th{background:#f0eaea}.table{overflow-x:auto}ul,ol{padding-left:24px}small{display:block;color:#696060;font-size:13px}.screens{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:14px}figure{margin:0}img{width:100%;height:320px;object-fit:contain;background:#fff;border:1px solid #e8e2e2}figcaption{font-size:14px;line-height:1.5;margin-top:8px}code{overflow-wrap:anywhere;font-size:13px}footer{border-top:1px solid #ddd;padding-top:18px;margin-top:36px;color:#696060;font-size:13px}@media(max-width:780px){.screens{grid-template-columns:repeat(3,minmax(0,1fr))}img{height:260px}h1{font-size:27px}}@media(max-width:450px){main{padding:20px 16px}.screens{grid-template-columns:repeat(2,minmax(0,1fr))}img{height:260px}}
</style></head><body><main><header><small>2026년 9월 13일 오전 11시 · 팀 검토 자료</small><h1>틈새 0.13.1 · code34</h1><p>먼저 앱과 기능설명서를 확인하고, 아래 표에서 파일별 제출처를 확인하세요.</p></header>
<div class="notice"><strong>최종 신청 전 자료입니다.</strong><br>11시 팀 검토 후 사용자의 새 명시 지시가 있어야 원스토어·공모전 최종 신청을 진행합니다. 이 폴더나 ZIP을 공모전에 통째로 첨부하지 마세요. 실제 스토어 공개 링크는 아직 없습니다.</div>
<nav class="quick" aria-label="주요 파일">${link('apk','앱 설치 APK')}${link('contestPdf','기능설명서 PDF')}${link('doc29','파일 사용처·팀 입력 항목')}${link('doc24','원스토어 실제 진행 상태')}</nav>
<h2>현재 상태</h2><p>수동 출발지 code34 APK는 공개 다운로드와 파일 해시 대조를 마쳤습니다. 수동·자동 보존 모드 각각227개 검사, 최종 주소·시트 흐름73초와 묶음 핀 가림 검사를 확인했습니다. 모든 화면·기기 검사를 뜻하지 않습니다.</p><p><strong>원스토어 마지막 확인: code${config.storeLastVerifiedApkCode}</strong> · ${escapeHtml(config.storeStatus)}<small>포털 확인 기준: ${escapeHtml(config.storeVerifiedAt)}. 현재 상태는 원스토어 인계 문서를 우선합니다.</small></p>
<h2>파일을 어디에 쓰나요?</h2><div class="table"><table><thead><tr><th scope="col">파일 열기</th><th scope="col">사용처</th></tr></thead><tbody>${rows}</tbody></table></div>
<h2>실제 앱 화면 6장</h2><p>원스토어에 저장한 최신 code34 실기기 화면입니다. 안목해변→경포대 관광 예시와 강릉 장소 탐색을 담았습니다. 각각 열어 전체 이미지를 확인할 수 있습니다.</p><div class="screens">${screens}</div>
<h2>팀이 마무리할 일</h2><ol>${config.remaining.map(item => `<li>${escapeHtml(item)}</li>`).join('')}</ol>
<h2>공유할 수 없는 것</h2><p>TourAPI·서버 비밀키, 서명키·백업, 비밀번호·인증 토큰, 개인 계정 화면·원본 로그는 넣지 않습니다. TourAPI 키는 소유자가 승인된 공식 입력란에서 직접 처리합니다. APK의 공개 클라이언트 설정과 서버 비밀키는 구분합니다.</p>
<h2>참고 링크</h2><p><a href="https://tteumsae-apk-six.vercel.app/">공개 APK 페이지</a> · <a href="https://tteumsae-backend-one.vercel.app/privacy">공개 개인정보처리방침</a> · <a href="README.md">텍스트 안내</a> · <a href="MANIFEST.json">파일 해시 목록</a></p>
<footer>기존 code33 폴더와 ZIP은 보존합니다. PDF/PPTX는 파일별 대상 버전을 확인하세요.<br>생성 시각(UTC): ${escapeHtml(preparedAt)}. 묶음 밖 문서 링크는 복사본에서 저장소 참고 문구로 변환했습니다. 정책 사본의 사이트 내부 링크는 운영 URL로 연결합니다. 원본 파일은 바꾸지 않았습니다.</footer></main></body></html>`;
}
function readme(config, files, git, preparedAt) {
  return `# 틈새 code34 · 오전 11시 팀 검토\n\n[먼저 열기: 파일 안내 화면](00-START-HERE.html)\n\n**최종 신청 전 자료입니다. 11시 팀 검토 후 사용자의 새 지시가 필요합니다.**\n이 ZIP 전체를 공모전·원스토어에 한 번에 첨부하지 않습니다. 실제 스토어 공개 링크는 아직 없습니다.\n\n## 파일과 사용처\n\n| 파일 | 사용처 |\n|---|---|\n${files.map(item => `| [${item.label.replaceAll('|', '\\|')}](${href(item.destination)}) | ${item.use.replaceAll('|', '\\|')} |`).join('\n')}\n\n## 버전·상태\n\n- 앱: 0.13.1 / code34 / 수동 출발지. 공개 파일 해시 확인: ${config.publicApkVerifiedAt}.\n- 원스토어 마지막 확인: code${config.storeLastVerifiedApkCode} / ${config.storeVerifiedAt}. ${config.storeStatus}\n- 최종 신청·공개는 팀 검토 후 새 지시까지 보류합니다. 이전 code33 묶음은 보존합니다.\n- APK SHA-256: ${APK_SHA}\n- 앱 소스: ${config.appSourceCommit}. 생성 시점 Git: ${git.commit}. 미커밋 변경: ${git.dirty ? '있음' : '없음'}.\n- 생성 시각(UTC): ${preparedAt}. 파일별 실제 내용은 MANIFEST 해시 기준입니다.\n\n## 남은 일\n\n${config.remaining.map(item => `- ${item}`).join('\n')}\n\n## 보안·변환 범위\n\n명시된 파일만 복사합니다. TourAPI·서버 비밀키·서명키·인증정보·개인 계정 로그는 공유하지 않습니다. APK의 공개 클라이언트 설정은 서버 비밀키와 구분합니다.\n텍스트·PPTX XML/노트·읽을 수 있는 PDF 스트림·PNG 텍스트의 민감값 패턴 검사는 전체 비밀값 탐지나 OCR을 보장하지 않습니다. 사진·화면·PDF는 별도 시각 검수가 필요하며 APK는 검증된 해시로 고정합니다.\n포함된 문서 링크는 상대경로로 재연결하고, 묶음에 없는 로컬 문서 링크는 저장소 참고 일반 텍스트로 바꿉니다. 정책 HTML 사본의 루트 상대 href는 공개 운영 URL로 바꿉니다. 원본 파일은 바꾸지 않습니다.\nMANIFEST는 자기 자신을 해시 목록에 넣지 않습니다. INCOMPLETE.txt가 남아 있으면 생성 실패이므로 공유·압축하지 마세요.\n`;
}

async function main() {
  const args = process.argv.slice(2);
  if (args.length > 1 || args.some(arg => !['--check', '--build'].includes(arg))) fail('Use --check (no writes) or --build');
  const build = args[0] === '--build';
  // Explicit opt-in is necessary even after config is ready; no argument is a read-only check.
  await noSymlinks(configPath);
  const configBytes = await fs.readFile(configPath); scanText(configBytes.toString('utf8'), 'config');
  const config = JSON.parse(configBytes);
  const artifacts = validateConfig(config);
  const records = [...artifacts,
    ...DOCS.map(name => ({ id: `doc${name.slice(0, 2)}`, source: `docs/${name}`, destination: `04-review/${name}`, label: ({23:'팀 검토 안내',24:'원스토어 인계',25:'공모전 인계',26:'위험·검증 범위',27:'code34 검증 기록',28:'기존 자료와 변경 구분',29:'제출 파일·직접 입력 항목'})[name.slice(0,2)], use: '팀 내부 검토 / 공식 필수 첨부 아님' })),
    ...['privacy.html','account-deletion.html'].map(name => ({ id: name === 'privacy.html' ? 'privacy' : 'deletion', source: `backend/${name}`, destination: `05-policies/${name}`, label: name === 'privacy.html' ? '개인정보 안내 사본' : '계정 삭제 안내 사본', use: '준비 시점 검토 사본 / 실제 제출란에는 공개 URL 사용' })),
  ];
  const allowed = new Set(['00-START-HERE.html', 'README.md', 'MANIFEST.json', ...records.map(item => item.destination)].map(name => name.toLowerCase()));
  if (allowed.size !== records.length + 3) fail('Duplicate destination path');
  for (const item of records) { safeRelative(item.source); safeRelative(item.destination); inside(root, path.resolve(root, item.source)); inside(output, path.resolve(output, item.destination)); }
  const sourceMap = new Map(records.map(item => [path.resolve(root, item.source).toLowerCase(), item.destination]));
  const prepared = [];
  for (const item of records) {
    const sourcePath = path.join(root, item.source); await noSymlinks(sourcePath);
    const stat = await fs.stat(sourcePath);
    if (!stat.isFile() || stat.size > 100 * 1024 * 1024) fail('Unexpected source file');
    const sourceBytes = await fs.readFile(sourcePath), sourceSha256 = sha256(sourceBytes);
    if (item.sha256 && sourceSha256 !== item.sha256) fail(`Reviewed source hash changed: ${item.id}`);
    const scan = scanArtifact(sourceBytes, item.destination);
    let bytes = sourceBytes, transformations = [];
    if (item.destination.endsWith('.md')) {
      const result = rewriteMarkdown(sourceBytes.toString('utf8'), item.source, item.destination, sourceMap);
      bytes = Buffer.from(result.text); transformations = result.changes;
    } else if (item.destination.endsWith('.html')) {
      let count = 0;
      const text = sourceBytes.toString('utf8').replace(/href=(["'])\/(?!\/)([^"']*)\1/g, (_, quote, target) => { count++; return `href=${quote}https://tteumsae-backend-one.vercel.app/${target}${quote}`; });
      bytes = Buffer.from(text); if (count) transformations.push({ type: 'policy-root-href-to-production', count });
    }
    if (/\.(md|html)$/.test(item.destination)) { scanText(bytes.toString('utf8'), item.destination); checkLinks(bytes.toString('utf8'), item.destination, allowed); }
    prepared.push({ ...item, bytes, sourceSha256, transformations, scan });
  }
  const git = { commit: execFileSync('git', ['rev-parse','HEAD'], { cwd: root, encoding: 'utf8' }).trim(), dirty: Boolean(execFileSync('git', ['status','--porcelain'], { cwd: root, encoding: 'utf8' }).trim()) };
  const preparedAt = new Date().toISOString();
  for (const [destination, text] of [['00-START-HERE.html', landing(config, records, preparedAt)], ['README.md', readme(config, records, git, preparedAt)]]) {
    scanText(text, destination); checkLinks(text, destination, allowed); prepared.push({ destination, bytes: Buffer.from(text), transformations: [], scan: {type:'generated-full-text-patterns'} });
  }
  await noSymlinks(output, true);
  const outputStat = await fs.lstat(output).catch(error => { if (error.code === 'ENOENT') return null; throw error; });
  if (outputStat && !outputStat.isDirectory()) fail('Output is not a directory');
  const existing = outputStat ? await listFiles(output) : [];
  if (existing.some(name => !allowed.has(name.toLowerCase()) && name !== 'INCOMPLETE.txt')) fail('Unexpected existing output files; refusing to overwrite');
  for (const item of prepared) await noSymlinks(path.join(output, item.destination), true);
  const entries = prepared.map(({bytes,...item}) => ({ path:item.destination, bytes:bytes.length, sha256:sha256(bytes), ...(item.source ? {source:item.source,sourceSha256:item.sourceSha256} : {}), ...(item.captureVersion ? {captureVersion:item.captureVersion} : {}), transformations:item.transformations, secretPatternScan:item.scan }));
  const manifest = { schemaVersion:1, preparedAt, appVersion:'0.13.1', versionCode:34, locationMode:'manual', appSourceCommit:config.appSourceCommit, gitCommit:git.commit, gitWorktreeDirty:git.dirty, configSha256:sha256(configBytes), publicApkVerifiedAt:config.publicApkVerifiedAt, storeLastVerifiedApkCode:config.storeLastVerifiedApkCode, storeVerifiedAt:config.storeVerifiedAt, submissionStatus:'NOT_SUBMITTED_WAIT_FOR_TEAM_REVIEW_AND_NEW_USER_GO', storePublicUrl:null, checks:{allowlist:'passed',reviewedArtifactHashes:'passed',localLinks:'passed',readableTextSecretPatterns:'passed',visualReview:'config-attested; not replaced by text scans'}, files:entries };
  const manifestBytes = Buffer.from(JSON.stringify(manifest, null, 2) + '\n'); scanText(manifestBytes.toString('utf8'), 'MANIFEST.json');
  if (!build) { console.log(JSON.stringify({mode:'check',output,fileCount:allowed.size,apkSha256:APK_SHA,checks:manifest.checks,noWrites:true},null,2)); return; }
  await fs.mkdir(output, { recursive:true });
  const marker = path.join(output, 'INCOMPLETE.txt');
  await noSymlinks(marker, true);
  await fs.writeFile(marker, '생성 중 또는 실패한 묶음입니다. 이 파일이 있으면 공유·압축하지 마세요.\n', 'utf8');
  for (const item of [...prepared,{destination:'MANIFEST.json',bytes:manifestBytes}]) {
    const target = path.join(output,item.destination); inside(output,target); await noSymlinks(target,true);
    await fs.mkdir(path.dirname(target),{recursive:true}); await fs.writeFile(target,item.bytes);
    if (sha256(await fs.readFile(target)) !== sha256(item.bytes)) fail('Written file hash mismatch; do not share output');
  }
  const actual = (await listFiles(output)).filter(name => name !== 'INCOMPLETE.txt');
  if (actual.length !== allowed.size || actual.some(name => !allowed.has(name.toLowerCase()))) fail('Final allowlist mismatch; do not share output');
  // Only this exact generated marker is removed. No recursive delete or workspace cleanup.
  inside(output,marker); await noSymlinks(marker); await fs.unlink(marker);
  console.log(JSON.stringify({mode:'build',output,fileCount:actual.length,apkSha256:APK_SHA,manifestSha256:sha256(manifestBytes),checks:manifest.checks,zipCreated:false},null,2));
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(error => { console.error(error.message); process.exitCode = 1; });
}

export { validateConfig, safeRelative, rewriteMarkdown, checkLinks, scanText, scanPptx };
