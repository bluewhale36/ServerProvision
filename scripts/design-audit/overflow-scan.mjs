// 글자 크기나 문구를 바꾼 뒤의 넘침을 찾는다. 폭마다 (1) 페이지 가로 넘침 (2) 칸 밖으로 나간 표 칸과 배지를 센다.
// 사용: node overflow-scan.mjs urls.txt out/overflow.json [폭,폭 = 1440,1024,360]
import fs from 'node:fs';
import { openSession, visit, readUrls, overflowInPage } from './lib.mjs';

const [urlFile, outFile = 'out/overflow.json', widthsArg = '1440,1024,360'] = process.argv.slice(2);
const out = [];
for (const width of widthsArg.split(',').map(Number)) {
  const { browser, page } = await openSession({ width, height: 900 });
  for (const u of readUrls(urlFile)) {
    try { const v = await visit(page, u); if (!v.ok) continue; } catch (e) { continue; }
    const r = await page.evaluate(overflowInPage);
    if (r.pageOverflow > 0 || r.items.length) out.push({ width, url: u, ...r });
  }
  await browser.close();
}
fs.mkdirSync(new URL('.', 'file://' + process.cwd() + '/' + outFile).pathname, { recursive: true });
fs.writeFileSync(outFile, JSON.stringify(out, null, 1));
console.log(`hits=${out.length}`);
for (const h of out) { console.log(`${h.width} ${h.url} page+${h.pageOverflow}px`); for (const i of h.items) console.log('   ' + i); }
