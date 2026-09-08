/* ============================================================
   ui-util.js — 화면 스크립트가 공유하는 작은 헬퍼 한 벌 (S17-4)
   ─────────────────────────────────────────────────────────────
   escapeHtml 7 · formatBytes 4 · cssEscape 2 · toast 2 · showBanner 5 가 파일마다 복붙돼 있었다.
   뜻은 같은데 구현이 조금씩 달라(null 을 "null" 로 찍는 escapeHtml, fallback 정규식이 다른 cssEscape)
   같은 값이 화면마다 다르게 보일 수 있었다. 여기 한 벌만 둔다.

   적재 순서 — 이 파일은 layout 의 head 에서 <b>defer 없이</b> 싣는다. 페이지 스크립트는 body 끝에서
   동기로 실행되는데, head 의 defer 스크립트는 그보다 뒤(문서 파싱 완료 후)에 실행된다(S17-2 CP5 F-3 이
   그 순서로 난 사고다). 동기로 실어야 body 끝 스크립트가 정의 시점에 window.UiUtil 을 볼 수 있다.
   ============================================================ */
(function (global) {
    'use strict';

    /** HTML 텍스트 노드 · 속성값에 끼울 문자열. null · undefined 는 빈 문자열이다. */
    function escapeHtml(value) {
        if (value == null) return '';
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    /** 바이트 수를 B · KB · MB · GB 로. 0 이하 · NaN 은 '0 B'. */
    function formatBytes(n) {
        if (!Number.isFinite(n) || n <= 0) return '0 B';
        if (n < 1024) return n.toFixed(0) + ' B';
        if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB';
        if (n < 1024 * 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + ' MB';
        return (n / 1024 / 1024 / 1024).toFixed(2) + ' GB';
    }

    /** 선택자 안에 넣을 식별자(필드 이름 등). CSS.escape 가 없는 환경만 직접 이스케이프한다. */
    function cssEscape(value) {
        if (typeof CSS !== 'undefined' && typeof CSS.escape === 'function') return CSS.escape(value);
        return String(value).replace(/[^a-zA-Z0-9_-]/g, ch => '\\' + ch);
    }

    /**
     * 배경 작업 알림 센터의 토스트로 한 줄 알린다. 두 번째 인자는 variant 문자열('info' · 'success' ·
     * 'error')이거나 옵션 객체({variant, duration})다 — 종전 두 래퍼의 시그니처를 모두 받는다.
     * 알림 센터가 없으면 조용히 넘어간다(alert 금지).
     */
    function toast(message, variantOrOpts) {
        if (typeof global.bgjobToast !== 'function') return;
        const opts = typeof variantOrOpts === 'string' ? {variant: variantOrOpts} : (variantOrOpts || {});
        global.bgjobToast(message, opts);
    }

    /**
     * 폼 배너(.n-form-banner 등)에 문장을 띄운다. lines 는 문자열 하나 또는 배열이고, 빈 값은 걸러
     * ' · ' 로 잇는다. 이을 것이 없으면 배너를 숨긴다. 배너 요소가 없으면 console 에만 남긴다.
     * opts.scroll 이 참이면 배너가 보이도록 문서 맨 위로 올린다(상세 화면의 삭제 실패 안내).
     */
    function showBanner(banner, lines, opts) {
        const text = (Array.isArray(lines) ? lines : [lines]).filter(Boolean).join(' · ');
        if (!banner) {
            if (text) console.warn('[UiUtil.banner]', text);
            return;
        }
        banner.textContent = text;
        banner.hidden = !text;
        if (text && opts && opts.scroll) global.scrollTo({top: 0, behavior: 'smooth'});
    }

    function hideBanner(banner) {
        if (banner) banner.hidden = true;
    }

    global.UiUtil = Object.freeze({escapeHtml, formatBytes, cssEscape, toast, showBanner, hideBanner});
})(window);
