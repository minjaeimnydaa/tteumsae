import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

// Copies an explicit, reviewed allowlist. Never archives the workspace or signing backup.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const output = path.join(root, 'output', 'team-review-20260913-1100');
const expectedApkHash = '895F46C5AF53EF24A8E38A4A73DD4D4F3B660C87EEAE0A667F90D21917F6AFB6';
const expectedRightsHash = '08A167570BBED54B3156667360163203AA87DE68F95E5537B3A016ED6942872D';
const files = [
  ['output/release-0.13.0/tteumsae-v0.13.0-33-manual-release.apk', '01-app/tteumsae-v0.13.0-33-manual-release.apk'],
  ...['pdf', 'pptx'].map(ext => [`output/presentations/tteumsae-contest-functions-0.13.0-code32-review-v3.${ext}`, `02-contest/tteumsae-contest-functions-0.13.0-code32-review-v3.${ext}`]),
  ['output/presentations/README.md', '02-contest/README.md'],
  ['output/pdf/tteumsae-third-party-rights-0.13.0-code33.pdf', '03-onestore/tteumsae-third-party-rights-0.13.0-code33.pdf'],
  ...['icon-512.png', 'banner-1024x578.png', 'screen-01-home.png', 'screen-02-route-input.png', 'screen-03-tourist-results.png', 'screen-04-tourist-map.png', 'screen-05-tourist-detail.png', 'screen-06-tourist-explore.png'].map(name => [`output/release-0.13.0/store/${name}`, `03-onestore/images/${name}`]),
  ['docs/24_ONESTORE_FINAL_HANDOFF.md', '03-onestore/24_ONESTORE_FINAL_HANDOFF.md'],
  ['output/pdf/tteumsae-team-review-20260913-1100.pdf', '04-review/tteumsae-team-review-20260913-1100.pdf'],
  ...['23_TEAM_REVIEW_2026-09-13.md', '25_CONTEST_FINAL_HANDOFF.md', '26_RELEASE_REVIEW_RISKS.md', '21_THIRD_PARTY_RIGHTS_EVIDENCE.md', '22_ANDROID_LICENSE_NOTICES.md'].map(name => [`docs/${name}`, `04-review/${name}`]),
  ...['privacy.html', 'account-deletion.html'].map(name => [`backend/${name}`, `05-policies/${name}`]),
  ...['release-33-collapsed-search.png', 'release-33-home-restored.png', 'release-33-real-reminder-kakao.png', 'release-33-license-list.png'].map(name => [`tmp/release-qa-final-20260913/${name}`, `06-evidence/${name}`]),
];
const sha256 = bytes => crypto.createHash('sha256').update(bytes).digest('hex').toUpperCase();
const relative = (from, to) => path.relative(from, to).split(path.sep).join('/');
const assertInside = (parent, target) => {
  const rel = path.relative(parent, target);
  if (!rel || rel.startsWith('..') || path.isAbsolute(rel)) throw new Error('Unsafe package path');
};
const privateValue = /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|sb_secret_[A-Za-z0-9_-]{16,}|ghp_[A-Za-z0-9]{20,}|sk_live_[A-Za-z0-9]{12,}|postgres(?:ql)?:\/\/[^\s]+:[^\s]+@|\b01[016789][ -]?\d{3,4}[ -]?\d{4}\b/i;
const markdownTargets = new Map(files.filter(([s]) => s.startsWith('docs/')).map(([s, d]) => [path.basename(s), d]));
const entries = [];
const prepared = [];

