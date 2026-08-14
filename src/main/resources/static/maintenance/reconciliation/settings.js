/*
  점검 운영 설정 — settings.js (MK4-4-4)
  ─────────────────────────────────────
  하는 일 셋. 셋 다 "저장 버튼이 화면 맨 아래에 있고 페이지가 두 화면을 넘는다" 는 이 화면의
  형태에서 나온다 — 항목을 네 묶음으로 펼치면서 생긴 조건이다.

  ① 이중 제출을 막는다.
     이 폼은 data-native-submit 이라 전역 fetch 경로(form-submit.js)를 타지 않는다 — 검증에
     실패하면 서버가 폼을 다시 렌더해야 하기 때문이다. 그래서 전역 가드의 보호도 받지 못했고,
     응답이 오기 전에 버튼을 다시 누르면 요청이 두 번 나갔다(MK4-3-2 CP5 결함 ③).
     저장 자체는 멱등이라 두 번 저장돼도 값은 같다. 그런데도 막는 이유는 사용자 쪽이다 —
     눌렀는데 아무 반응이 없으면 다시 누르게 되고, 그때 화면은 무엇이 진행 중인지 말하지 않는다.

  ② 거절된 필드로 데려간다.
     브라우저 기본 검증(required · min · max)에 걸리면 submit 이 발생하지 않는다. 그런데 문제
     필드가 화면 밖에 있으면 브라우저 안내 말풍선도 화면 밖에서 뜬다 — 사용자 눈에는 저장을
     눌렀는데 아무 일도 안 일어난 것으로 보인다(MK4-4-4 CP5 결함 ①. 최하단에서 누르면 문제
     필드가 뷰포트 위 853px 바깥이었다). 서버가 거절해 다시 렌더된 경우도 같다 — 배너는
     맨 위에 뜨는데 해당 필드는 최대 두 화면 아래에 있다(CP5 C1, 실측 1874px).
     두 경우 모두 같은 처리로 해소한다 : 첫 번째 문제 지점으로 스크롤한다.

  ③ 차단기 상태를 즉시 반영하고, 바뀐 채로 저장하려 하면 무슨 일이 벌어지는지 알린다.
     시스템 해결을 끄면 자동 처리 대상 선택은 지금 아무 효과가 없다. 저장 후에는 서버가 렌더로
     알려 주지만, 끄는 순간에는 아무 변화가 없어 그 사실이 전해지지 않았다(CP5 C4).
     확인은 <b>저장할 때</b> 받는다 — 토글은 의사 표시일 뿐이고 실제로 바뀌는 것은 저장 시점이라,
     그 전에 물으면 되돌릴 수 있는 조작에 확인을 요구하는 셈이 된다.

  ①~③ 은 모두 submit 한 리스너 안에 순서대로 놓인다. 나누면 preventDefault 가 같은 요소의 다른
  리스너를 멈추지 않아, 확인 창을 띄우려고 막아도 버튼 잠금이 그대로 도는 사고가 난다.
*/
(function () {
    'use strict';

    // 화면 밖 요소를 데려올 때의 여유. 헤더가 상단을 가리므로 그만큼 더 올린다.
    const SCROLL_MARGIN = 120;

    function scrollTo(el) {
        if (!el) return;
        const top = el.getBoundingClientRect().top + window.scrollY - SCROLL_MARGIN;
        window.scrollTo({top: Math.max(top, 0), behavior: 'smooth'});
    }

    document.addEventListener('DOMContentLoaded', () => {
        const form = document.getElementById('reconciliationSettingsForm');
        if (!form) return;

        // ── ② 서버가 거절해 다시 렌더된 경우 : 첫 필드 오류로 데려간다 ──
        // 배너는 맨 위에 있어 "무엇이" 는 즉시 읽히지만 "어디" 는 스크롤해야 닿는다.
        const firstError = form.querySelector('.n-error');
        if (firstError) scrollTo(firstError);

        // ── ② 브라우저 기본 검증에 걸린 경우 : 그 필드로 데려간다 ──
        // invalid 는 버블링하지 않으므로 캡처 단계에서 받는다. 여러 필드가 동시에 걸리면
        // 브라우저가 순서대로 발화하므로 첫 발화만 쓰고 나머지는 흘린다.
        let scrolledThisAttempt = false;
        form.addEventListener('invalid', (ev) => {
            if (scrolledThisAttempt) return;
            scrolledThisAttempt = true;
            scrollTo(ev.target);
            // 같은 제출 시도 안에서만 한 번이다. 다음 시도에는 다시 열어 둔다.
            setTimeout(() => { scrolledThisAttempt = false; }, 0);
        }, true);

        // ── ③ 차단기 ↔ 자동 처리 묶음 연동 ──
        const breaker = form.querySelector('#resolutionEnabled, [name="resolutionEnabled"]');
        const autoGroup = form.querySelector('[data-auto-apply-group]');
        const inertNote = form.querySelector('[data-auto-apply-inert-note]');
        // 저장된 상태. 지금 화면 값과 다를 때만 확인을 받는다 — 껐다 다시 켜서 원래대로 돌려놓은
        // 경우에는 바뀐 것이 없으므로 물을 이유도 없다.
        const savedOn = breaker ? breaker.checked : null;
        const kindBoxes = autoGroup
                ? autoGroup.querySelectorAll('.n-option-row input[type="checkbox"]')
                : [];

        if (breaker && autoGroup) {
            const sync = () => {
                const on = breaker.checked;
                autoGroup.classList.toggle('n-group-inert', !on);
                if (inertNote) inertNote.classList.toggle('n-hidden', on);
                // 차단기가 꺼져 있으면 고를 수 없게 한다. 값은 지우지 않는다 — 다시 켰을 때 무엇을
                // 맡길지는 미리 정해 둘 수 있어야 하고, 그것은 막을 이유가 없는 조작이다.
                kindBoxes.forEach(b => { b.disabled = !on; });
            };
            breaker.addEventListener('change', sync);
            sync();
        }

        /*
          차단기 전환 확인 문구.

          이 설정이 확인을 받을 자격은 <b>되돌릴 수 없어서가 아니라 조용해서</b> 생긴다. 값 하나가
          수동 [해결] 과 점검 중 자동 처리를 동시에 멈추는데, 화면에는 체크가 풀리는 것 말고 아무
          일도 일어나지 않는다. 효과는 며칠 뒤 "왜 아무것도 처리가 안 되지" 하는 순간에 나타난다.
          그래서 목적은 저지가 아니라 <b>고지</b>다 — 지금 무슨 결과에 서명하는지를 읽히게 한다.

          묻는 시점은 토글이 아니라 <b>저장</b>이다. 토글은 의사 표시일 뿐이고 실제로 바뀌는 것은
          저장할 때이므로, 그 전에 물으면 되돌릴 수 있는 조작에 확인을 요구하는 셈이 된다.

          두 방향의 위험이 다르므로 문구를 가른다. 끄는 쪽은 "쌓인다", 켜는 쪽은 "그동안 쌓인 것이
          한꺼번에 움직인다" 이다.
        */
        const COPY = {
            off: {
                title: '시스템 해결을 끕니다',
                message:
                    '외부 작업으로 파일이 대량으로 바뀌는 동안, 또는 드리프트의 원인을 조사하는 ' +
                    '동안처럼 시스템이 손대지 않기를 바랄 때 끕니다.\n\n' +
                    '끄면 점검은 계속 돌아 드리프트를 찾아내지만 아무것도 고치지 않습니다. ' +
                    '수동 [해결] 도 함께 막히므로, 급한 자원 하나만 처리하려던 조작도 거절됩니다. ' +
                    '그동안 발견된 것은 계속 쌓이며, 다시 켤 때까지 방치 기간이 늘어납니다.',
                confirmLabel: '끄고 저장',
                confirmClass: 'n-btn-outline-danger'
            },
            on: {
                title: '시스템 해결을 켭니다',
                message:
                    '조사나 대량 작업이 끝나 시스템이 다시 손대도 될 때 켭니다.\n\n' +
                    '켜면 수동 [해결] 이 다시 열리고, 다음 점검부터 자동 처리 대상으로 고른 종류가 ' +
                    '사람 확인 없이 처리됩니다. 꺼 둔 동안 쌓인 드리프트가 있다면 그 점검에서 ' +
                    '한꺼번에 처리될 수 있으니, 자동 처리 대상 목록을 먼저 확인하세요.',
                confirmLabel: '켜고 저장',
                confirmClass: 'n-btn-primary'
            }
        };

        /*
          제출 처리는 <b>한 리스너로 모은다</b>. 나누면 순서를 보장할 수 없기 때문이다 —
          preventDefault 는 같은 요소의 다른 리스너를 멈추지 않으므로, 확인 창을 띄우려고 막아도
          뒤에 등록된 버튼 잠금이 그대로 돌아 <b>취소했는데 버튼이 잠긴 채 남는</b> 상태가 된다.
        */
        const submitBtn = form.querySelector('button[type="submit"]');
        let confirmed = false;

        form.addEventListener('submit', (ev) => {
            // ⓐ 차단기가 바뀌었으면 먼저 묻는다.
            if (!confirmed && breaker && savedOn !== null
                    && breaker.checked !== savedOn && window.ConfirmModal) {
                ev.preventDefault();
                const copy = breaker.checked ? COPY.on : COPY.off;
                window.ConfirmModal.open('resolutionToggle', {
                    title: copy.title,
                    message: copy.message,
                    confirmLabel: copy.confirmLabel,
                    confirmClass: copy.confirmClass,
                    // 확인하면 같은 경로로 다시 태운다. 브라우저 기본 검증도 다시 도므로
                    // 다른 칸이 잘못돼 있으면 그쪽이 정상적으로 막는다.
                    onConfirm: () => { confirmed = true; form.requestSubmit(); }
                });
                return;
            }

            /*
              ⓑ disabled 인 입력은 <b>제출에서 통째로 빠진다</b>. 그대로 두면 차단기를 끄고 저장하는
              순간 자동 처리 대상이 빈 값으로 덮여, 다시 켰을 때 고르지도 않은 "아무것도 안 맡김"
              상태가 된다. 제출 직전에 되살려 화면에 보이던 선택이 그대로 실려 가게 한다
              (submit 은 폼 데이터를 모으기 전에 발화하므로 여기서 푸는 것으로 충분하다).
            */
            kindBoxes.forEach(b => { b.disabled = false; });

            // ⓒ 이중 제출. 여기 도달했다는 것은 요청이 실제로 나간다는 뜻이다 — 기본 검증에
            // 걸리면 submit 이 아예 발생하지 않으므로 값이 틀려 막힌 사용자가 갇히지 않는다.
            if (submitBtn) {
                submitBtn.disabled = true;
                submitBtn.textContent = '저장 중…';
            }
        });
    });
})();
