/*
 * BIOS 세팅 템플릿 작성/수정 편집기 클라이언트 로직 (vanilla JS, 구 bios-setup.js 이관).
 * - 탭/브레드크럼 네비게이션 (서버가 모든 페이지를 렌더, 클라가 표시 토글)
 * - 도움말 패널 (focus/hover)
 * - 의존성 엔진 (동적 Hidden/GrayOut — 실제 BIOS 처럼 종속 항목을 가림)
 * - 변경분 diff (기준선 data-default 에서 바뀐 (AttributeName→값) 만 전송)
 * - 수정 모드 pre-fill: data-stored(저장값)를 위젯 선택값으로만 적용 — 기준선 불변 (applyStoredValues 참고)
 * - 템플릿 저장: data-save-method/data-save-url (생성 POST · 수정 PUT) → 성공 시 data-done-url 이동
 *   (생성 201 → 목록, 수정 200 → 상세) · 실패 → FormError 배너 + fieldErrors 메타/위젯 매핑
 * - 저장 사전 차단 (서버 판정과 동일 SSOT: name 비어있음 / 변경분 0건 — 수정도 emptyDiff 400 동일) — disabled + tooltip
 */
(function () {
	'use strict';

	const form = document.getElementById('biosForm');
	if (!form) return;
	const saveUrl = form.dataset.saveUrl;
	const saveMethod = (form.dataset.saveMethod || 'POST').toUpperCase();
	const doneUrl = form.dataset.doneUrl;
	// data-board-model-id = management BoardModel FK — 생성 계약은 숫자 타입이므로 여기서 변환한다.
	const boardModelId = form.dataset.boardModelId ? Number(form.dataset.boardModelId) : null;
	const items = document.getElementById('biosItems');
	const tabs = Array.from(document.querySelectorAll('.bios-tab'));
	const pages = Array.from(document.querySelectorAll('.bios-page'));
	const breadcrumb = document.getElementById('biosBreadcrumb');
	const helpText = document.getElementById('biosHelpText');
	const helpMeta = document.getElementById('biosHelpMeta');
	const nameInput = document.getElementById('templateName');
	const descriptionInput = document.getElementById('templateDescription');
	const saveButton = document.getElementById('biosSave');
	const saveWrap = document.getElementById('biosSaveWrap');

	const pageById = new Map();
	pages.forEach(p => pageById.set(p.dataset.page, p));

	let crumbs = []; // [{pageId, title}]

	/* ───────── 네비게이션 ───────── */
	function showPage(pageId) {
		pages.forEach(p => p.classList.toggle('is-active', p.dataset.page === pageId));
		if (items) items.scrollTop = 0;
	}

	function pageTitle(pageId) {
		const p = pageById.get(pageId);
		const t = p && p.querySelector('.bios-page-title');
		return t ? t.textContent : pageId;
	}

	function renderBreadcrumb() {
		breadcrumb.innerHTML = '';
		crumbs.forEach((c, i) => {
			if (i > 0) {
				const sep = document.createElement('span');
				sep.className = 'bios-crumb-sep';
				sep.textContent = '›';
				breadcrumb.appendChild(sep);
			}
			const last = i === crumbs.length - 1;
			const node = document.createElement(last ? 'span' : 'button');
			node.className = 'bios-crumb' + (last ? ' is-current' : '');
			node.textContent = c.title;
			if (!last) {
				node.type = 'button';
				node.addEventListener('click', () => {
					crumbs = crumbs.slice(0, i + 1);
					showPage(c.pageId);
					renderBreadcrumb();
				});
			}
			breadcrumb.appendChild(node);
		});
	}

	function activateTab(pageId) {
		tabs.forEach(t => t.classList.toggle('is-active', t.dataset.menu === pageId));
		crumbs = [{ pageId: pageId, title: pageTitle(pageId) }];
		showPage(pageId);
		renderBreadcrumb();
	}

	function drillInto(pageId) {
		crumbs.push({ pageId: pageId, title: pageTitle(pageId) });
		showPage(pageId);
		renderBreadcrumb();
	}

	function back() {
		if (crumbs.length > 1) {
			crumbs.pop();
			const c = crumbs[crumbs.length - 1];
			showPage(c.pageId);
			renderBreadcrumb();
		}
	}

	tabs.forEach(t => t.addEventListener('click', () => activateTab(t.dataset.menu)));

	items.addEventListener('click', e => {
		const row = e.target.closest('.bios-row--submenu');
		if (!row || !row.dataset.goto) return;
		drillInto(row.dataset.goto);
	});

	/* ───────── 도움말 패널 ───────── */
	function showHelp(row) {
		if (!row) return;
		helpText.textContent = row.dataset.help || '';
		const meta = [];
		const input = row.querySelector('[data-type]');
		if (input && input.dataset.type === 'number') {
			meta.push('범위 ' + input.min + '~' + input.max + ' (증분 ' + input.step + ')');
		}
		if (row.dataset.reset === 'true') meta.push('변경 시 재부팅 필요');
		helpMeta.textContent = meta.join('  ·  ');
	}

	items.addEventListener('focusin', e => showHelp(e.target.closest('.bios-row')));
	items.addEventListener('mouseover', e => {
		const r = e.target.closest('.bios-row');
		if (r) showHelp(r);
	});

	/* ───────── 의존성 엔진 (동적 Hidden/GrayOut) ───────── */
	const rules = Array.from(document.querySelectorAll('#biosDepData .bios-dep')).map(el => ({
		from: el.dataset.from, fromVal: el.dataset.fromval, to: el.dataset.to, prop: el.dataset.prop
	}));
	const rulesBySource = new Map();
	rules.forEach(r => {
		if (!rulesBySource.has(r.from)) rulesBySource.set(r.from, []);
		rulesBySource.get(r.from).push(r);
	});
	const targets = new Set(rules.map(r => r.to));

	function cssEsc(s) {
		return (window.CSS && CSS.escape) ? CSS.escape(s) : String(s).replace(/"/g, '\\"');
	}
	function controlOf(attr) { return document.querySelector('[data-attr="' + cssEsc(attr) + '"]'); }
	function rowOf(attr) { return document.querySelector('.bios-row[data-row="' + cssEsc(attr) + '"]'); }

	// MapFromValue 혼합타입(bool false / int 1 / string "Disabled") 정규화 — 타입 엄격 비교 대신 on/off.
	function onOff(v) {
		const s = String(v == null ? '' : v).trim().toLowerCase();
		if (['false', '0', 'disable', 'disabled', 'off', 'no'].indexOf(s) >= 0) return 'off';
		if (['true', '1', 'enable', 'enabled', 'on', 'yes'].indexOf(s) >= 0) return 'on';
		return s;
	}

	function evalTarget(attr) {
		const governing = rules.filter(r => r.to === attr);
		if (!governing.length) return;
		let hidden = false, gray = false;
		governing.forEach(r => {
			const src = controlOf(r.from);
			if (!src) return;
			if (onOff(src.value) === onOff(r.fromVal)) {
				if (r.prop === 'HIDDEN') hidden = true;
				else if (r.prop === 'GRAYOUT') gray = true;
			}
		});
		const row = rowOf(attr);
		const ctrl = controlOf(attr);
		if (row) {
			row.classList.toggle('is-hidden', hidden);
			row.classList.toggle('is-grayout', gray && !hidden);
		}
		if (ctrl) {
			const readonly = ctrl.dataset.readonly === 'true';
			ctrl.disabled = readonly || hidden || gray;
		}
	}

	function evalAll() { targets.forEach(evalTarget); }

	// 조건부 활성 orphan (XML 에 없는 종속 속성): 같은 페이지의 controller 값에 따라 활성/강제.
	// controller 를 같은 페이지(.bios-page) 범위에서 찾아 전역 중복 data-attr 와의 잘못된 결합을 피한다.
	function applyConditionals() {
		document.querySelectorAll('[data-enable-when]').forEach(ctrl => {
			const page = ctrl.closest('.bios-page');
			const controller = page ? page.querySelector('[data-attr="' + cssEsc(ctrl.dataset.enableWhen) + '"]') : null;
			const enabled = !!controller && controller.value === ctrl.dataset.enableValue;
			const row = ctrl.closest('.bios-row');
			if (enabled) {
				ctrl.disabled = ctrl.dataset.readonly === 'true';
				if (row) row.classList.remove('is-grayout');
			} else {
				if (ctrl.dataset.forced != null && ctrl.dataset.forced !== '') {
					ctrl.value = ctrl.dataset.forced; // controller 가 enable 값이 아니면 특정 값으로 강제.
				}
				ctrl.disabled = true;
				if (row) row.classList.add('is-grayout');
			}
		});
	}

	items.addEventListener('change', e => {
		const attr = e.target.dataset && e.target.dataset.attr;
		// 전이적 의존성 체인(A→B→C)까지 한 번에 반영하려면 직접 target 만 보지 말고 전체 closure 를 재평가한다.
		// (직접 target 만 보면, 중간 노드가 disabled 되어 change 이벤트를 못 내보내 체인이 stale 해진다.)
		if (attr && rulesBySource.has(attr)) {
			evalAll();
		}
		applyConditionals(); // controller 변경 시 조건부 orphan 활성/강제 갱신 (dep source 가 아닐 수 있어 항상 호출).
		updateSaveGate();    // 값 변경 = diff 변동 → 저장 차단 판정 갱신.
	});

	/* ───────── 변경분 diff ───────── */
	function collectChanged() {
		const changed = {};
		document.querySelectorAll('[data-attr]').forEach(ctrl => {
			if (ctrl.disabled) return; // readonly / hidden / grayout 제외
			const row = ctrl.closest('.bios-row');
			if (row && (row.classList.contains('is-hidden') || row.classList.contains('is-grayout'))) return;
			const cur = String(ctrl.value);
			const def = ctrl.dataset.default != null ? String(ctrl.dataset.default) : '';
			if (cur !== def) changed[ctrl.dataset.attr] = cur;
		});
		return changed;
	}

	/* ───────── 저장 사전 차단 (서버 판정과 동일 SSOT) ─────────
	   서버: name @NotBlank(400) / 유효 변경분 0건 emptyDiff(400).
	   UI 는 같은 조건을 disabled + tooltip 으로 선차단하고, 서버 검증은 direct POST 안전망으로 남는다. */
	function saveBlockReasons() {
		const reasons = [];
		if (!nameInput || !nameInput.value.trim()) reasons.push('템플릿 명칭을 입력하세요.');
		if (Object.keys(collectChanged()).length === 0) reasons.push('기본값에서 변경된 속성이 없습니다.');
		return reasons;
	}

	function updateSaveGate() {
		const reasons = saveBlockReasons();
		const blocked = reasons.length > 0;
		if (saveButton) saveButton.disabled = blocked;
		if (saveWrap) {
			saveWrap.dataset.tooltip = reasons.join(' · ');
			saveWrap.dataset.tooltipActive = String(blocked);
		}
	}

	if (nameInput) nameInput.addEventListener('input', updateSaveGate);
	items.addEventListener('input', updateSaveGate); // number 입력 타이핑은 change 전에 diff 가 변한다.

	/* ───────── 템플릿 저장 (XHR — 생성 POST / 수정 PUT) ───────── */
	form.addEventListener('submit', async e => {
		e.preventDefault();
		if (saveButton && saveButton.disabled) return; // F4/requestSubmit 우회 대비 게이트 재확인.
		clearErrors();
		const payload = {
			name: nameInput ? nameInput.value.trim() : '',
			description: descriptionInput && descriptionInput.value.trim() !== '' ? descriptionInput.value.trim() : null,
			attributes: collectChanged()
		};
		if (saveMethod === 'POST') {
			payload.boardModelId = boardModelId; // 생성 계약에만 존재 — 수정(PUT)은 보드 귀속(boardModelId) 불변(계약 밖).
		}
		if (saveButton) saveButton.disabled = true; // 이중 제출 방지.
		try {
			const res = await fetch(saveUrl, {
				method: saveMethod,
				headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
				body: JSON.stringify(payload)
			});
			if (res.ok) {
				window.location.href = doneUrl; // 생성 201 → 목록 / 수정 200 → 상세.
				return;
			}
			const body = await res.json().catch(() => null);
			renderError(body || { message: '요청을 처리할 수 없습니다 (HTTP ' + res.status + ').' });
		} catch (err) {
			renderError({ message: '서버와 통신하지 못했습니다: ' + String(err) });
		} finally {
			updateSaveGate(); // 실패 시 게이트 재산정으로 버튼 복원 (성공 시엔 페이지 이탈).
		}
	});

	/* ───────── 서버 에러 매핑 ─────────
	   ApiErrorResponse {message, fieldErrors:[{field,message}], code} 를
	   ① AttributeName 형 필드(위젯 존재) → 해당 행 has-error + 페이지 자동 전환/스크롤,
	     메시지는 displayName 라벨로 배너에 병기 (위젯 행에는 텍스트 슬롯이 없음).
	   ② 그 외(name/description/attributes 등) → FormError(공통 헬퍼)가 메타 필드/배너에 매핑. */
	function renderError(body) {
		const fieldErrors = (body && body.fieldErrors) || [];
		const metaErrors = [];
		const widgetErrors = [];
		fieldErrors.forEach(fe => {
			if (fe && fe.field && controlOf(fe.field)) widgetErrors.push(fe);
			else metaErrors.push(fe);
		});

		widgetErrors.forEach(fe => {
			const ctrl = controlOf(fe.field);
			const row = ctrl && ctrl.closest('.bios-row');
			if (row) row.classList.add('has-error');
		});
		if (widgetErrors.length) revealAttribute(widgetErrors[0].field);

		if (window.FormError) {
			// field 없는 항목은 FormError 가 배너로 폴백한다 — 위젯 에러를 displayName 라벨로 변환해 동봉.
			const bannerFallbacks = widgetErrors.map(fe => ({ message: widgetLabel(fe.field) + ' — ' + (fe.message || '') }));
			window.FormError.renderResponse({
				message: body && body.message,
				fieldErrors: metaErrors.concat(bannerFallbacks)
			});
		}
	}

	// 위젯 행 라벨은 DisplayName(사용자 표시명) — AttributeName(원시 키)은 위젯을 못 찾을 때의 보조 폴백.
	function widgetLabel(attr) {
		const row = rowOf(attr);
		const label = row && row.querySelector('.bios-label');
		return label ? label.textContent : attr;
	}

	// 에러 위젯이 있는 페이지로 자동 전환: data-parent 체인을 따라 탭→하위 페이지 순서로 재현 후 스크롤.
	function revealAttribute(attr) {
		const row = rowOf(attr);
		const page = row && row.closest('.bios-page');
		if (!page) return;
		const chain = [];
		let node = page, guard = 0;
		while (node && guard++ < 20) {
			chain.unshift(node);
			node = pageById.get(node.dataset.parent);
		}
		activateTab(chain[0].dataset.page);
		for (let i = 1; i < chain.length; i++) drillInto(chain[i].dataset.page);
		row.scrollIntoView({ block: 'center' });
	}

	function clearErrors() {
		document.querySelectorAll('.bios-row.has-error').forEach(r => r.classList.remove('has-error'));
		if (window.FormError) window.FormError.clear();
	}

	/* ───────── 기본값 복원 (F3) ───────── */
	function resetDefaults() {
		document.querySelectorAll('[data-attr]').forEach(ctrl => {
			ctrl.value = ctrl.dataset.default != null ? ctrl.dataset.default : '';
		});
		clearErrors();
		evalAll();
		applyConditionals();
		updateSaveGate();
	}
	document.getElementById('biosResetDefaults').addEventListener('click', resetDefaults);

	/* ───────── 키보드 단축키 ───────── */
	document.addEventListener('keydown', e => {
		if (e.key === 'Escape') {
			back();
		} else if (e.key === 'F4') {
			e.preventDefault();
			form.requestSubmit();
		} else if (e.key === 'F3') {
			e.preventDefault();
			resetDefaults();
		}
	});

	/* ───────── 저장값 pre-fill (수정 모드) ─────────
	   data-stored 는 위젯의 "선택값"만 저장값으로 세팅하고 data-default(diff 기준선)는 생성 때와
	   동일하게 둔다 → collectChanged() 가 기준선 대비 "전체 변경분"을 재수집해 PUT 의 전체 교체
	   의미론을 충족하고, 사용자가 기본값으로 되돌린 속성은 diff 에서 자연 탈락한다(별도 삭제 연산 불필요).
	   저장값이 카탈로그 개정으로 옵션에서 사라진 경우 select 는 값 세팅이 무시되어 기본값에 머문다
	   (stale 속성 재저장 시 탈락 — 상세 페이지가 경고로 안내). */
	function applyStoredValues() {
		document.querySelectorAll('[data-attr]').forEach(ctrl => {
			const stored = ctrl.dataset.stored;
			if (stored == null) return; // 생성 화면·미저장 속성 — 서버가 data-stored 를 렌더하지 않는다.
			ctrl.value = stored;
		});
	}

	/* ───────── 초기화 ───────── */
	applyStoredValues();
	if (tabs.length) activateTab(tabs[0].dataset.menu);
	// storedValue 적용 직후 1회 재평가 — 저장값이 controller 인 의존성/조건부 orphan 도 라이브 상태로 정렬.
	evalAll();
	applyConditionals();
	updateSaveGate();
})();
