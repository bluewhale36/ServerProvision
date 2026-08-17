/* ============================================================
   그룹 상세 — 정의서 고르기 모달의 열기 · 고르기 (U3-5-c → U3-5-d 두 모드).
   ─────────────────────────────────────────────────────────
   이 파일은 고르는 일까지만 한다. 제출은 브라우저가 그대로 한다 — 폼에 data-native-submit 이 있어
   전역 가로채기를 타지 않고, 서버가 redirect + flash 로 결과를 알린다(DEC-E). 여기서 fetch 로 보내면
   그 flash 가 fetch 안에서 소비돼 화면에 도달하지 못한다.

   두 모드가 한 모달을 나눠 쓴다(U3-5-d DEC-B)
     · assign   — 지금 멤버들에게 붙인다. 붙는 대상이 0 이면 할 일이 없으므로 확정이 열리지 않는다.
     · standard — 그룹이 기억할 정의서를 정한다. 지금 붙는 서버가 0 이어도 정할 수 있다.
   고르는 동작 자체는 같으므로, 갈리는 값만 아래 표에 두고 함수는 공유한다. 모드가 늘어도
   select() · applyMode() 안에 분기가 늘지 않는다.

   좌측 목록의 클릭은 컨테이너 위임으로 듣는다 — 내용이 나중에 채워지므로 직접 바인딩하면 교체된
   행을 놓친다(U3-4 서버 넣기 · U3-5-b 단건 모달과 같은 이유).

   아무에게도 안 붙는 정의서도 클릭된다 — 눌러서 왜 그런지(멤버별 사유) 볼 수 있어야 한다.
   ============================================================ */
