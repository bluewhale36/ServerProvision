/*
 * 항목 순서 드래그 공용 모듈 — 세팅 정의서의 표(디스크 묶음 · 볼륨 우선순위, U4-1-1 · U4-1-2)와
 * 펌웨어 버전 목록(E2-1-a)이 같은 구현을 쓴다. 원래 setting-form.js 안에만 있던 구현을 끌어올린
 * 것이라 조작감(잡는 느낌 · 놓일 자리 표시 · 취소 동작)이 화면마다 갈리지 않는다.
 *
 * 구현 원칙 두 가지는 원 구현의 이유를 그대로 잇는다.
 *  - draggable 은 핸들을 누르는 동안만 켠다 — 셀 안 입력의 텍스트 드래그와 겹치지 않게.
 *  - dataTransfer 에 값을 하나 실는다 — Firefox 는 데이터가 없으면 드래그를 시작하지 않는다.
 *
 * 표시 클래스는 rd-dragging · rd-drop-before · rd-drop-after 로 고정하고, 화면별 CSS 가 그 이름에
 * 자기 문맥의 스타일을 붙인다(표는 셀 그림자, Miller 목록은 항목 테두리).
 */
(function (global) {
    'use strict';

    var dragging = null;

    function items(container, selector) {
        return Array.prototype.slice.call(container.querySelectorAll(selector));
    }

    function clearMarks(container, selector) {
        items(container, selector).forEach(function (el) {
            el.classList.remove('rd-drop-before', 'rd-drop-after');
        });
    }

    function dropsBefore(item, event) {
        var rect = item.getBoundingClientRect();
        return event.clientY < rect.top + rect.height / 2;
    }

    /**
     * 항목 하나에 순서 드래그를 붙인다.
     *
     * @param {Object} o
     *   item          {Element} 끌 항목
     *   container     {Element} 형제들이 사는 부모 — 다른 목록의 항목은 받지 않는 판정 기준
     *   handle        {string}  핸들 선택자(item 하위). 핸들이 없으면 바인딩하지 않는다(고정 항목)
     *   itemSelector  {string}  형제 판정 선택자(기본: 항목의 태그명)
     *   payload       {string}  dataTransfer 에 실을 값(Firefox 대응)
     *   onStart       {Function} 드래그 시작 시(선택) — 되돌리기용 스냅샷을 뜨는 자리
     *   onDrop        {Function} 놓은 뒤(선택) — 순서는 이미 DOM 에 반영돼 있다
     */
    function bind(o) {
        var item = o.item;
        var container = o.container;
        var selector = o.itemSelector || item.tagName.toLowerCase();
        var handle = o.handle ? item.querySelector(o.handle) : null;
        if (!handle) return;

        handle.addEventListener('mousedown', function () { item.setAttribute('draggable', 'true'); });
        handle.addEventListener('mouseup', function () { item.removeAttribute('draggable'); });

        item.addEventListener('dragstart', function (e) {
            if (item.getAttribute('draggable') !== 'true') { e.preventDefault(); return; }
            dragging = item;
            item.classList.add('rd-dragging');
            e.dataTransfer.effectAllowed = 'move';
            e.dataTransfer.setData('text/plain', o.payload || 'row');
            if (o.onStart) o.onStart();
        });

        item.addEventListener('dragover', function (e) {
            if (!dragging || dragging === item || dragging.parentNode !== container) return;
            e.preventDefault();
            e.dataTransfer.dropEffect = 'move';
            clearMarks(container, selector);
            item.classList.add(dropsBefore(item, e) ? 'rd-drop-before' : 'rd-drop-after');
        });

        item.addEventListener('drop', function (e) {
            if (!dragging || dragging === item || dragging.parentNode !== container) return;
            e.preventDefault();
            container.insertBefore(dragging, dropsBefore(item, e) ? item : item.nextSibling);
            clearMarks(container, selector);
            if (o.onDrop) o.onDrop();
        });

        item.addEventListener('dragend', function () {
            item.classList.remove('rd-dragging');
            item.removeAttribute('draggable');
            dragging = null;
            clearMarks(container, selector);
        });
    }

    global.RowDrag = { bind: bind };
})(window);
