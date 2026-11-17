// 공용: 로그인한 브라우저 문맥을 연다. 읽기 전용 점검이라 GET 외 요청(로그인 제외)은 막는다.
import { createRequire } from 'node:module';
import fs from 'node:fs';
const require = createRequire(import.meta.url);
const { chromium } = require('playwright-core');

export const BASE = process.env.SPV_BASE || 'http://localhost:7797';

export function readUrls(file) {
  return fs.readFileSync(file, 'utf8').split('\n').map(s => s.trim()).filter(s => s && !s.startsWith('#'));
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
