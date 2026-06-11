/*
 * BMC 비밀번호 변경 — 입력을 서버(/plan)로 보내 Redfish 적용 계획(경로/method/headers/body)을 받아 화면에 표시.
 * 실제 Redfish 호출 없음. textContent 렌더로 XSS 회피.
 */
(function () {
    'use strict';

    const form = document.getElementById('bmcForm');
    if (!form) return;
    const planUrl = form.dataset.planUrl;
    const formError = document.getElementById('bmcFormError');
    const result = document.getElementById('bmcResult');
    const requestLine = document.getElementById('bmcRequestLine');
    const requestBody = document.getElementById('bmcRequestBody');
    const note = document.getElementById('bmcNote');

    function showError(msg) {
        formError.textContent = msg;
        formError.hidden = false;
        result.hidden = true;
    }

    function clearError() {
        formError.hidden = true;
        formError.textContent = '';
    }

    form.addEventListener('submit', async e => {
        e.preventDefault();
        clearError();

        const pw = document.getElementById('bmcPassword').value;
        const pw2 = document.getElementById('bmcPasswordConfirm').value;
        if (pw !== pw2) {
            showError('새 비밀번호와 확인 값이 일치하지 않습니다.');
            return;
        }

        const payload = {
            accountId: document.getElementById('bmcAccountId').value.trim(),
            userName: document.getElementById('bmcUserName').value.trim(),
            newPassword: pw
        };

        try {
            const res = await fetch(planUrl, {
                method: 'POST',
                headers: {'Content-Type': 'application/json', 'Accept': 'application/json'},
                body: JSON.stringify(payload)
            });
            const body = await res.json().catch(() => ({}));
            if (res.ok) renderPlan(body);
            else renderApiError(body);
        } catch (err) {
            showError('서버와 통신하지 못했습니다: ' + String(err));
        }
    });

    function renderPlan(plan) {
        const ip = document.getElementById('bmcIp').value.trim() || '{BMC_IP}';
        const lines = [plan.method + ' https://' + ip + plan.target];
        Object.entries(plan.headers || {}).forEach(([k, v]) => lines.push(k + ': ' + v));
        requestLine.textContent = lines.join('\n');
        requestBody.textContent = JSON.stringify(plan.body, null, 2);
        note.textContent = (plan.userName ? '대상 계정: ' + plan.userName + ' · ' : '') + (plan.note || '');
        result.hidden = false;
    }

    function renderApiError(body) {
        let msg = body && body.message ? body.message : '요청을 처리할 수 없습니다.';
        if (body && body.fieldErrors && body.fieldErrors.length) {
            msg += ' — ' + body.fieldErrors.map(fe => fe.field + ': ' + fe.message).join(', ');
        }
        showError(msg);
    }
})();
