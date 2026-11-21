// 공용: 브라우저 문맥(로그인한 샌드박스 또는 파일 스니펫)과 화면 점검 함수. 샌드박스 점검은 읽기 전용이라 GET 외 요청(로그인 제외)은 막는다.
import { createRequire } from 'node:module';
import fs from 'node:fs';
const require = createRequire(import.meta.url);
const { chromium } = require('playwright-core');

export const BASE = process.env.SPV_BASE || 'http://localhost:7797';

export function readUrls(file) {
  return fs.readFileSync(file, 'utf8').split('\n').map(s => s.trim()).filter(s => s && !s.startsWith('#'));
}

// 로그인 없는 브라우저(파일로 만든 스니펫 페이지용, snippet-check.mjs)
export async function openBrowser({ width = 1440, height = 900 } = {}) {
  const browser = await chromium.launch({ channel: process.env.SPV_BROWSER || 'chrome' });
  const page = await browser.newPage({ viewport: { width, height } });
  return { browser, page };
}

export async function openSession({ width = 1440, height = 900 } = {}) {
  const user = process.env.SPV_USER, pass = process.env.SPV_PASS;
  if (!user || !pass) throw new Error('SPV_USER, SPV_PASS 환경변수로 샌드박스 계정을 넘기십시오.');
  const browser = await chromium.launch({ channel: process.env.SPV_BROWSER || 'chrome' });
  const ctx = await browser.newContext({ viewport: { width, height } });
  await ctx.route('**/*', r => (r.request().method() !== 'GET' && !r.request().url().endsWith('/login')) ? r.abort() : r.continue());
  const page = await ctx.newPage();
  await page.goto(BASE + '/login');
  await page.fill('input[name=username]', user);
  await page.fill('input[name=password]', pass);
  await Promise.all([page.waitForNavigation(), page.click('button[type=submit]')]);
  return { browser, page };
}

// SSE 를 여는 화면은 networkidle 에 도달하지 않으므로 load 뒤 잠시 기다린다.
export async function visit(page, url) {
  const resp = await page.goto(BASE + url, { waitUntil: 'load', timeout: 20000 });
  await page.waitForTimeout(700);
  const status = resp ? resp.status() : 0;
  const title = await page.title();
  return { ok: status < 400 && !/오류/.test(title), status, title };
}

// DESIGN.md 7절 표기 위반(copy-scan, snippet-check 공용)
export const BAD = /[—·]|십시오|하세요|해요[.!]?$|주세요|(없음|있음|됨|뜀)(?=[\s.,)]|$)|(?<![가-힣])축(?:을|의|이|은|별|마다)?(?![가-힣])|갈래|렌즈|손잡이/;
// 받침 ㅁ 으로 끝나는 낱말 가운데 문맥상 명사인 것(검토 목록에서 뺀다). 판정이 끝난 낱말만 더한다.
export const NOUN_M = new Set(['다음','처음','이름','흐름','묶음','시점','지점','시스템','프로그램','지금','요금','마음','사람','그림','기점','관점','장점','단점','문제점','차이점','중점','만점','점','금','품','템','봄','밤','몸','힘','삶','앎','감','남','함','플랫폼','스트림','타임','네임','알림','이름표','바람','걸음','소름','고름','정점','종점','거점','쟁점','허점','논점','초점','점검','포함','볼륨','제품','새로고침','호스트네임','메인스트림','잠금','시스템','프로그램']);
export const endsWithMieum = w => { const c = w.charCodeAt(w.length - 1); return c >= 0xAC00 && c <= 0xD7A3 && (c - 0xAC00) % 28 === 16; };

// 아래 함수들은 page.evaluate 로 브라우저 안에서 돈다. 바깥 변수를 참조하지 않는다.

// 화면 글자와 title, placeholder, aria-label, data-*message 류 속성
export function copyLinesInPage() {
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
}

// DESIGN.md 5절 '구분선' 위반: 겹침(2.5px 안에 포갠 두 선), 빈 구분선(사이에 글자 없이 GAP px 안에 나란한 두 선)
export function lineHitsInPage(GAP) {
  const sel = e => { const c = [...e.classList].slice(0, 3).join('.'); return e.tagName.toLowerCase() + (e.id ? '#' + e.id : '') + (c ? '.' + c : ''); };
  const path = e => { const a = []; for (let x = e; x && x !== document.body && a.length < 3; x = x.parentElement) a.unshift(sel(x)); return a.join(' > '); };
  // 닫힌 details 의 몸통은 그려지지 않지만 상자 좌표는 그대로 가진다(content-visibility: hidden). 그 선을 세면 오탐이다.
  const shown = e => !e.checkVisibility || e.checkVisibility({ contentVisibilityAuto: true });
  const lines = [];
  for (const e of document.querySelectorAll('body *')) {
    const r = e.getBoundingClientRect(); if (r.width < 80 || r.height === 0) continue;
    if (/^(INPUT|SELECT|TEXTAREA|BUTTON|OPTION|IMG|SVG|CODE|KBD)$/.test(e.tagName) || e.matches('.n-btn,.n-badge,.n-group-badge,.n-chip,.n-tab,.n-input,.n-select')) continue;
    const cs = getComputedStyle(e); if (cs.visibility === 'hidden' || cs.display === 'none') continue;
    if (!shown(e)) continue;
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
  const texts = [...document.querySelectorAll('body *')].filter(t => shown(t) && [...t.childNodes].some(n => n.nodeType === 3 && n.textContent.trim())).map(t => { const r = t.getBoundingClientRect(); return { t: r.top + scrollY, b: r.bottom + scrollY, l: r.left, r: r.right }; });
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
}

// 페이지 가로 넘침과 칸 밖으로 나간 표 칸, 배지, 버튼, 칩
export function overflowInPage() {
  const sel = e => e.tagName.toLowerCase() + (e.id ? '#' + e.id : '') + ([...e.classList].slice(0, 2).map(c => '.' + c).join(''));
  const vis = e => { const b = e.getBoundingClientRect(); return b.width > 0 && b.height > 0; };
  const pageOverflow = document.documentElement.scrollWidth - document.documentElement.clientWidth;
  const items = [];
  for (const el of document.querySelectorAll('td, th, .n-badge, .n-btn, .n-chip')) {
    if (!vis(el)) continue;
    if (el.scrollWidth > el.clientWidth + 1) items.push(`${sel(el)}: ${el.textContent.replace(/\s+/g, ' ').trim().slice(0, 40)} (+${el.scrollWidth - el.clientWidth}px)`);
  }
  // 표만 가로로 밀려야 한다. 표 곁의 블록까지 함께 미는 스크롤 상자는 라벨을 왼쪽 밖으로 자른다(R21-1 CP6, DESIGN.md 4절 목록 표)
  for (const el of document.querySelectorAll('body *')) {
    const cs = getComputedStyle(el);
    if (!/(auto|scroll)/.test(cs.overflowX) || el.scrollWidth <= el.clientWidth + 1 || !vis(el)) continue;
    if (!el.querySelector(':scope > table')) continue;
    const others = [...el.children].filter(k => k.tagName !== 'TABLE' && vis(k));
    if (others.length) items.push(`${sel(el)}: 표 곁의 ${others.length} 블록까지 함께 가로로 밀린다(${others.slice(0, 2).map(sel).join(', ')})`);
  }
  return { pageOverflow, items: items.slice(0, 15) };
}
