/*
  자원 경로 점검 페이지 — list.js
  ─────────────────────────────────────
  Miller 컬럼 클릭 동작은 os-list.js 가 처리한다 (data-os-key / data-os-id 일반화).
  본 스크립트는 reconciliation 페이지 특화 동작:
    · 수동 스캔 / Deep 스캔 / 마커 서명 재발급 버튼 → JS fetch 트리거
    · R9-1 — bgjob:completed / bgjob:failed 구독으로 완료를 화면에 반영:
        자기 트리거 스캔   → sessionStorage 토스트 + 자동 reload (위치는 selectKey alias 로 보존)
        외부(주기) 스캔    → 배너 안내만 (열람 중 화면 리셋 방지)
        재발급             → reload 없이 결과 토스트. 실패>0 이면 서버가 후속 점검을 자동 시작(R9-6)
                         — 그 완료는 외부 스캔과 동일하게 '새 보고서 도착' 배너가 안내
    · R9-3 — 확인 UI 정합:
        Deep 스캔/재발급 → 정적 generic modal (reconConfirm — 페이지 액션이라 자원 시그니처 무관)
        드리프트 적용/보고 닫기 → lazy modal (DRIFT_APPLY / DRIFT_DISMISS) + data-async-submit
        성공 = 토스트+reload, 거절 = 전역 AsyncSubmitResult(ErrorModal) — 전체 페이지 이탈 없음
      바인딩은 본 파일(페이지 로컬) — 전역 confirm-modals 스크립트 목록 무변경.
*/
(function () {
    'use strict';

    // reload 너머로 완료 토스트를 전달하는 키. 읽는 즉시 삭제 — 재방문 시 낡은 토스트 재노출 방지.
    const TOAST_KEY = 'reconciliation:pendingToast';

    // 이 페이지에서 트리거한 jobId → 트리거 버튼. 완료/실패 이벤트에서 자기 작업 식별 + 버튼 원복.
    const selfJobs = new Map();

    function toast(message, opts) {
        if (typeof window.bgjobToast === 'function') window.bgjobToast(message, opts || {});
    }

    function restore(btn) {
        if (!btn) return;
        btn.disabled = false;
        if (btn.dataset.originalLabel) btn.textContent = btn.dataset.originalLabel;
    }

    // R9-3 — 페이지 액션 확인. ConfirmModal 부재(스크립트 로드 실패) 시에만 native fallback.
    function confirmThen(opts, proceed) {
        if (window.ConfirmModal) {
            ConfirmModal.open('reconConfirm', {
                title: opts.title,
                message: opts.message,
                confirmLabel: opts.confirmLabel,
                confirmClass: opts.confirmClass,
                onConfirm: proceed
            });
            return;
        }
        if (window.confirm(opts.message)) proceed();
    }

    function requestStart(btn) {
        const url = btn.dataset.scanUrl;
        if (!url) return;
        const isReissue = btn.id === 'reissueBtn';

        btn.disabled = true;
        btn.dataset.originalLabel = btn.textContent;
        btn.textContent = '시작 중…';

        fetch(url, {
            method: 'POST',
            headers: {'Accept': 'application/json'}
        }).then(res => {
            if (res.ok) return res.json();
            // 409 동시 실행 / 5xx 에러
            return res.text().then(txt => {
                throw new Error('스캔 시작 실패 (HTTP ' + res.status + ') : ' + txt);
            });
        }).then(data => {
            // 이벤트 detail 의 식별자 필드는 id — 트리거 응답(jobId)과 이름이 다름에 주의.
            selfJobs.set(data.jobId, btn);
            btn.textContent = isReissue ? '재서명 중…' : '스캔 중…';
            toast(isReissue ? '마커 서명 재발급을 시작했습니다.' : '자원 점검을 시작했습니다.');
            // 버튼 원복은 완료/실패 이벤트 수신 시 — 실제 완료와 무관한 고정 타이머는 폐기(R9-1).
        }).catch(err => {
            ErrorModal.show({message: err.message || '스캔 시작 실패', status: 0});
            restore(btn);
        });
    }

    function trigger(btn) {
        if (btn.id === 'scanDeepBtn') {
            confirmThen({
                title: '정밀 점검',
                message: '모든 자원의 파일 내용 해시를 다시 계산합니다. 용량이 큰 ISO 는 수십 초에서 수 분이 걸릴 수 있어요. 계속할까요?',
                confirmLabel: '시작'
            }, () => requestStart(btn));
            return;
        }
        if (btn.id === 'reissueBtn') {
            confirmThen({
                title: '마커 서명 재발급',
                message: '서명 키(secret) 회전 직후에만 실행하세요. 모든 자원의 마커 서명이 현재 키로 다시 계산됩니다. '
                    + '파일 내용 해시는 유지되어, 변조된 자원은 이후 정밀 점검에서 그대로 감지됩니다. 계속할까요?',
                confirmLabel: '재발급',
                confirmClass: 'n-btn-outline-danger'
            }, () => requestStart(btn));
            return;
        }
        requestStart(btn);
    }

    function showNewReportBanner() {
        const banner = document.getElementById('newReportBanner');
        if (banner) banner.hidden = false;
    }

    document.addEventListener('bgjob:completed', ev => {
        const d = ev.detail || {};
        if (d.type === 'PATH_RECONCILIATION') {
            if (selfJobs.has(d.id)) {
                const count = d.metadata ? d.metadata.driftCount : null;
                sessionStorage.setItem(
                    TOAST_KEY,
                    count != null ? ('점검 완료 — 드리프트 ' + count + '건') : '점검 완료 — 보고서가 갱신되었습니다.'
                );
                window.location.reload();
            } else {
                showNewReportBanner();
            }
            return;
        }
        if (d.type === 'HASH_ACCEPT' && selfJobs.has(d.id)) {
            const m = d.metadata || {};
            sessionStorage.setItem(TOAST_KEY,
                '수용 완료 — ' + (m.acceptedResource || '자원') + ' 의 등록 지문을 현재 내용으로 갱신했습니다.');
            window.location.reload();
            return;
        }
        if (d.type === 'MARKER_REISSUE' && selfJobs.has(d.id)) {
            // 재발급은 DriftReport 를 만들지 않으므로 reload 무의미 — 결과(부분 실패 포함)만 토스트.
            const m = d.metadata || {};
            const failed = parseInt(m.reissueFailed || '0', 10);
            const msg = '마커 재서명 완료 — 성공 ' + (m.reissueSucceeded != null ? m.reissueSucceeded : '?')
                + '건, 실패 ' + (m.reissueFailed != null ? m.reissueFailed : '?') + '건'
                + (failed > 0 ? ' — 실패 자원 확인을 위한 점검이 자동으로 이어집니다' : '');
            toast(msg, failed > 0 ? {variant: 'error', duration: 8000} : {});
            restore(selfJobs.get(d.id));
            selfJobs.delete(d.id);
        }
    });

    document.addEventListener('bgjob:failed', ev => {
        const d = ev.detail || {};
        if (!selfJobs.has(d.id)) return;
        const prefix = ({MARKER_REISSUE: '마커 재서명 실패', HASH_ACCEPT: '내용 수용 실패'})[d.type] || '점검 실패';
        toast(prefix + (d.errorMessage ? ' : ' + d.errorMessage : ''), {variant: 'error', duration: 8000});
        restore(selfJobs.get(d.id));
        selfJobs.delete(d.id);
    });

    // R9-3 — 드리프트 폼용 lazy modal URL. resourceType/Id 는 endpoint 시그니처 충족용 (fragment 는 자원 lookup 없음).
    function driftModalUrl(type, form) {
        return '/ui/confirm-modal/' + type
            + '?resourceType=' + encodeURIComponent(form.getAttribute('data-resource-type') || 'OS_ISO')
            + '&resourceId=' + encodeURIComponent(form.getAttribute('data-resource-id') || '0');
    }

    function bindDriftForm(markerAttr, modalType, template, fallbackMessage) {
        ConfirmModal.bindFormSubmit(markerAttr, form => {
            const message = ConfirmModal.composeMessage(form, template, fallbackMessage);
            ConfirmModal.openLazy(driftModalUrl(modalType, form), {
                afterInject: ({messageEl}) => {
                    if (messageEl) messageEl.textContent = message;
                    return null;
                },
                onConfirm: () => ConfirmModal.approveAndSubmit(form)
            });
        });
    }

    // S6-3-3 — [다시 점검] : 확인 액션이라 modal 없이 즉시 실행. 해소면 토스트+reload(카드 소멸 반영),
    // 잔존이면 카드 불변 + 안내 토스트만 (재분류는 다음 전체 점검의 몫).
    function bindRecheckButtons() {
        document.querySelectorAll('[data-recheck-url]').forEach(btn => {
            btn.addEventListener('click', () => {
                btn.disabled = true;
                fetch(btn.dataset.recheckUrl, {method: 'POST', headers: {'Accept': 'application/json'}})
                    .then(res => {
                        if (!res.ok) return res.text().then(txt => { throw new Error('다시 점검 실패 (HTTP ' + res.status + ')'); });
                        return res.json();
                    })
                    .then(data => {
                        if (data.resolved) {
                            sessionStorage.setItem(TOAST_KEY, '해소 확인 — 카드를 정리했습니다.');
                            window.location.reload();
                        } else {
                            toast('아직 해결되지 않았습니다 — 안내된 조치 후 다시 시도하세요.', {duration: 6000});
                            btn.disabled = false;
                        }
                    })
                    .catch(err => {
                        ErrorModal.show({message: err.message || '다시 점검 실패', status: 0});
                        btn.disabled = false;
                    });
            });
        });
    }

    // S6-3-4 — [현재 내용을 정본으로 수용] : 자원명 확인 + 비동기 작업 시작. 완료/실패는 bgjob 이벤트.
    function bindAcceptHashForms() {
        document.querySelectorAll('form[data-accept-hash-url]').forEach(form => {
            form.addEventListener('submit', ev => {
                ev.preventDefault();
                const input = form.querySelector('input[name=typedName]');
                const btn = form.querySelector('button[type=submit]');
                if (!input.value.trim()) { input.focus(); return; }
                btn.disabled = true;
                fetch(form.dataset.acceptHashUrl, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded', 'Accept': 'application/json'},
                    body: 'typedName=' + encodeURIComponent(input.value.trim())
                }).then(res => {
                    if (!res.ok) {
                        // R2-6 공용 파서 재사용 — raw JSON 노출 방지 (거절 사유를 정제된 문구로)
                        ErrorModal.fromResponse(res, {fallback: '수용을 시작하지 못했어요.'});
                        btn.disabled = false;
                        return null;
                    }
                    return res.json();
                }).then(data => {
                    if (!data) return;
                    selfJobs.set(data.jobId, btn);
                    btn.textContent = '지문 재계산 중…';
                    toast('내용 수용 작업을 시작했습니다 — 완료되면 알려드립니다.');
                }).catch(() => {
                    ErrorModal.show({message: '서버와 통신할 수 없어요.', status: 0});
                    btn.disabled = false;
                });
            });
        });
    }

    // HF4-5 — [자원 중복 존재] 택일 해소 : 정적 dupResolve 모달(radio 2택일)에서 남길 쪽을 고르고
    // form 의 hidden survivor 를 채워 async 제출한다. 열릴 때마다 기본 선택을 원본(DB 기록 경로)으로
    // 리셋 — 이전 열람의 선택이 잔존해 실수로 원본이 삭제되는 사고를 막는다 (사용자 결정 ③).
    function bindDuplicateResolveForms() {
        if (!window.ConfirmModal) return;
        ConfirmModal.bindFormSubmit('data-duplicate-resolve', form => {
            ConfirmModal.open('dupResolve', {
                title: '자원 중복 해소',
                message: (form.getAttribute('data-resource-label') || '이 자원')
                    + ' 이(가) 두 위치에 존재합니다. 남길 위치를 선택하세요 — 선택하지 않은 쪽 파일은 삭제됩니다.',
                confirmLabel: '선택 적용',
                confirmClass: 'n-btn-outline-danger',
                afterOpen: () => {
                    const origEl = document.getElementById('dupResolveOriginalPath');
                    const dupEl = document.getElementById('dupResolveDuplicatePath');
                    if (origEl) origEl.textContent = form.getAttribute('data-original-path') || '';
                    if (dupEl) dupEl.textContent = form.getAttribute('data-duplicate-path') || '';
                    document.querySelectorAll('input[name="dupResolveSurvivor"]')
                        .forEach(r => { r.checked = (r.value === 'ORIGINAL'); });
                    return null;
                },
                onConfirm: () => {
                    const chosen = document.querySelector('input[name="dupResolveSurvivor"]:checked');
                    form.querySelector('input[name="survivor"]').value = chosen ? chosen.value : 'ORIGINAL';
                    ConfirmModal.approveAndSubmit(form);
                }
            });
        });
    }

    document.addEventListener('DOMContentLoaded', () => {
        bindRecheckButtons();
        bindAcceptHashForms();
        bindDuplicateResolveForms();
        ['scanBtn', 'scanDeepBtn', 'reissueBtn'].forEach(id => {
            const btn = document.getElementById(id);
            if (btn) btn.addEventListener('click', () => trigger(btn));
        });

        const reloadBtn = document.getElementById('newReportReloadBtn');
        if (reloadBtn) reloadBtn.addEventListener('click', () => window.location.reload());

        // 자기 트리거 스캔/폼 액션 완료 → reload 직전에 남긴 토스트를 표시. 읽는 즉시 삭제.
        const pending = sessionStorage.getItem(TOAST_KEY);
        if (pending) {
            sessionStorage.removeItem(TOAST_KEY);
            toast(pending);
        }

        if (window.ConfirmModal) {
            bindDriftForm('data-confirm-drift-apply', 'DRIFT_APPLY',
                '{resource} 을(를) 적용할까요?', '이 드리프트를 적용할까요?');
            bindDriftForm('data-confirm-drift-dismiss', 'DRIFT_DISMISS',
                '{resource} 보고를 닫을까요?', '이 드리프트 보고를 닫을까요?');
        }

        // R9-3 — async 제출 성공 피드백. 거절/네트워크 오류는 전역 핸들러(error-modal.js → ErrorModal)에 위임하고
        // onSuccess 만 교체해 토스트를 reload 너머로 전달한다 (trash-action.js 의 페이지 로컬 override 선례).
        const base = window.AsyncSubmitResult;
        window.AsyncSubmitResult = {
            onSuccess: (form) => {
                if (form && form.hasAttribute('data-confirm-drift-apply')) {
                    sessionStorage.setItem(TOAST_KEY, '드리프트를 적용했습니다.');
                } else if (form && form.hasAttribute('data-confirm-drift-dismiss')) {
                    sessionStorage.setItem(TOAST_KEY, '드리프트 보고를 닫았습니다.');
                } else if (form && form.hasAttribute('data-duplicate-resolve')) {
                    // HF4-5 — 택일 해소 성공. 어느 갈래였는지는 서버 [AUDIT] 로그가 보존.
                    sessionStorage.setItem(TOAST_KEY, '자원 중복을 해소했습니다.');
                }
                window.location.reload();
            },
            onRejected: (form, status, payload) => {
                if (base && base.onRejected) { base.onRejected(form, status, payload); return; }
                ErrorModal.show({message: (payload && payload.message) || ('요청이 거절되었어요. (HTTP ' + status + ')'), status: status});
            },
            onNetworkError: (form, err) => {
                if (base && base.onNetworkError) { base.onNetworkError(form, err); return; }
                ErrorModal.show({message: '서버와 통신할 수 없어요.', status: 0});
            }
        };
    });
})();