// Validate every input before touching the output directory.
for (const [source, destination] of files) {
  const sourcePath = path.resolve(root, source);
  const destinationPath = path.resolve(output, destination);
  assertInside(root, sourcePath);
  assertInside(output, destinationPath);
  if (!/\.(?:apk|png|pdf|pptx|md|html)$/.test(destination)) throw new Error('Unexpected asset type');
  const sourceBytes = await fs.readFile(sourcePath);
  const sourceSha256 = sha256(sourceBytes);
  if (destination.endsWith('.apk') && sourceSha256 !== expectedApkHash) throw new Error('APK mismatch');
  if (destination.endsWith('third-party-rights-0.13.0-code33.pdf') && sourceSha256 !== expectedRightsHash) throw new Error('Rights PDF mismatch');
  let bytes = sourceBytes;
  if (/\.(md|html)$/.test(destination)) {
    let text = sourceBytes.toString('utf8');
    if (privateValue.test(text)) throw new Error(`Private value check failed: ${destination}`);
    if (destination.endsWith('.md')) {
      text = text.replace(/\]\(([^)]+\.md)\)/g, (whole, target) => {
        const resolved = markdownTargets.get(target);
        return resolved ? `](${relative(path.dirname(destinationPath), path.resolve(output, resolved))})` : whole;
      });
    }
    if (destination.endsWith('.html')) {
      text = text.replace(/href="\/(?!\/)([^"]*)"/g, 'href="https://tteumsae-backend-one.vercel.app/$1"');
    }
    bytes = Buffer.from(text);
  }
  prepared.push({ source, destination, destinationPath, bytes, sourceSha256 });
}
const destinations = new Set(files.map(([, d]) => d));
if (destinations.size !== files.length) throw new Error('Duplicate output names');
const allowed = new Set([...destinations, 'README.md', 'MANIFEST.json']);
// Reject existing links or unrelated output files before writing any artifact.
for (const directory of [path.dirname(output), output]) {
  const stat = await fs.lstat(directory).catch(error => {
    if (error.code === 'ENOENT') return null;
    throw error;
  });
  if (stat && (!stat.isDirectory() || stat.isSymbolicLink())) throw new Error('Unsafe output directory');
}
const previous = await listFiles(output).catch(error => {
  if (error.code === 'ENOENT') return [];
  throw error;
});
if (previous.some(name => !allowed.has(name))) throw new Error('Unrelated output files: refusing to overwrite');
for (const item of prepared) {
  await fs.mkdir(path.dirname(item.destinationPath), { recursive: true });
  await fs.writeFile(item.destinationPath, item.bytes);
  const copied = await fs.readFile(item.destinationPath);
  if (sha256(copied) !== sha256(item.bytes)) throw new Error('Copy verification failed');
  entries.push({ path: item.destination, bytes: copied.length, sha256: sha256(copied), source: item.source, sourceSha256: item.sourceSha256 });
}
const preparedAt = new Date().toISOString();
const gitCommit = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim();
const gitWorktreeDirty = Boolean(execFileSync('git', ['status', '--porcelain'], { cwd: root, encoding: 'utf8' }).trim());
const readme = `# 틈새 - 오전 11시 팀 검토 묶음

**최종 심사 신청 전 자료입니다. 11시 팀 검토 후 사용자의 새 지시가 있어야 신청합니다.**
원스토어 공개 링크는 아직 없으며 이 ZIP 자체는 공모전 제출 파일이 아닙니다.

## 먼저 열기

1. [2쪽 검토 요약](04-review/tteumsae-team-review-20260913-1100.pdf)
2. [상세 검토 동선과 체크리스트](04-review/23_TEAM_REVIEW_2026-09-13.md)
3. [최종 앱 APK](01-app/tteumsae-v0.13.0-33-manual-release.apk)
4. [지정 기능설명서 PDF](02-contest/tteumsae-contest-functions-0.13.0-code32-review-v3.pdf) / [편집용 PPTX](02-contest/tteumsae-contest-functions-0.13.0-code32-review-v3.pptx)
5. [원스토어 등록 상태·복사용 심사 메모](03-onestore/24_ONESTORE_FINAL_HANDOFF.md)
6. [공모전 공식 요건·남은 입력](04-review/25_CONTEST_FINAL_HANDOFF.md)
7. [검증 범위·남은 위험](04-review/26_RELEASE_REVIEW_RISKS.md)

## 등록용 파일

- 최종 APK: **0.13.0 / code33 / 수동 출발지**. 자동 위치·debug·이전 APK는 포함하지 않았습니다.
- [권리 이용 근거 PDF](03-onestore/tteumsae-third-party-rights-0.13.0-code33.pdf)
- 스토어 이미지: 03-onestore/images 안 아이콘1개·배너1개·관광지 화면6개.
- 정책 사본: 05-policies. 준비 시점 본문을 보존하고 로컬에서 깨지는 연결만 공개 URL로 바꿨습니다.
- 검증 참고 화면: 06-evidence. 스토어 제출6장과 구분합니다. 알림은 약3분 늦게 수신된 사례입니다.

기능설명서 파일명의 code32는 캡처 기준입니다. 변경 없는 홈·설정 상단은 code31이며
code33 변경은 자료에 없는 접힌 지도 로고 위치 보완이라 검수한 v3를 그대로 사용합니다.
현재 포털은 code32 APK·이전 권리 PDF이며, 국가 저장 확인과 파일 교체가 남아 있습니다.

## 공유·보안

서명키 백업, 비밀번호, TourAPI·서버 비밀키, 계정·알림 XML, 원시 로그는 포함하지 않았습니다.
APK에 필요한 공개 클라이언트 설정은 서버 비밀키와 구분합니다.
TourAPI 신청자별 인증키는 공식 로그인 제출 화면에서 소유자가 직접 다룹니다.
팀원용 참고 자료이며 폴더 전체를 공모전이나 원스토어에 한 번에 첨부하지 않습니다.

## 링크와 식별

- [APK 안내](https://tteumsae-apk-six.vercel.app/) - 스토어 공개 링크 아님
- [개인정보처리방침](https://tteumsae-backend-one.vercel.app/privacy)
- [계정 삭제 안내](https://tteumsae-backend-one.vercel.app/account-deletion)
- [GitHub](https://github.com/Newrred/tteumsae)

APK SHA-256: ${expectedApkHash}

문서 소스 Git: ${gitCommit} · 생성 시각(UTC): ${preparedAt}
작업 트리: ${gitWorktreeDirty ? '미커밋 변경 있음 - Git은 기준 커밋이며 실제 파일은 MANIFEST 해시 기준' : '커밋 반영 완료'}

전체 파일의 크기·SHA-256·원본 경로는 MANIFEST.json에 있습니다.
`;
await fs.writeFile(path.join(output, 'README.md'), readme, 'utf8');
entries.push({ path: 'README.md', bytes: Buffer.byteLength(readme), sha256: sha256(Buffer.from(readme)) });
await fs.writeFile(path.join(output, 'MANIFEST.json'), JSON.stringify({
  preparedAt, gitCommit, gitWorktreeDirty, appSourceCommit: 'f248e3a', appVersion: '0.13.0', versionCode: 33,
  locationMode: 'manual', submissionStatus: 'NOT_SUBMITTED_WAIT_FOR_TEAM_REVIEW_AND_NEW_USER_GO',
  storeLastVerifiedApkCode: 32, files: entries,
}, null, 2) + '\n', 'utf8');

async function listFiles(directory) {
  const result = [];
  for (const entry of await fs.readdir(directory, { withFileTypes: true })) {
    const full = path.join(directory, entry.name);
    if (entry.isSymbolicLink()) throw new Error('Package cannot include symlinks');
    if (entry.isDirectory()) result.push(...await listFiles(full));
    else result.push(relative(output, full));
  }
  return result;
}
const actual = await listFiles(output);
if (actual.length !== allowed.size || actual.some(name => !allowed.has(name))) {
  throw new Error('Unexpected files in output: do not share or archive');
}
console.log(JSON.stringify({ output, fileCount: actual.length, gitCommit, gitWorktreeDirty, apkSha256: expectedApkHash, mdHtmlPrivatePatternCheck: 'passed', allowlistCheck: 'passed' }, null, 2));
