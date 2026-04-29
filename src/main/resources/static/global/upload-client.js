/* ============================================================
   Service Worker 정리용 스크립트
   ─────────────────────────────────────────────────────────────
   과거 버전에서 /upload-sw.js 를 등록해 SW 기반 background 업로드를
   시도했으나, Fetch API 의 upload progress 미지원 때문에 옵션 A (XHR +
   페이지 유지) 로 전환했다. 기존 사용자 브라우저에 남아있는 레거시 SW 를
   자동으로 unregister 해서 stale 등록이 영향을 주지 않도록 정리한다.

   이 파일 자체는 한동안 남겨둔 뒤 (모든 사용자 세션에서 정리가 끝나면)
   layout.html 의 로드 선언과 함께 제거 가능하다.
   ============================================================ */
(function () {
    if (!('serviceWorker' in navigator)) return;
    try {
        navigator.serviceWorker.getRegistrations().then(regs => {
            regs.forEach(reg => {
                const url = reg.active && reg.active.scriptURL ? reg.active.scriptURL : '';
                if (url.indexOf('upload-sw.js') !== -1) {
                    reg.unregister().then(() => {
                        // eslint-disable-next-line no-console
                        console.log('[upload-client] legacy upload-sw.js unregistered');
                    });
                }
            });
        }).catch(() => { /* no-op */ });
    } catch (_) { /* no-op */ }
})();
