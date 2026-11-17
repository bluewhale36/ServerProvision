// 앱이 뜨지 않아도 마크업 조각과 CSS 를 앱 시트 위에 렌더해 본다. 새 CSS 변형이나 화면 조각을 제안하기 전에 쓴다.
// 폭마다 스크린샷을 남기고, 화면 점검 세 도구와 같은 검사(겹친 구분선, 넘침, 7절 표기)를 돌린다.
// 사용: node snippet-check.mjs <조각.html> [출력 폴더 = out/snippet] [폭,폭 = 1440,1024,390] [--sheets a.css,b.css] [--css 추가.css]
//   조각.html : body 안에 들어갈 마크업만 쓴다(html, head 없이).
//   --sheets  : 기본 시트(reset, style, form-validation, table-list) 뒤에 더 얹을 앱 시트. static 기준 경로(예: css/miller.css,provisioning/setting.css)
//   --css     : 아직 앱에 없는 제안 CSS 파일(로컬 경로). 앱 시트보다 뒤에 얹는다
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { openBrowser, BAD, copyLinesInPage, lineHitsInPage, overflowInPage } from './lib.mjs';

const args = process.argv.slice(2);
const flag = name => { const i = args.indexOf(name); if (i < 0) return null; const v = args[i + 1]; args.splice(i, 2); return v; };
const extraSheets = (flag('--sheets') || '').split(',').filter(Boolean);
const proposalCss = flag('--css');
const [snippetFile, outDir = 'out/snippet', widthsArg = '1440,1024,390'] = args;
if (!snippetFile) { console.error('사용: node snippet-check.mjs <조각.html> [출력 폴더] [폭,폭] [--sheets a.css] [--css 제안.css]'); process.exit(2); }

const STATIC = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../src/main/resources/static');
// 화면 공통 머리(fragments/layout.html commonHead)와 같은 순서의 기본 시트
const BASE_SHEETS = ['fonts/ibm-plex-sans-kr/ibm-plex-sans-kr.css', 'fonts/ibm-plex-mono/ibm-plex-mono.css', 'global/reset.css', 'global/style.css', 'css/form-validation.css', 'css/table-list.css'];
const links = [...BASE_SHEETS, ...extraSheets].map(s => {
  const f = path.join(STATIC, s);
  if (!fs.existsSync(f)) { console.error(`시트 없음: ${s}`); process.exit(2); }
  return `<link rel="stylesheet" href="${pathToFileURL(f)}">`;
});
if (proposalCss) links.push(`<link rel="stylesheet" href="${pathToFileURL(path.resolve(proposalCss))}">`);

fs.mkdirSync(outDir, { recursive: true });
const pageFile = path.resolve(outDir, 'page.html');
// 본문 자리는 앱의 내용 칸처럼 바탕색 위 여백 24px 이다. 폭은 뷰포트가 정한다
fs.writeFileSync(pageFile, `<!doctype html><html lang="ko"><head><meta charset="utf-8">${links.join('')}
<style>body { padding: 24px; background: var(--n-bg); }</style></head><body>
${fs.readFileSync(snippetFile, 'utf8')}
</body></html>`);

const report = [];
const { browser, page } = await openBrowser();
for (const width of widthsArg.split(',').map(Number)) {
  await page.setViewportSize({ width, height: 900 });
  await page.goto(pathToFileURL(pageFile).href);
  await page.waitForTimeout(300);
  const shot = path.join(outDir, `w${width}.png`);
  await page.screenshot({ path: shot, fullPage: true });
  const lines = await page.evaluate(lineHitsInPage, 44);
  const overflow = await page.evaluate(overflowInPage);
  report.push({ width, shot, overlaps: lines.filter(h => h.kind === '겹침'), emptyDividers: lines.filter(h => h.kind !== '겹침'), ...overflow });
}
const copy = [...new Set((await page.evaluate(copyLinesInPage)).filter(t => /[가-힣]/.test(t) && BAD.test(t)))];
await browser.close();

fs.writeFileSync(path.join(outDir, 'report.json'), JSON.stringify({ copy, widths: report }, null, 1));
for (const r of report) {
  console.log(`${r.width}px  겹침 ${r.overlaps.length}  빈 구분선 ${r.emptyDividers.length}  페이지 넘침 ${r.pageOverflow}px  칸 넘침 ${r.items.length}  → ${r.shot}`);
  for (const h of [...r.overlaps, ...r.emptyDividers]) console.log(`   ${h.kind} ${h.dy}px  ${h.a}  ⟂  ${h.b}`);
  for (const i of r.items) console.log('   넘침 ' + i);
}
console.log(`7절 표기 의심 ${copy.length}`);
for (const t of copy) console.log('   ' + t.slice(0, 140));
console.log('스크린샷은 눈으로도 본다. 검사는 말줄임에 가린 표지, 위계가 뒤집힌 색처럼 숫자로 안 잡히는 것을 놓친다.');
