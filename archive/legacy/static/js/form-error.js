/**
 * form-error.js — 서버 ErrorResponse 의 field-path 기반 인라인 렌더러 (공통 모듈)
 * ============================================================================
 *
 * Spring 측 `GlobalExceptionHandler` 가 돌려주는 에러 응답을 DOM 에 인라인으로
 * 표시하는 작은 런타임 라이브러리. IIFE 로 감싸 `window.FormError` 하나만 노출.
 *
 * ── 서버 응답 계약 ─────────────────────────────────────────────────────────
 *
 * 서버는 400 응답 바디로 다음 JSON 을 내려준다 (`ErrorResponse` 레코드):
 *
 *   {
 *     "code":        "VALIDATION_FAILED" | "INVALID_ARGUMENT" | "MALFORMED_JSON" | ...,
 *     "message":     "사람이 읽을 수 있는 대표 메시지",
 *     "fieldErrors": [ { "field": "<경로>", "message": "..." }, ... ] | null,
 *     "timestamp":   "..."
 *   }
 *
 * `fieldErrors` 가 비어 있거나 null 이면 폼-레벨 에러로 취급해 `#formErrorArea` 같은
 * 폼 루트의 폴백 영역에 `message` 를 표시한다.
 *
 * ── 필드 경로 규칙 ────────────────────────────────────────────────────────
 *
 * Bean Validation 이 생성하는 경로 포맷과 일치하며, Java 측 `FieldValidationException`
 * 을 `SettingService.resolveOneAtIndex()` 가 인덱스 프리픽스로 가공한 결과이기도 하다.
 *
 *   - "name"                                    → 폼 루트 필드
 *   - "processList[0].boardModelId"             → 0 번째 스텝의 단일 필드
 *   - "processList[1].partitions"               → 1 번째 스텝의 섹션 래퍼
 *   - "processList[1].partitions[0].mountPoint" → 정규화 후 "partitions" 섹션에 표시
 *
 * 깊은 경로는 최상위 서브필드 이름으로 정규화되어 섹션 단위 강조만 제공된다. (세부
 * 셀 단위 강조가 필요하면 별도 확장 필요.)
 *
 * ── DOM 마커 계약 ─────────────────────────────────────────────────────────
 *
 * HTML 측에는 두 종류의 data-* 속성이 필요하다:
 *
 *   1. 폼 루트 / 스텝 스코프:
 *      <div data-error-scope="BASIC_UPDATE"> ... </div>
 *
 *      `processList[i]` 의 i 를 DOM 스코프로 역매핑할 때 사용. 호출자가 제출 시점에
 *      `options.stepTypeByIndex` 배열 (e.g. ["BASIC_UPDATE","OS_INSTALLATION"]) 을
 *      만들어 넘겨주면 i → scope 이름으로 변환된다.
 *
 *   2. 필드 마커:
 *      <select data-error-field="boardModelId">
 *      <div    data-error-field="partitions"> ... </div>
 *
 *      서버 경로의 마지막 서브필드 이름과 일치해야 한다. 매칭 실패 시 스코프 루트
 *      자체에 에러가 표시되고, 그것도 실패하면 폼-레벨 폴백 영역에 노출된다.
 *
 * ── 공개 API ───────────────────────────────────────────────────────────────
 *
 *   FormError.clearAll(root?)
 *       root 이하(생략 시 document) 의 .has-error / .field-error-message 제거 +
 *       #formErrorArea 초기화.
 *
 *   FormError.renderResponse(errBody, options?)
 *       errBody  = 서버의 ErrorResponse JSON 본문
 *       options  = {
 *           stepTypeByIndex: string[]            // optional — processList[i] 역매핑용
 *           formErrorAreaId: string = 'formErrorArea'  // optional — 폴백 영역 id
 *       }
 *       필드 매칭 성공 항목은 인라인으로, 매칭 실패 항목은 폼-레벨 영역에 모아 표시.
 *       첫 매칭 성공 위치로 scrollIntoView 호출.
 *
 *   FormError.renderFormLevel(message, areaId?)
 *       폼-레벨 영역에 단일 메시지 표시. areaId 생략 시 'formErrorArea'.
 *
 * ── 사용 예시 (setting/new.html 요약) ─────────────────────────────────────
 *
 *   const stepTypeByIndex = [];
 *   // 제출 시점에 순서대로 push...
 *   const res = await fetch(...);
 *   if (!res.ok) {
 *     const errBody = await res.json();
 *     FormError.renderResponse(errBody, { stepTypeByIndex });
 *     return;
 *   }
 *   FormError.clearAll();
 */
