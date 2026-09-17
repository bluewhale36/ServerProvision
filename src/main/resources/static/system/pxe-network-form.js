/* R16 — PXE 네트워크 구성 폼의 모드 토글.
 * DHCP 모드 라디오가 바뀌면 주소 배정 필드 그룹(fieldset[data-mode-group]) 을 proxyDHCP 에서 disabled 하고 안내를 켠다.
 * 판정은 서버 DhcpMode.requiresAddressing() 과 같다 — AUTHORITATIVE 만 주소 배정 필드를 쓴다(UI 1차 차단 · 서버 가드 공유).
 * disabled 된 fieldset 의 입력은 제출되지 않으므로 서버는 그 필드를 null 로 받는다(값이 와도 버리는 것은 서버 규칙). */
(function () {
    const form = document.getElementById('pxeNetworkConfigForm');
    if (!form) return;
    const radios = form.querySelectorAll('input[name="dhcpMode"]');
    const group = form.querySelector('[data-mode-group]');
    const hint = form.querySelector('[data-mode-hint]');
    if (!radios.length || !group) return;

    function selectedMode() {
        for (const r of radios) if (r.checked) return r.value;
        return null;
    }

    function apply() {
        const requiresAddressing = selectedMode() === group.dataset.modeGroup;
        group.disabled = !requiresAddressing;
        if (hint) hint.hidden = requiresAddressing;
        group.querySelectorAll('label[data-required="true"]').forEach(function (label) {
            label.classList.toggle('strong', requiresAddressing);
        });
    }

    radios.forEach(function (r) { r.addEventListener('change', apply); });
    apply();
})();
