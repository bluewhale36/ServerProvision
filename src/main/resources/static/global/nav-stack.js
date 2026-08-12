/* ============================================================
   화면 이력 스택 — '목록으로' 와 '돌아온 행 강조'
   ─────────────────────────────────────────────────────────
   목록에서 행을 눌러 상세로 들어가고, 상세에서 '목록으로' 눌러 되돌아온다.
   이 왕복이 여러 겹 겹칠 수 있다 — 서버 목록 → 그룹 상세 → 서버 상세처럼.

   앞선 구현은 이것을 URL 파라미터 둘(returnTo · returned)로 표현했다가 두 가지로 깨졌다.
     ① 깊이 2 를 표현할 수단이 없었다. 그룹 상세의 멤버 링크는 "그룹 상세로 돌아와라" 만 실었고
        그 그룹 상세가 어디서 왔는지는 실을 자리가 없어, 한 번 더 들어갔다 나오면 출신을 잊었다.
     ② 짚을 행의 키를 목록을 건너 옮겼다. 서버 상세에서 그룹 상세로 돌아올 때 실려 온 서버 id 를
        그룹 상세가 자기 back 링크에 재사용해, 그룹 목록에 서버 id 가 갔다 — 그 목록의 행 키는
        그룹 id 라 아무것도 맞지 않았다. <b>짚을 행은 목록마다 다른 좌표계를 갖는다.</b>

   그래서 둘을 갈랐다.
     · <b>어디로 돌아가나</b> — 이력 스택이 안다. 목록에서 상세로 들어갈 때 한 겹 쌓고,
       상세에서 나올 때 한 겹 걷는다. 겹이 몇이든 같은 규칙이다.
     · <b>무엇을 짚나</b> — 그 겹이 함께 들고 있다. 자기를 만든 목록의 좌표계 안에서만 쓰인다.

   URL 에는 아무것도 싣지 않는다. 주소가 깨끗해지고, 바깥에서 준 경로로 이동하는 표면도 사라진다.

   마크업 규약(딱 하나):
     · 목록의 각 행에 `data-nav-key="<그 행의 식별자>"`.
       그 행 안의 링크를 누르면 자동으로 한 겹 쌓인다 — 이름 링크든 그룹 배지든 구분하지 않는다.
     · 상세의 '목록으로' 에 `data-nav-back` + 기본 목록을 가리키는 href.
       스크립트가 없거나 스택이 비었으면(주소창 직접 진입 · 새로고침) 그 href 로 간다.

   ─────────────────────────────────────────────────────────
   전제와 한계 — 조회 화면이 바뀌면 여기도 손봐야 한다

   지금 이 구현은 <b>돌아간 목록의 DOM 안에 그 행이 있다</b>는 전제 위에 서 있다.
   되돌아갈 주소(`from`)에 질의 문자열을 통째로 담으므로 URL 에 실린 화면 상태
   (필터 · 펼침 등)는 그대로 복원되고, 접힌 묶음 안의 행은 펼쳐서 찾는다.
   현재 목록은 한 번에 전부 그리므로 이 전제가 성립한다.

   <b>페이징이 들어오면 성립하지 않는다.</b> 돌아간 첫 페이지에 그 행이 없으면
   querySelector 가 못 찾고 — 예외도 안내도 없이 — 강조가 조용히 사라진다.
   무한 스크롤이나 가상 스크롤(보이는 만큼만 DOM 에 두는 방식)도 같은 문제를 낳는다.

   그때는 이 파일을 고쳐야 한다. 방향은 둘 중 하나다.
     ① 쌓을 때 행의 위치까지 담는다 — 페이지 번호처럼 그 목록에서 행을 다시 찾을 수 있는 좌표.
        `from` 에 페이지 파라미터가 이미 실려 있다면 대체로 이것으로 충분하다.
     ② 목록 쪽이 "이 키가 몇 쪽에 있는가" 를 풀어 준다 — 서버가 그 행이 있는 쪽으로
        보내 주거나, 화면이 키로 조회해 해당 쪽을 연다.

   어느 쪽이든 <b>못 찾았을 때 조용히 넘어가지 않게</b> 하는 것이 핵심이다.
   지금은 못 찾으면 아무 일도 하지 않는데, 그것이 정상 동작인 이유는 "그 목록에 없는 행"이
   지금 구조에서는 생기지 않기 때문이다. 페이징이 들어오면 그 전제가 깨진다.
   ============================================================ */
