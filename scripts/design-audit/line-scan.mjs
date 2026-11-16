// 렌더된 화면에서 DESIGN.md 5절 '구분선' 위반을 찾는다.
//  - 겹침: 두 선이 2.5px 안에 포개져 굵은 한 줄 또는 두 줄로 보인다
//  - 빈 구분선: 사이에 글자가 없는 두 선이 GAP px 안에 나란하다(한쪽이 판 테두리면 다른 쪽 구분선은 불필요)
// 판(네 변 테두리 상자)끼리 떨어져 선 것은 정상 간격이라 세지 않는다. border-collapse 표 안의 같은 자리 선은 하나로 그려지므로 세지 않는다.
// 사용: node line-scan.mjs urls.txt out/lines.json [GAP=44]
import fs from 'node:fs';
import { openSession, visit, readUrls } from './lib.mjs';

const [urlFile, outFile = 'out/lines.json', gapArg] = process.argv.slice(2);
const GAP = Number(gapArg || 44);
const { browser, page } = await openSession();
const out = [];
for (const u of readUrls(urlFile)) {
  try { const v = await visit(page, u); if (!v.ok) continue; } catch (e) { continue; }
  const found = await page.evaluate((GAP) => {
    const sel = e => { const c = [...e.classList].slice(0, 3).join('.'); return e.tagName.toLowerCase() + (e.id ? '#' + e.id : '') + (c ? '.' + c : ''); };
    const path = e => { const a = []; for (let x = e; x && x !== document.body && a.length < 3; x = x.parentElement) a.unshift(sel(x)); return a.join(' > '); };
    const lines = [];
    for (const e of document.querySelectorAll('body *')) {
      const r = e.getBoundingClientRect(); if (r.width < 80 || r.height === 0) continue;
      if (/^(INPUT|SELECT|TEXTAREA|BUTTON|OPTION|IMG|SVG|CODE|KBD)$/.test(e.tagName) || e.matches('.n-btn,.n-badge,.n-group-badge,.n-chip,.n-tab,.n-input,.n-select')) continue;
      const cs = getComputedStyle(e); if (cs.visibility === 'hidden' || cs.display === 'none') continue;
      for (const side of ['top', 'bottom']) {
        const w = parseFloat(cs['border-' + side + '-width']), col = cs['border-' + side + '-color'], st = cs['border-' + side + '-style'];
        if (!w || st === 'none' || /rgba\(\d+, \d+, \d+, 0\)/.test(col)) continue;
        const y = (side === 'top' ? r.top : r.bottom) + scrollY;
        const host = (e.tagName === 'TD' || e.tagName === 'TH') ? e.parentElement : e;   // 칸의 선은 행 단위로 합친다
        const hr = host.getBoundingClientRect();
        const box = host === e && parseFloat(cs['border-left-width']) > 0 && parseFloat(cs['border-right-width']) > 0;
        const tb = host.closest('table'); const tbl = tb && getComputedStyle(tb).borderCollapse === 'collapse' ? (tb.id || path(tb)) : null;
        lines.push({ y: Math.round(y * 2) / 2, x1: hr.left, x2: hr.right, side, el: path(host), box, tbl });
      }
    }
    const seen = new Set(), uniq = [];
    for (const l of lines) { const k = `${l.y}|${Math.round(l.x1)}|${Math.round(l.x2)}|${l.el}`; if (!seen.has(k)) { seen.add(k); uniq.push(l); } }
    uniq.sort((a, b) => a.y - b.y);
    const texts = [...document.querySelectorAll('body *')].filter(t => [...t.childNodes].some(n => n.nodeType === 3 && n.textContent.trim())).map(t => { const r = t.getBoundingClientRect(); return { t: r.top + scrollY, b: r.bottom + scrollY, l: r.left, r: r.right }; });
    const hits = [];
    for (let i = 0; i < uniq.length; i++) for (let j = i + 1; j < uniq.length; j++) {
      const a = uniq[i], c = uniq[j], dy = c.y - a.y; if (dy > GAP) break;
      if (a.el === c.el) continue;
      if (a.tbl && a.tbl === c.tbl && dy <= 1.5) continue;
      const ov = Math.min(a.x2, c.x2) - Math.max(a.x1, c.x1); if (ov < 0.6 * Math.min(a.x2 - a.x1, c.x2 - c.x1)) continue;
      const stacked = dy <= 2.5; if (!stacked && a.box && c.box) continue;
      const lo = Math.max(a.x1, c.x1), hi = Math.min(a.x2, c.x2);
      const text = dy > 2 && texts.some(t => t.b > a.y + 1 && t.t < c.y - 1 && t.r > lo && t.l < hi);
      if (!text) hits.push({ kind: stacked ? '겹침' : '빈 구분선', dy: Math.round(dy * 10) / 10, a: `${a.el} [${a.side}${a.box ? ' 상자' : ' 선'}]`, b: `${c.el} [${c.side}${c.box ? ' 상자' : ' 선'}]` });
    }
    return hits;
  }, GAP);
  for (const h of found) out.push({ url: u, ...h });
}
fs.mkdirSync(new URL('.', 'file://' + process.cwd() + '/' + outFile).pathname, { recursive: true });
fs.writeFileSync(outFile, JSON.stringify(out, null, 1));
const byPair = new Map();
for (const h of out) { const k = `${h.kind} ${h.dy}px  ${h.a}  ⟂  ${h.b}`; if (!byPair.has(k)) byPair.set(k, new Set()); byPair.get(k).add(h.url); }
console.log(`hits=${out.length} patterns=${byPair.size}`);
for (const [k, s] of [...byPair.entries()].sort((a, b) => b[1].size - a[1].size)) console.log(`${s.size}\t${k}\t${[...s][0]}`);
await browser.close();