(function (window) {
    'use strict';

    /** querySelector 에 값을 주입할 때 특수문자를 escape. */
    function cssEscape(s) {
        return (window.CSS && CSS.escape) ? CSS.escape(s) : String(s).replace(/[^\w-]/g, '\\$&');
    }

    /**
     * 주어진 root 이하 (기본 document) 의 모든 에러 마커를 제거.
     * 폼-레벨 폴백 영역도 함께 초기화.
     */
    function clearAll(root) {
        const scope = root || document;
        scope.querySelectorAll('.has-error').forEach(el => el.classList.remove('has-error'));
        scope.querySelectorAll('.field-error-message').forEach(el => el.remove());
        const formErrorArea = document.getElementById('formErrorArea');
        if (formErrorArea) {
            formErrorArea.classList.add('d-none');
            formErrorArea.textContent = '';
        }
    }

    /** 대상 요소에 .has-error 클래스와 메시지 div 를 부착.
     *  이후 값이 입력/변경되면 유효성 검사 없이 에러 스타일만 즉시 제거한다. */
    function paintFieldError(target, message) {
        if (!target) return false;
        target.classList.add('has-error');
        const msgDiv = document.createElement('div');
        msgDiv.className = 'field-error-message';
        msgDiv.textContent = message;
        target.appendChild(msgDiv);

        // 값이 입력/변경되면 에러 스타일 제거 (유효성 재검사 없음).
        // input 이벤트: 텍스트 입력 / change 이벤트: select 선택 및 컨테이너 내부 버블링.
        function clearOnInteract() {
            target.classList.remove('has-error');
            target.querySelectorAll('.field-error-message').forEach(function (el) { el.remove(); });
            target.removeEventListener('input', clearOnInteract);
            target.removeEventListener('change', clearOnInteract);
        }
        target.addEventListener('input', clearOnInteract);
        target.addEventListener('change', clearOnInteract);

        return true;
    }

    function renderFormLevel(message, areaId) {
        const id = areaId || 'formErrorArea';
        const area = document.getElementById(id);
        if (!area) {
            // 폴백: 폼 루트에 alert 영역이 없으면 콘솔에만 기록.
            console.warn('[FormError] formErrorArea not found. message:', message);
            return;
        }
        area.textContent = message;
        area.classList.remove('d-none');
        area.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }

    /**
     * 필드 경로 → DOM target 매칭.
     * 매칭 실패 시 null. 호출자는 폼-레벨 에러로 폴백해야 한다.
     */
    function findErrorTarget(fieldPath, stepTypeByIndex) {
        // 폼 루트 필드 (e.g. "name")
        const processListMatch = fieldPath.match(/^processList\[(\d+)\](?:\.(.+))?$/);
        if (!processListMatch) {
            return document.querySelector('[data-error-field="' + cssEscape(fieldPath) + '"]');
        }
        const index     = parseInt(processListMatch[1], 10);
        const remainder = processListMatch[2]; // undefined 또는 "boardModelId" / "partitions[0].mountPoint"
        const scope     = stepTypeByIndex && stepTypeByIndex[index];
        if (!scope) return null;
        const scopeRoot = document.querySelector('[data-error-scope="' + cssEscape(scope) + '"]');
        if (!scopeRoot) return null;
        if (!remainder) return scopeRoot;
        // 깊은 경로는 최상위 필드 이름으로 정규화해 섹션 단위 표시만 제공.
        const topField  = remainder.split(/[.\[]/)[0];
        return scopeRoot.querySelector('[data-error-field="' + cssEscape(topField) + '"]') || scopeRoot;
    }

    /**
     * 서버 ErrorResponse 바디를 받아 인라인 + 폼-레벨 에러를 함께 표시.
     * options.stepTypeByIndex 가 없으면 processList[i] 경로의 역매핑이 불가해
     * 해당 항목은 모두 폼-레벨 폴백으로 떨어진다.
     */
    function renderResponse(errBody, options) {
        const opts             = options || {};
        const stepTypeByIndex  = opts.stepTypeByIndex;
        const formErrorAreaId  = opts.formErrorAreaId || 'formErrorArea';

        clearAll();

        const fieldErrors = (errBody && errBody.fieldErrors) || [];
        if (fieldErrors.length === 0) {
            renderFormLevel(
                (errBody && errBody.message) || '요청 처리에 실패했습니다.',
                formErrorAreaId
            );
            return;
        }

        let firstPainted = null;
        const unmatched  = [];
        fieldErrors.forEach(fe => {
            const target = findErrorTarget(fe.field, stepTypeByIndex);
            if (paintFieldError(target, fe.message)) {
                if (!firstPainted) firstPainted = target;
            } else {
                unmatched.push('[' + fe.field + '] ' + fe.message);
            }
        });

        if (unmatched.length > 0) renderFormLevel(unmatched.join('\n'), formErrorAreaId);
        if (firstPainted) firstPainted.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }

    // 공개 네임스페이스.
    // paintField: 섹션 div 에 has-error 마킹 + 메시지 삽입. input 에는 사용하지 말 것.
    window.FormError = {
        clearAll: clearAll,
        renderResponse: renderResponse,
        renderFormLevel: renderFormLevel,
        paintField: paintFieldError
    };
})(window);
