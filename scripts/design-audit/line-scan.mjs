// 렌더된 화면에서 DESIGN.md 5절 '구분선' 위반을 찾는다.
//  - 겹침: 두 선이 2.5px 안에 포개져 굵은 한 줄 또는 두 줄로 보인다
//  - 빈 구분선: 사이에 글자가 없는 두 선이 GAP px 안에 나란하다(한쪽이 판 테두리면 다른 쪽 구분선은 불필요)
// 판(네 변 테두리 상자)끼리 떨어져 선 것은 정상 간격이라 세지 않는다. border-collapse 표 안의 같은 자리 선은 하나로 그려지므로 세지 않는다.
// 폭마다 따로 센다. 1024 이하에서 열이 쌓이는 컴포넌트(Miller 등)는 그 폭에서만 선이 겹친다.
// 사용: node line-scan.mjs urls.txt out/lines.json [폭,폭 = 1440,1024,360] [GAP=44]
import fs from 'node:fs';
import { openSession, visit, readUrls, lineHitsInPage } from './lib.mjs';

const [urlFile, outFile = 'out/lines.json', widthsArg = '1440,1024,360', gapArg] = process.argv.slice(2);
const GAP = Number(gapArg || 44);
const out = [];
for (const width of widthsArg.split(',').map(Number)) {
  const { browser, page } = await openSession({ width, height: 900 });
  for (const u of readUrls(urlFile)) {
    try { const v = await visit(page, u); if (!v.ok) continue; } catch (e) { continue; }
    const found = await page.evaluate(lineHitsInPage, GAP);
    for (const h of found) out.push({ width, url: u, ...h });
  }
  await browser.close();
}
fs.mkdirSync(new URL('.', 'file://' + process.cwd() + '/' + outFile).pathname, { recursive: true });
fs.writeFileSync(outFile, JSON.stringify(out, null, 1));
const byPair = new Map();
for (const h of out) { const k = `${h.width}  ${h.kind} ${h.dy}px  ${h.a}  ⟂  ${h.b}`; if (!byPair.has(k)) byPair.set(k, new Set()); byPair.get(k).add(h.url); }
console.log(`hits=${out.length} patterns=${byPair.size}`);
for (const [k, s] of [...byPair.entries()].sort((a, b) => b[1].size - a[1].size)) console.log(`${s.size}\t${k}\t${[...s][0]}`);