(function () {
    'use strict';

    const PROMPT = '왼쪽에서 정의서를 고르세요';

    const MODES = {
        assign: {
            key: 'assign',
            title: '정의서 골라 할당',
            message: '정의서를 고르면 이 그룹의 각 서버에 어떻게 적용되는지 먼저 보여 드립니다. '
                + '이미 정의서가 있는 서버는 갈아엎지 않고 건너뜁니다.',
            submitLabel: '선택한 정의서 할당',
            actionAttr: 'data-assign-action',
            /* 붙는 대상이 0 이면 확정을 열지 않는다 — 아무 일도 일어나지 않을 제출이다. */
            allowsEmptyTarget: false,
            /* '고르는 김에 표준으로도 두기'(OQ-3) 는 할당 모드에서만 뜻이 있다. */
            showStandardOption: true,
            status: (name, count, summary) => name + ' — ' + summary
        },
        standard: {
            key: 'standard',
            title: '표준 정의서 지정',
            message: '이 그룹이 기억할 정의서를 고릅니다. 지정해도 서버에 자동으로 붙지 않습니다 — '
                + '아직 적용받지 않은 서버가 있으면 그룹 상세에서 한 번에 붙일 수 있습니다.',
            submitLabel: '표준으로 지정',
            actionAttr: 'data-standard-action',
            /* 지금 붙는 서버가 없어도 표준은 정할 수 있다 — 빈 그룹에 정책부터 정해 두는 것이
               이 기능의 출발점이다(DEC-B). 그래서 좌측이 '0 대' 여도 고를 수 있어야 한다. */
            allowsEmptyTarget: true,
            showStandardOption: false,
            status: (name, count) => count > 0
                ? name + ' — 표준으로 지정합니다. 지정 뒤 ' + count + ' 대에 적용할 수 있습니다.'
                : name + ' — 표준으로 지정합니다. 지금 적용할 서버는 없습니다.'
        }
    };

    function wire() {
        const overlay = document.getElementById('groupDefinitionPicker');
        const openers = document.querySelectorAll('[data-picker-mode]');
        if (!overlay || openers.length === 0) return;

        const form = document.getElementById('groupDefinitionPickerForm');
        const title = document.getElementById('groupDefinitionPickerTitle');
        const message = document.getElementById('groupDefinitionPickerMessage');
        const body = document.getElementById('groupDefinitionPickerBody');
        const status = document.getElementById('groupDefinitionPickerStatus');
        const submit = document.getElementById('groupDefinitionPickerSubmit');
        const choice = document.getElementById('groupDefinitionPickerChoice');
        const standardOpt = document.getElementById('groupDefinitionPickerStandardOpt');
        const alsoStandard = document.getElementById('groupDefinitionPickerAlsoStandard');

        let mode = MODES.assign;
        let loaded = false;

        /* 모드가 바꾸는 것은 '고른 뒤에 무슨 일이 일어나는가' 뿐이다 — 제목 · 안내문 · 제출 대상 ·
           버튼 문구, 그리고 잠긴 모양을 보일지. 목록과 미리보기는 서버가 한 벌만 내려주고 그대로 쓴다. */
        function applyMode(next) {
            mode = next;
            title.textContent = next.title;
            message.textContent = next.message;
            submit.textContent = next.submitLabel;
            form.setAttribute('action', form.getAttribute(next.actionAttr));
            standardOpt.hidden = !next.showStandardOption;
            alsoStandard.checked = false;
            body.classList.toggle('n-picker-standard-mode', next.key === 'standard');
        }

        /**
         * '고르는 김에 표준으로도 두기' 를 열고 닫는다 (CP5 F1).
         *
         * 붙을 대상이 0 대면 확정 버튼이 열리지 않으므로, 체크박스만 켜지게 두면 <b>사용자가 의사를
         * 표현했는데 보낼 수단이 없는 상태</b>가 된다. 켤 수 없게 하되 왜 그런지 tooltip 으로 말한다 —
         * 거절하는 것으로 끝내지 않고 다음 행동(위 절의 [표준 정하기])으로 유도한다.
         */
        function setStandardOption(enabled, reason) {
            alsoStandard.disabled = !enabled;
            if (!enabled) alsoStandard.checked = false;
            standardOpt.classList.toggle('n-picker-standard-opt-blocked', !enabled);
            if (reason) standardOpt.setAttribute('title', reason);
            else standardOpt.removeAttribute('title');
        }

        /* 다시 열 때 지난 선택이 남아 있으면 눈에는 아무것도 안 골라 보이는데 버튼만 열려 있게 된다. */
        function clearSelection() {
            body.querySelectorAll('.n-miller-selected')
                .forEach((el) => el.classList.remove('n-miller-selected'));
            body.querySelectorAll('.n-miller-detail-panel.active')
                .forEach((el) => el.classList.remove('active'));
            const empty = body.querySelector('.n-miller-empty');
            if (empty) empty.classList.remove('hidden');
            choice.value = '';
            status.textContent = PROMPT;
            status.classList.remove('n-picker-count-blocked');
            submit.disabled = true;
            submit.removeAttribute('title');
            // 아직 고른 것이 없으면 '이 정의서를' 이 가리킬 대상도 없다 — 확정 버튼과 같이 잠가 둔다
            setStandardOption(false, '먼저 왼쪽에서 정의서를 고르세요.');
        }

        function select(item) {
            const id = item.getAttribute('data-definition-id');
            const name = item.getAttribute('data-definition-name');
            const count = parseInt(item.getAttribute('data-assign-count') || '0', 10);
            const summary = item.getAttribute('data-summary');

            body.querySelectorAll('.n-miller-selected')
                .forEach((el) => el.classList.remove('n-miller-selected'));
            item.classList.add('n-miller-selected');

            body.querySelectorAll('.n-miller-detail-panel.active')
                .forEach((el) => el.classList.remove('active'));
            const panel = document.getElementById('group-definition-panel-' + id);
            if (panel) panel.classList.add('active');
            const empty = body.querySelector('.n-miller-empty');
            if (empty) empty.classList.add('hidden');

            /* 패널을 전부 미리 그려 두고 표시만 바꾸므로 스크롤 컨테이너가 하나다 — 아무 조치도 하지
               않으면 앞서 아래로 내려둔 위치가 그대로 남아, 새로 고른 정의서의 요약과 멤버 표(이 화면의
               요지)가 화면 밖에서 시작한다. 정의서를 바꾸는 것은 새로 읽기 시작하는 일이므로 위로 되돌린다.
               CP5 실측 — 전환 전후 scrollTop 이 292 로 같았다. */
            const column = body.querySelector('.n-miller-col-detail');
            if (column) column.scrollTop = 0;

            /* 붙는 대상이 0 이어도 선택 표시는 남긴다 — 어느 것을 보고 있는지가 사라지면 멤버별 사유를
               읽는 동안 맥락을 잃는다. 할당 모드에서만 값을 싣지 않아 제출할 수 없게 한다.
               상태줄 문구는 서버가 만든 요약 그대로다(화면과 서버가 같은 문장). */
            if (count === 0 && !mode.allowsEmptyTarget) {
                choice.value = '';
                status.textContent = summary;
                status.classList.add('n-picker-count-blocked');
                submit.disabled = true;
                submit.setAttribute('title', summary);
                /* 안내가 버튼 이름을 가리키지 않는 이유(CP5 재검증 N1) : 그 버튼의 문구는 표준이
                   있으면 [표준 바꾸기], 없으면 [표준 정하기] 로 바뀐다. 이 tooltip 이 뜨는 상황은
                   대개 표준이 이미 있는 그룹이라, 이름을 박아 두면 화면에 없는 버튼을 찾게 된다.
                   상태와 무관하게 고정인 것 — 절 제목 — 을 가리킨다. */
                setStandardOption(false,
                    '지금 붙일 서버가 없어 함께 지정할 수 없습니다 — 표준만 정하려면 위 \'표준 세팅 정의서\' 절에서 지정하세요.');
                return;
            }
            choice.value = id;
            status.textContent = mode.status(name, count, summary);
            status.classList.remove('n-picker-count-blocked');
            submit.disabled = false;
            submit.removeAttribute('title');
            setStandardOption(true, null);
        }

        function close() {
            overlay.hidden = true;
            document.removeEventListener('keydown', onKeydown);
        }

        function onKeydown(e) {
            if (e.key === 'Escape') close();
        }

        /* 내용은 한 번만 받는다 — 두 모드가 같은 목록과 같은 미리보기를 쓰므로 모드를 바꿔 열어도
           다시 받을 이유가 없다. 모드는 화면 표기만 바꾼다. */
        async function load(url) {
            if (loaded) return;
            try {
                const resp = await fetch(url, {headers: {'Accept': 'text/html'}, credentials: 'same-origin'});
                body.innerHTML = resp.ok
                    ? await resp.text()
                    : '<p class="n-detail-empty">정의서를 불러오지 못했습니다. 다시 시도해주세요.</p>';
                loaded = resp.ok;
            } catch (e) {
                body.innerHTML = '<p class="n-detail-empty">서버와 통신할 수 없습니다.</p>';
            }
        }

        openers.forEach((opener) => opener.addEventListener('click', async function () {
            applyMode(MODES[opener.getAttribute('data-picker-mode')]);
            overlay.hidden = false;
            document.addEventListener('keydown', onKeydown);
            await load(opener.getAttribute('data-fetch-url'));
            clearSelection();
        }));

        body.addEventListener('click', function (e) {
            const item = e.target.closest('.n-miller-item');
            if (item) select(item);
        });
        overlay.addEventListener('click', function (e) {
            if (e.target.closest('[data-picker-close]')) close();
        });
    }

    document.addEventListener('DOMContentLoaded', wire);
})();