(function () {
    'use strict';

    var STACK_KEY = 'spv.nav.stack';
    var PENDING_KEY = 'spv.nav.pending';
    var MAX_DEPTH = 10;          // 되돌아오지 못한 겹이 무한정 쌓이지 않게
    var HIGHLIGHT_MS = 1800;     // grouped-list.css 의 n-row-returned 애니메이션 길이와 맞춘다
    var SMOOTH_GRACE_MS = 700;   // 부드러운 이동이 끝날 만한 시간

    /* ── 저장소 ── */

    function read(key) {
        try {
            return JSON.parse(window.sessionStorage.getItem(key)) || null;
        } catch (e) {
            return null;
        }
    }

    function write(key, value) {
        try {
            if (value === null) window.sessionStorage.removeItem(key);
            else window.sessionStorage.setItem(key, JSON.stringify(value));
        } catch (e) { /* 저장소를 못 쓰면 기본 동작(서버가 렌더한 href)으로 떨어진다 */ }
    }

    function stack() {
        var s = read(STACK_KEY);
        return Array.isArray(s) ? s : [];
    }

    /** 우리가 만든 같은 출처 경로만 통과시킨다 — 저장소가 오염돼도 바깥으로 나가지 않게. */
    function isInternal(href) {
        return typeof href === 'string' && href.charAt(0) === '/' && href.charAt(1) !== '/';
    }

    function here() {
        return window.location.pathname + window.location.search;
    }

    /* ── 쌓기: 목록에서 상세로 ── */

    document.addEventListener('click', function (e) {
        var link = e.target.closest('a[href]');
        if (!link) return;
        var row = link.closest('[data-nav-key]');
        if (!row) return;

        var to = link.getAttribute('href');
        if (!isInternal(to)) return;

        // 어디서(from) 무엇을 눌러(key) 어디로(to) 갔는지. to 는 되돌아왔을 때
        // "이 겹이 정말 이 화면의 것인가" 를 대조하는 데 쓴다.
        var next = stack().concat([{from: here(), key: row.getAttribute('data-nav-key'), to: to}]);
        write(STACK_KEY, next.slice(-MAX_DEPTH));
    });

    /* ── 걷기: 상세에서 목록으로 ── */

    document.addEventListener('click', function (e) {
        var back = e.target.closest('[data-nav-back]');
        if (!back) return;

        var s = stack();
        var top = s[s.length - 1];

        // 맨 위 겹이 이 화면으로 들어온 그 겹일 때만 쓴다. 아니면 남의 겹이거나 오래된 것이므로
        // 서버가 렌더한 기본 목록으로 간다(그 경우 스택도 버린다 — 흐름이 끊긴 것이다).
        var mine = top && isInternal(top.from)
                && window.location.pathname === String(top.to).split('?')[0];
        if (!mine) {
            write(STACK_KEY, null);
            return;   // 기본 동작 — <a href> 가 그대로 열린다
        }

        e.preventDefault();
        write(STACK_KEY, s.slice(0, -1));
        write(PENDING_KEY, {path: top.from.split('?')[0], key: top.key});
        window.location.assign(top.from);
    });

    /* ── 도착: 짚을 행 강조 ── */

    function isInView(el) {
        var r = el.getBoundingClientRect();
        return r.top >= 0 && r.bottom <= (window.innerHeight || document.documentElement.clientHeight);
    }

    /**
     * 행을 화면 안으로 가져온다.
     *
     * 부드러운 이동은 장식이고 기능은 "행이 보이게 하는 것" 이다. 그런데 부드러운 이동이 무시되는
     * 환경(동작 줄이기 설정 등)에서는 scrollIntoView 가 예외도 대체 동작도 없이 조용히 아무 일도
     * 하지 않는다 — 실제로 검증 중 돌아온 행이 화면 밖에 있는데 화면은 최상단에 머물렀고 강조가
     * 보이지 않는 곳에서 켜졌다 꺼졌다. 그래서 시도한 뒤 끝날 만한 시간이 지나도 제자리면 즉시 이동한다.
     */
    function bringIntoView(row) {
        if (isInView(row)) return;
        row.scrollIntoView({block: 'center', behavior: 'smooth'});
        window.setTimeout(function () {
            if (!isInView(row)) row.scrollIntoView({block: 'center', behavior: 'instant'});
        }, SMOOTH_GRACE_MS);
    }

    function highlightPending() {
        var pending = read(PENDING_KEY);
        if (!pending) return;
        write(PENDING_KEY, null);   // 한 번만 — 새로고침마다 깜빡이면 소음이 된다

        // 다른 화면의 표식이면 쓰지 않는다. 목록마다 행 키의 좌표계가 다르기 때문이다.
        if (pending.path !== window.location.pathname) return;

        // 못 찾으면 아무 일도 하지 않는다. 지금 구조에서는 "그 목록에 없는 행" 이 생기지 않기 때문이다
        // — 페이징 · 무한 스크롤이 들어오면 이 전제가 깨진다(파일 머리말의 '전제와 한계' 참고).
        var row = document.querySelector('[data-nav-key="' + CSS.escape(pending.key) + '"]');
        if (!row) return;

        var collapsed = row.closest('details:not([open])');
        if (collapsed) collapsed.open = true;

        bringIntoView(row);
        row.classList.add('n-row-returned');
        window.setTimeout(function () { row.classList.remove('n-row-returned'); }, HIGHLIGHT_MS);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', highlightPending);
    } else {
        highlightPending();
    }
})();
