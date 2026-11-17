// 렌더된 화면 글자에서 DESIGN.md 7절 위반(대시, 가운뎃점, 하십시오체와 해요체, '없음/~됨' 꼴, 설계 어휘)을 센다.
// 받침이 ㅁ 인 음절로 끝나는 낱말은 위반이 아니라 '검토' 목록으로 따로 모은다(MANUAL.md 8절과 같은 방식, 문맥으로 사람이 판정).
// 숨은 모달 본문과 title, placeholder, aria-label, data-*message 류 속성까지 본다.
// 사용: node copy-scan.mjs urls.txt out/copy.json
import fs from 'node:fs';
import { openSession, visit, readUrls, BAD, NOUN_M, endsWithMieum, copyLinesInPage } from './lib.mjs';

const [urlFile, outFile = 'out/copy.json'] = process.argv.slice(2);
const { browser, page } = await openSession();
const hits = new Map(), review = new Map(), broken = [];
for (const u of readUrls(urlFile)) {
  let v; try { v = await visit(page, u); } catch (e) { broken.push(u + ' timeout'); continue; }
  if (!v.ok) { broken.push(`${u} ${v.status} ${v.title}`); continue; }
  const lines = await page.evaluate(copyLinesInPage);
  for (const t of lines) {
    if (!/[가-힣]/.test(t)) continue;
    if (BAD.test(t)) { if (!hits.has(t)) hits.set(t, new Set()); hits.get(t).add(u); }
    for (const w of (t.match(/[가-힣]+/g) || [])) if (endsWithMieum(w) && !NOUN_M.has(w)) { if (!review.has(w)) review.set(w, new Set()); review.get(w).add(t.slice(0, 80)); }
  }
}
const rows = [...hits.entries()].map(([t, s]) => ({ text: t, pages: s.size, urls: [...s].slice(0, 3) })).sort((a, b) => b.pages - a.pages);
fs.mkdirSync(new URL('.', 'file://' + process.cwd() + '/' + outFile).pathname, { recursive: true });
const reviewRows = [...review.entries()].map(([w, s]) => ({ word: w, samples: [...s].slice(0, 3) })).sort((a, b) => a.word.localeCompare(b.word));
fs.writeFileSync(outFile, JSON.stringify({ broken, rows, review: reviewRows }, null, 1));
console.log(`pages=${readUrls(urlFile).length} broken=${broken.length} distinct=${rows.length}`);
for (const b of broken) console.log('BROKEN ' + b);
for (const r of rows.slice(0, 40)) console.log(`${r.pages}\t${r.text.slice(0, 140)}\t${r.urls[0]}`);
console.log(`검토(받침 ㅁ 낱말) ${reviewRows.length}종: ` + reviewRows.slice(0, 60).map(r => r.word).join(' '));
console.log('사용자 입력 데이터(정의서 이름, 메모 등)도 함께 잡히므로 화면 문구인지 사람이 가른다. 검토 목록의 명사는 NOUN_M 에 더한다.');
await browser.close();
