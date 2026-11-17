/*
 * 사용 매뉴얼의 공통 틀.
 * 페이지 파일은 <main class="m-main"> 본문만 갖고, 이 스크립트가 nav.json 을 읽어
 * 머리줄(검색 포함), 사이드 목차, 현재 위치, 페이지 내 목차, 이전/다음, 확인 일자를 그린다.
 * 목차의 정본은 nav.json 하나다. 페이지를 추가하면 nav.json 에 한 줄을 더한다.
 */
(function () {
    'use strict';

    const BASE = new URL('.', document.currentScript.src);   // .../manual/
    const NAV_URL = new URL('nav.json', BASE);
    const APP_ROOT = new URL('..', BASE);                      // 관리 화면 루트(컨텍스트 경로를 따른다)
    const RESULT_LIMIT = 12;
    const SNIPPET_RADIUS = 48;

    const state = { nav: null, pages: [], written: [], current: null, index: null, indexing: null };

    function el(tag, attrs, children) {
        const node = document.createElement(tag);
        Object.entries(attrs || {}).forEach(([k, v]) => {
            if (v === null || v === undefined || v === false) return;
            if (k === 'class') node.className = v;
            else if (k === 'text') node.textContent = v;
            else node.setAttribute(k, v === true ? '' : v);
        });
        (children || []).forEach(c => node.append(c));
        return node;
    }

    function escapeHtml(s) {
        return s.replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    }

    function pageUrl(page) {
        return new URL(page.path, BASE);
    }

    function samePath(a, b) {
        return a.pathname === b.pathname;
    }

    /* 세션이 끝나 nav.json 대신 로그인 화면이 오면 JSON 이 아니다. */
    async function fetchNav() {
        const res = await fetch(NAV_URL, { credentials: 'same-origin', headers: { Accept: 'application/json' } });
        const type = res.headers.get('content-type') || '';
        if (!res.ok || !type.includes('json')) throw new Error('nav.json unavailable');
        return res.json();
    }

    function flattenPages(nav) {
        const pages = [];
        nav.groups.forEach(group => group.pages.forEach(page => pages.push({ ...page, group: group.title })));
        return pages;
    }

    /* ── 머리줄 ── */

    function renderTop() {
        const toggle = el('button', { type: 'button', class: 'm-side-toggle', 'aria-expanded': 'false', text: '목차' });
        toggle.addEventListener('click', () => {
            const open = document.body.classList.toggle('m-side-open');
            toggle.setAttribute('aria-expanded', String(open));
        });

        const input = el('input', {
            type: 'search', placeholder: '예: 정의서 할당', 'aria-label': '매뉴얼 검색',
            autocomplete: 'off', spellcheck: 'false'
        });
        const panel = el('div', { class: 'm-search-panel', role: 'listbox', hidden: true });
        const search = el('div', { class: 'm-search' }, [input, panel]);

        const top = el('header', { class: 'm-top' }, [
            toggle,
            el('a', { class: 'm-brand', href: new URL('index.html', BASE).href, text: state.nav ? state.nav.title : '사용 매뉴얼' }),
            search,
            el('a', { class: 'm-app-link', href: APP_ROOT.href, text: '관리 화면으로' })
        ]);
        document.body.prepend(top);
        bindSearch(input, panel);
    }

    /* ── 사이드 목차 ── */

    function renderSide() {
        const side = el('nav', { class: 'm-side', 'aria-label': '매뉴얼 목차' });
        state.nav.groups.forEach(group => {
            const list = el('ul');
            group.pages.forEach(page => {
                const isCurrent = state.current && state.current.path === page.path;
                let link;
                if (page.status === 'planned') {
                    link = el('span', { class: 'm-side-link is-planned' }, [page.title, el('span', { class: 'm-soon', text: '준비 중' })]);
                } else {
                    link = el('a', {
                        class: 'm-side-link' + (isCurrent ? ' is-current' : ''),
                        href: pageUrl(page).href,
                        'aria-current': isCurrent ? 'page' : null,
                        text: page.title
                    });
                }
                list.append(el('li', {}, [link]));
            });
            side.append(el('div', { class: 'm-side-group' }, [el('div', { class: 'm-side-title', text: group.title }), list]));
        });
        document.querySelector('.m-top').after(side);
        const current = side.querySelector('.is-current');
        if (current) current.scrollIntoView({ block: 'nearest' });
    }

    /* ── 본문 앞뒤 ── */

    function renderCrumb(main) {
        if (!state.current) return;
        const h1 = main.querySelector('h1');
        if (!h1) return;
        h1.before(el('div', { class: 'm-crumb', text: state.current.group }));
    }

    function renderPager(main) {
        if (state.current) {
            const i = state.written.findIndex(p => p.path === state.current.path);
            const prev = state.written[i - 1];
            const next = state.written[i + 1];
            if (prev || next) {
                const pager = el('div', { class: 'm-pager' });
                if (prev) pager.append(pagerLink(prev, '이전', 'is-prev'));
                if (next) pager.append(pagerLink(next, '다음', 'is-next'));
                main.append(pager);
            }
        }
        const reviewed = document.querySelector('meta[name="manual-reviewed"]');
        if (reviewed) main.append(el('p', { class: 'm-reviewed', text: '마지막 확인 ' + reviewed.content }));
    }

    function pagerLink(page, label, cls) {
        return el('a', { class: cls, href: pageUrl(page).href }, [
            el('span', { class: 'm-pager-label', text: label }),
            el('span', { class: 'm-pager-title', text: page.title })
        ]);
    }

    /* 아직 쓰이지 않은 페이지로 가는 본문 링크는 비활성으로 보인다. 작성자는 최종 경로로 미리 링크해 둔다. */
    function markPlannedLinks(main) {
        const planned = state.pages.filter(p => p.status === 'planned').map(p => pageUrl(p).pathname);
        main.querySelectorAll('a[href]').forEach(a => {
            const url = new URL(a.getAttribute('href'), location.href);
            if (url.origin !== location.origin || !planned.includes(url.pathname)) return;
            a.classList.add('m-link-planned');
            a.setAttribute('aria-disabled', 'true');
            a.setAttribute('title', '준비 중인 페이지입니다');
            a.addEventListener('click', e => e.preventDefault());
        });
    }

    /* ── 페이지 내 목차 ── */

    function renderToc(main) {
        const headings = [...main.querySelectorAll('h2, h3')];
        if (headings.length < 2) return;
        const list = el('ul');
        const links = headings.map((h, i) => {
            if (!h.id) h.id = 'section-' + (i + 1);
            const a = el('a', { href: '#' + h.id, class: h.tagName === 'H3' ? 'is-sub' : null, text: h.textContent.trim() });
            list.append(el('li', {}, [a]));
            return a;
        });
        const toc = el('aside', { class: 'm-toc', 'aria-label': '이 페이지의 내용' }, [
            el('div', { class: 'm-toc-title', text: '이 페이지의 내용' }), list
        ]);
        main.after(toc);

        /* 읽는 중 표시. 화면 위쪽 30% 선을 지난 마지막 제목을 읽는 중으로 본다.
         * 페이지 끝에서는 마지막 절들의 제목이 그 선까지 올라오지 못하므로, 끝까지 내렸으면 마지막 제목을 고른다.
         * 목차나 주소의 #으로 이동하면 도착할 제목을 고정한다. 끝 근처의 절로 이동하면 선 판정과 어긋나기 때문이다.
         * 이동은 부드러운 스크롤(reset.css)이라 도착 시점을 알기 어려우므로, 사용자가 직접 스크롤을 시작할 때 고정을 푼다. */
        const byId = new Map(links.map(a => [a.getAttribute('href').slice(1), a]));
        const SCROLL_KEYS = new Set(['ArrowUp', 'ArrowDown', 'PageUp', 'PageDown', 'Home', 'End', ' ']);
        let pinned = null;
        const setActive = id => links.forEach(a => a.classList.toggle('is-active', a === byId.get(id)));
        const update = () => {
            if (pinned) return setActive(pinned);
            const doc = document.documentElement;
            if (window.innerHeight + window.scrollY >= doc.scrollHeight - 2) return setActive(headings[headings.length - 1].id);
            const line = window.innerHeight * 0.3;
            let current = headings[0];
            for (const h of headings) {
                if (h.getBoundingClientRect().top > line) break;
                current = h;
            }
            setActive(current.id);
        };
        const pinToHash = () => {
            const id = decodeURIComponent(location.hash.slice(1));
            if (!byId.has(id)) return;
            pinned = id;
            update();
        };
        const release = () => { if (pinned) { pinned = null; update(); } };
        let ticking = false;
        window.addEventListener('scroll', () => {
            if (ticking) return;
            ticking = true;
            requestAnimationFrame(() => { ticking = false; update(); });
        }, { passive: true });
        ['wheel', 'touchstart', 'mousedown'].forEach(type => window.addEventListener(type, release, { passive: true }));
        window.addEventListener('keydown', e => { if (SCROLL_KEYS.has(e.key)) release(); });
        window.addEventListener('resize', update);
        window.addEventListener('hashchange', pinToHash);
        // 이미 같은 #이 주소에 있으면 hashchange 가 오지 않으므로 목차를 누를 때도 고정한다
        links.forEach(a => a.addEventListener('click', () => setTimeout(pinToHash)));
        update();
        pinToHash();
    }

    /* ── 검색 ──
     * 처음 검색할 때 nav.json 의 작성된 페이지를 모두 가져와 브라우저에서 색인한다. 색인 파일을 따로 두지 않으므로
     * 페이지를 고쳐도 색인이 어긋나지 않는다. 절(h1, h2, h3) 단위로 나눠 결과가 그 절로 바로 이동한다.
     */

    async function buildIndex() {
        const parser = new DOMParser();
        const docs = await Promise.all(state.written.map(async page => {
            try {
                const res = await fetch(pageUrl(page), { credentials: 'same-origin' });
                if (!res.ok) return [];
                const doc = parser.parseFromString(await res.text(), 'text/html');
                return sectionsOf(page, doc.querySelector('main.m-main'));
            } catch (e) {
                return [];
            }
        }));
        return docs.flat();
    }

    function sectionsOf(page, main) {
        if (!main) return [];
        const sections = [];
        let cur = { page, heading: page.title, anchor: '', text: '' };
        main.childNodes.forEach(node => {
            if (node.nodeType === 1 && /^H[1-3]$/.test(node.tagName)) {
                if (node.tagName !== 'H1') {
                    sections.push(cur);
                    cur = { page, heading: node.textContent.trim(), anchor: node.id ? '#' + node.id : '', text: '' };
                }
                return;
            }
            cur.text += ' ' + (node.textContent || '');
        });
        sections.push(cur);
        return sections.map(s => ({ ...s, text: s.text.replace(/\s+/g, ' ').trim() }));
    }

    function ensureIndex() {
        if (!state.indexing) state.indexing = buildIndex().then(idx => (state.index = idx));
        return state.indexing;
    }

    function search(query) {
        const terms = query.toLowerCase().split(/\s+/).filter(Boolean);
        if (!terms.length) return [];
        return state.index
            .map(s => {
                const title = s.page.title.toLowerCase();
                const heading = s.heading.toLowerCase();
                const text = s.text.toLowerCase();
                let score = 0;
                for (const t of terms) {
                    const inTitle = title.includes(t), inHeading = heading.includes(t), inText = text.includes(t);
                    if (!inTitle && !inHeading && !inText) return null;
                    score += (inTitle ? 10 : 0) + (inHeading ? 5 : 0) + (inText ? 1 : 0);
                }
                return { s, score };
            })
            .filter(Boolean)
            .sort((a, b) => b.score - a.score)
            .slice(0, RESULT_LIMIT)
            .map(r => r.s);
    }

    function highlight(text, terms) {
        let html = escapeHtml(text);
        terms.forEach(t => {
            const re = new RegExp(escapeHtml(t).replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'gi');
            html = html.replace(re, m => '<mark>' + m + '</mark>');
        });
        return html;
    }

    function snippet(text, terms) {
        const lower = text.toLowerCase();
        const at = terms.map(t => lower.indexOf(t)).filter(i => i >= 0).sort((a, b) => a - b)[0];
        if (at === undefined) return text.slice(0, SNIPPET_RADIUS * 2);
        const start = Math.max(0, at - SNIPPET_RADIUS);
        const end = Math.min(text.length, at + SNIPPET_RADIUS);
        return (start > 0 ? '… ' : '') + text.slice(start, end) + (end < text.length ? ' …' : '');
    }

    function bindSearch(input, panel) {
        let active = -1;

        function close() {
            panel.hidden = true;
            active = -1;
        }

        function status(message) {
            panel.replaceChildren(el('div', { class: 'm-search-status', text: message }));
            panel.hidden = false;
        }

        async function run() {
            const query = input.value.trim();
            if (!query) return close();
            if (!state.index) {
                status('색인을 만드는 중입니다.');
                await ensureIndex();
                if (input.value.trim() !== query) return;
            }
            const terms = query.toLowerCase().split(/\s+/).filter(Boolean);
            const results = search(query);
            if (!results.length) return status('일치하는 내용이 없습니다.');
            panel.replaceChildren(...results.map(r => {
                const a = el('a', { class: 'm-search-result', role: 'option', href: pageUrl(r.page).href + r.anchor });
                a.innerHTML =
                    '<div class="m-search-result-title">' + highlight(r.heading, terms) + '</div>' +
                    '<div class="m-search-result-where">' + escapeHtml(r.page.group + ' › ' + r.page.title) + '</div>' +
                    '<div class="m-search-result-snippet">' + highlight(snippet(r.text, terms), terms) + '</div>';
                return a;
            }));
            panel.hidden = false;
            active = -1;
        }

        function move(delta) {
            const items = [...panel.querySelectorAll('.m-search-result')];
            if (!items.length) return;
            active = (active + delta + items.length) % items.length;
            items.forEach((it, i) => it.classList.toggle('is-active', i === active));
            items[active].scrollIntoView({ block: 'nearest' });
        }

        input.addEventListener('focus', () => { ensureIndex(); if (input.value.trim()) run(); });
        input.addEventListener('input', run);
        input.addEventListener('keydown', e => {
            if (e.key === 'ArrowDown') { e.preventDefault(); move(1); }
            else if (e.key === 'ArrowUp') { e.preventDefault(); move(-1); }
            else if (e.key === 'Enter') {
                const target = panel.querySelectorAll('.m-search-result')[Math.max(active, 0)];
                if (target) location.href = target.href;
            } else if (e.key === 'Escape') { close(); input.blur(); }
        });
        document.addEventListener('click', e => { if (!e.target.closest('.m-search')) close(); });
        document.addEventListener('keydown', e => {
            if (e.key !== '/' || e.target.closest('input, textarea, select, [contenteditable]')) return;
            e.preventDefault();
            input.focus();
        });
    }

    /* ── 시작 ── */

    async function init() {
        document.body.classList.add('m-body');
        const main = document.querySelector('main.m-main');
        try {
            state.nav = await fetchNav();
        } catch (e) {
            renderTop();
            const login = new URL('login', APP_ROOT).href;
            document.querySelector('.m-top').after(el('div', { class: 'm-load-error' }, [
                '목차를 불러오지 못했습니다. 로그인이 끝났을 수 있습니다. ',
                el('a', { href: login, text: '다시 로그인' })
            ]));
            return;
        }
        state.pages = flattenPages(state.nav);
        state.written = state.pages.filter(p => p.status !== 'planned');
        state.current = state.pages.find(p => samePath(pageUrl(p), location)) || null;

        renderTop();
        renderSide();
        if (main) {
            renderCrumb(main);
            markPlannedLinks(main);
            renderToc(main);
            renderPager(main);
        }
        if (location.hash) {
            const target = document.getElementById(decodeURIComponent(location.hash.slice(1)));
            if (target) target.scrollIntoView();
        }
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
    else init();
})();
