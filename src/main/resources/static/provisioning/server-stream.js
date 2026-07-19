// S7 — 게스트 서버 실시간 상태 스트림 (SSE 신호-재조회).
// 페이지가 [data-stream-scope] 를 선언하면 활성:
//   목록: data-stream-scope="list"
//   상세: data-stream-scope="detail" data-server-id="<uuid>"
// 신호(changed: 서버 id)를 받으면 같은 URL 을 다시 fetch 해 [data-live] 영역만 교체한다.
// payload 스키마 없음 — 상태 데이터의 SSOT 는 기존 SSR 조회 경로 하나다.
// 수정 폼(form)에 [data-live] 를 붙이지 않는 것이 곧 입력 보존 규약(plan Q5).
document.addEventListener('DOMContentLoaded', function () {
    const scopeEl = document.querySelector('[data-stream-scope]');
    if (!scopeEl || !window.EventSource) return;

    const scope = scopeEl.getAttribute('data-stream-scope');
    const serverId = scopeEl.getAttribute('data-server-id');
    const streamUrl = scopeEl.getAttribute('data-stream-url');
    if (!streamUrl) return;

    let refetchTimer = null;
    let refetching = false;
    let rolloverTimer = null;

    // 재연결은 EventSource 기본 동작에 위임 — 연결 상태는 콘솔로만 (운영자 UI 소음 방지).
    const source = new EventSource(streamUrl);
    source.addEventListener('changed', function (e) {
        // 구독 필터는 클라이언트(plan Q3) — 상세 페이지는 자기 서버의 신호에만 반응한다.
        if (scope === 'detail' && serverId && e.data !== serverId) return;
        // 연속 신호는 300ms 디바운스로 재조회 1회에 합친다.
        clearTimeout(refetchTimer);
        refetchTimer = setTimeout(refetch, 300);
    });
    source.onerror = function () {
        console.debug('[server-stream] 연결 끊김 — EventSource 자동 재연결 대기');
    };

    function refetch() {
        if (refetching) {
            // 재조회 진행 중 도착한 신호 — 한 번 더 예약해 마지막 변화가 유실되지 않게 한다.
            clearTimeout(refetchTimer);
            refetchTimer = setTimeout(refetch, 300);
            return;
        }
        refetching = true;
        fetch(location.href, { headers: { 'X-Requested-With': 'server-stream' } })
            .then(function (res) {
                if (!res.ok) throw new Error('HTTP ' + res.status);
                return res.text();
            })
            .then(function (html) {
                const next = new DOMParser().parseFromString(html, 'text/html');
                document.querySelectorAll('[data-live]').forEach(function (cur) {
                    const fresh = next.querySelector('[data-live="' + cur.getAttribute('data-live') + '"]');
                    if (!fresh) return;
                    cur.replaceWith(fresh);
                    fresh.classList.add('n-live-flash');
                    fresh.addEventListener('animationend', function () {
                        fresh.classList.remove('n-live-flash');
                    }, { once: true });
                });
            })
            .then(scheduleContactRollover)
            .catch(function (err) {
                // 일시 실패는 침묵 — 다음 신호나 수동 새로고침으로 회복한다(plan §7).
                console.debug('[server-stream] 재조회 실패:', err);
            })
            .finally(function () { refetching = false; });
    }

    // 연결 중 → 끊어짐 전이는 게스트 침묵 = 발행 이벤트가 없는 변화라 SSE 신호가 오지 않는다.
    // 서버가 계산해 심어둔 남은 초([data-contact-remaining])의 최솟값 시점에 1회 재조회를 예약한다.
    // 전이 후엔 속성이 사라져 타이머도 사라진다 — 주기 폴링이 아니다.
    function scheduleContactRollover() {
        clearTimeout(rolloverTimer);
        let min = Infinity;
        document.querySelectorAll('[data-contact-remaining]').forEach(function (el) {
            const v = parseInt(el.getAttribute('data-contact-remaining'), 10);
            if (!isNaN(v) && v >= 0) min = Math.min(min, v);
        });
        if (min === Infinity) return;
        rolloverTimer = setTimeout(refetch, (min + 2) * 1000);   // +2초 — 서버 판정이 확실히 넘어간 뒤
    }

    scheduleContactRollover();
});
