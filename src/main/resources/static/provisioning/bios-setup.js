/*
 * BIOS 셋업 클라이언트 로직 (vanilla JS).
 * - 탭/브레드크럼 네비게이션 (서버가 모든 페이지를 렌더, 클라가 표시 토글)
 * - 도움말 패널 (focus/hover)
 * - 의존성 엔진 (동적 Hidden/GrayOut — 실제 BIOS 처럼 종속 항목을 가림)
 * - 변경분 diff (기본값에서 바뀐 (AttributeName→값) 만 전송)
 * - 서버 Redfish 프리뷰 echo 렌더 ("서버가 값을 정확히 인식" 의 가시적 증거)
 */
(function () {
	'use strict';

	const form = document.getElementById('biosForm');
	if (!form) return;
	const saveUrl = form.dataset.saveUrl;
	const items = document.getElementById('biosItems');
	const tabs = Array.from(document.querySelectorAll('.bios-tab'));
	const pages = Array.from(document.querySelectorAll('.bios-page'));
	const breadcrumb = document.getElementById('biosBreadcrumb');
	const helpText = document.getElementById('biosHelpText');
	const helpMeta = document.getElementById('biosHelpMeta');
	const modal = document.getElementById('biosModal');
	const modalBody = document.getElementById('biosModalBody');
	const modalTitle = document.getElementById('biosModalTitle');

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
	});

	/* ───────── 변경분 diff + 전송 ───────── */
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

	form.addEventListener('submit', async e => {
		e.preventDefault();
		clearFieldErrors();
		const changed = collectChanged();
		if (Object.keys(changed).length === 0) {
			openModal('변경 사항 없음', '<p class="bios-modal-note">기본값에서 변경된 항목이 없습니다.</p>');
			return;
		}
		try {
			const res = await fetch(saveUrl, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
				body: JSON.stringify({ attributes: changed })
			});
			const body = await res.json().catch(() => ({}));
			if (res.ok) renderSuccess(body, changed);
			else renderError(body);
		} catch (err) {
			openModal('전송 실패', '<p class="bios-modal-error">서버와 통신하지 못했습니다: ' + escapeHtml(String(err)) + '</p>');
		}
	});

	function renderSuccess(body, changed) {
		const attrs = body.attributes || {};
		const pwChanges = body.passwordChanges || [];
		const attrCount = Object.keys(attrs).length;

		let html = '<p class="bios-modal-note">서버가 <strong>' + (body.changedCount || 0) + '</strong>개 변경을 인식했습니다.'
			+ (body.resetRequired ? ' <span class="bios-reset-note">↻ 재부팅 후 반영</span>' : '') + '</p>';

		if (attrCount > 0) {
			html += '<p class="bios-modal-sub"><strong>' + escapeHtml(body.settingsMethod || 'PATCH') + '</strong> <code>'
				+ escapeHtml(body.settingsTarget || '') + '</code> — Integer 는 따옴표 없는 숫자, Enum 은 문자열로 직렬화:</p>';
			html += '<pre class="bios-json">' + escapeHtml(JSON.stringify({ Attributes: attrs }, null, 2)) + '</pre>';
		}

		if (pwChanges.length) {
			html += '<p class="bios-modal-sub">비밀번호는 Attributes 가 아니라 <code>Bios.ChangePassword</code> 액션으로 별도 전송:</p>';
			html += '<ul class="bios-modal-fields">';
			pwChanges.forEach(pc => {
				html += '<li><code>POST ' + escapeHtml(pc.actionTarget) + '</code> · PasswordName=<code>'
					+ escapeHtml(pc.passwordName) + '</code> <span class="bios-modal-sub">(Old/New 비밀번호는 호출 시 입력)</span></li>';
			});
			html += '</ul>';
		}

		if (body.resetRequired && body.resetTarget) {
			html += '<p class="bios-modal-sub">적용(반영): <code>POST ' + escapeHtml(body.resetTarget)
				+ '</code> <code>{"ResetType":"ForceRestart"}</code> 로 재부팅</p>';
		}

		if (attrCount === 0 && !pwChanges.length) {
			html += '<p class="bios-modal-note">적용 대상 변경이 없습니다.</p>';
		}

		openModal('저장 결과 — Redfish 적용 계획', html);
		// 저장된 값을 새 기본값으로 — 다음 diff 기준 갱신.
		Object.keys(changed).forEach(attr => {
			const ctrl = controlOf(attr);
			if (ctrl) ctrl.dataset.default = ctrl.value;
		});
	}

	function renderError(body) {
		let html = '<p class="bios-modal-error">' + escapeHtml(body && body.message ? body.message : '요청을 처리할 수 없습니다.') + '</p>';
		if (body && body.fieldErrors && body.fieldErrors.length) {
			html += '<ul class="bios-modal-fields">';
			body.fieldErrors.forEach(fe => {
				html += '<li><code>' + escapeHtml(fe.field) + '</code> : ' + escapeHtml(fe.message) + '</li>';
				const ctrl = controlOf(fe.field);
				const row = ctrl && ctrl.closest('.bios-row');
				if (row) row.classList.add('has-error');
			});
			html += '</ul>';
		}
		openModal('저장 거부됨', html);
	}

	function clearFieldErrors() {
		document.querySelectorAll('.bios-row.has-error').forEach(r => r.classList.remove('has-error'));
	}

	/* ───────── 기본값 복원 (F3) ───────── */
	function resetDefaults() {
		document.querySelectorAll('[data-attr]').forEach(ctrl => {
			ctrl.value = ctrl.dataset.default != null ? ctrl.dataset.default : '';
		});
		clearFieldErrors();
		evalAll();
		applyConditionals();
	}
	document.getElementById('biosResetDefaults').addEventListener('click', resetDefaults);

	/* ───────── 모달 ───────── */
	function openModal(title, bodyHtml) {
		modalTitle.textContent = title;
		modalBody.innerHTML = bodyHtml;
		modal.hidden = false;
	}
	document.getElementById('biosModalClose').addEventListener('click', () => { modal.hidden = true; });
	modal.addEventListener('click', e => { if (e.target === modal) modal.hidden = true; });

	/* ───────── 키보드 단축키 ───────── */
	document.addEventListener('keydown', e => {
		if (e.key === 'Escape') {
			if (!modal.hidden) modal.hidden = true;
			else back();
		} else if (e.key === 'F4') {
			e.preventDefault();
			form.requestSubmit();
		} else if (e.key === 'F3') {
			e.preventDefault();
			resetDefaults();
		}
	});

	function escapeHtml(s) {
		return String(s).replace(/[&<>"']/g, c => ({
			'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
		}[c]));
	}

	/* ───────── 초기화 ───────── */
	if (tabs.length) activateTab(tabs[0].dataset.menu);
	evalAll();
	applyConditionals();
})();
