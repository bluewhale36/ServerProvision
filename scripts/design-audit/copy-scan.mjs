// 렌더된 화면 글자에서 DESIGN.md 7절 위반(대시, 가운뎃점, 하십시오체와 해요체, '없음/~됨' 꼴, 설계 어휘)을 센다.
// 받침이 ㅁ 인 음절로 끝나는 낱말은 위반이 아니라 '검토' 목록으로 따로 모은다(MANUAL.md 8절과 같은 방식, 문맥으로 사람이 판정).
// 숨은 모달 본문과 title, placeholder, aria-label, data-*message 류 속성까지 본다.
// 사용: node copy-scan.mjs urls.txt out/copy.json
import fs from 'node:fs';
import { openSession, visit, readUrls } from './lib.mjs';

const [urlFile, outFile = 'out/copy.json'] = process.argv.slice(2);
const BAD = /[—·]|십시오|하세요|해요[.!]?$|주세요|(없음|있음|됨|뜀)(?=[\s.,)]|$)|(?<![가-힣])축(?:을|의|이|은|별|마다)?(?![가-힣])|갈래|렌즈|손잡이/;
// 받침 ㅁ 으로 끝나는 낱말 가운데 문맥상 명사인 것(검토 목록에서 뺀다). 판정이 끝난 낱말만 더한다.
const NOUN_M = new Set(['다음','처음','이름','흐름','묶음','시점','지점','시스템','프로그램','지금','요금','마음','사람','그림','기점','관점','장점','단점','문제점','차이점','중점','만점','점','금','품','템','봄','밤','몸','힘','삶','앎','감','남','함','플랫폼','스트림','타임','네임','알림','이름표','바람','걸음','소름','고름','정점','종점','거점','쟁점','허점','논점','초점','점검','포함','볼륨','제품','새로고침','호스트네임','메인스트림','잠금','시스템','프로그램']);
const endsWithMieum = w => { const c = w.charCodeAt(w.length - 1); return c >= 0xAC00 && c <= 0xD7A3 && (c - 0xAC00) % 28 === 16; };
const { browser, page } = await openSession();
const hits = new Map(), review = new Map(), broken = [];
for (const u of readUrls(urlFile)) {
  let v; try { v = await visit(page, u); } catch (e) { broken.push(u + ' timeout'); continue; }
  if (!v.ok) { broken.push(`${u} ${v.status} ${v.title}`); continue; }
  const lines = await page.evaluate(() => {
    const out = []; if (!document.body) return out;
    const w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    for (let n; (n = w.nextNode());) {
      const el = n.parentElement; if (!el || el.closest('script,style,noscript')) continue;
      const t = n.textContent.replace(/\s+/g, ' ').trim(); if (t) out.push(t);
    }
    document.querySelectorAll('[title],[placeholder],[aria-label]').forEach(e => {
      for (const a of ['title', 'placeholder', 'aria-label']) { const v = e.getAttribute(a); if (v) out.push(`[${a}] ${v}`); }
    });
    document.querySelectorAll('*').forEach(e => { for (const a of e.attributes) {
      if (/^data-.*(message|title|label|confirm|text|reason|hint)/.test(a.name) && /[가-힣]/.test(a.value)) out.push(`[${a.name}] ${a.value}`);
    } });
    return out;
  });
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
