// R21-1 — 1024 이하에서 사이드바 링크 묶음을 서랍으로 여닫는다. 마크업 계약: nav.n-nav 안의 button.n-nav-toggle[aria-controls] 와 그 id 의 .n-nav-menu.
// 닫는 길은 넷이다: 메뉴 버튼을 다시 누름, 어둡게 덮인 뒤 화면을 누름(.n-nav::before 라 대상이 nav 자신), Esc, 폭이 1024 를 넘음.
(function (root) {
    function bind(nav) {
        var toggle = nav.querySelector('.n-nav-toggle');
        var menu = toggle && nav.querySelector('#' + toggle.getAttribute('aria-controls'));
        if (!toggle || !menu || nav.dataset.drawerBound) return;
        nav.dataset.drawerBound = 'true';
        var doc = nav.ownerDocument;
        var wide = (doc.defaultView || window).matchMedia('(min-width: 1025px)');
        // 잠긴 메뉴(n-nav-locked)의 링크는 CSS 가 pointer-events: none 으로 막는다. 그 판정을 읽어 키보드 순서에서도 뺀다(R21-1 CP5 O6)
        Array.prototype.forEach.call(nav.querySelectorAll('a'), function (el) {
            if (getComputedStyle(el).pointerEvents === 'none') el.tabIndex = -1;
        });
        var note = menu.querySelector('.n-nav-locked-note');
        if (note) note.tabIndex = -1;

        function setOpen(open, returnFocus) {
            nav.classList.toggle('is-open', open);
            toggle.setAttribute('aria-expanded', String(open));
            toggle.textContent = open ? '닫기' : '메뉴';
            doc.documentElement.classList.toggle('n-nav-open', open);
            if (open) {
                // 잠긴 메뉴면 잠금 안내 문장에, 아니면 첫 링크에 포커스를 둔다. 서랍 아래쪽 항목에 포커스를 주면 브라우저가 서랍을 굴려
                // 맨 위 안내가 가려지고, 굴리지 않으면 포커스가 화면 밖에 있다(R21-1 CP5 D2, O7). 서랍은 늘 맨 위부터 보인다
                menu.scrollTop = 0;
                var first = note || menu.querySelector('a.n-nav-link:not([tabindex="-1"]), button.n-nav-link');
                if (first) first.focus({ preventScroll: true });
            } else if (returnFocus) {
                toggle.focus();
            }
        }

        // 열린 서랍은 덮개로 뒤 화면을 가리므로 Tab 이 덮개 뒤 입력칸으로 나가지 않게 머리 줄과 서랍 안에서 돈다(R21-1 CP5 O8)
        function keepFocusInside(e) {
            var items = Array.prototype.filter.call(nav.querySelectorAll('a, button, input, select, textarea'), function (el) {
                return el.tabIndex >= 0 && !el.disabled && el.getClientRects().length > 0;
            });
            if (!items.length) return;
            var head = items[0], tail = items[items.length - 1], at = doc.activeElement;
            if (e.shiftKey ? (at === head || !nav.contains(at)) : (at === tail || !nav.contains(at))) {
                e.preventDefault();
                (e.shiftKey ? tail : head).focus();
            }
        }

        toggle.addEventListener('click', function () { setOpen(!nav.classList.contains('is-open'), false); });
        nav.addEventListener('click', function (e) { if (e.target === nav && nav.classList.contains('is-open')) setOpen(false, true); });
        doc.addEventListener('keydown', function (e) {
            if (!nav.classList.contains('is-open')) return;
            if (e.key === 'Escape') setOpen(false, true);
            else if (e.key === 'Tab') keepFocusInside(e);
        });
        wide.addEventListener('change', function (e) { if (e.matches) setOpen(false, false); });
    }

    root.NavDrawer = { bind: bind };
    if (typeof document !== 'undefined') {
        document.addEventListener('DOMContentLoaded', function () {
            document.querySelectorAll('nav.n-nav').forEach(bind);
        });
    }
})(window);
