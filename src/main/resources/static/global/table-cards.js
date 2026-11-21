// R21-1 — 640 이하에서 목록 표(table.n-table)는 행마다 카드로 선다(style.css 휴대폰 절). 카드의 칸마다 붙는 이름표는 머리 칸 글자다.
// 그 글자를 각 칸의 data-label 로 옮긴다. 머리 칸이 정본이라 템플릿에 같은 글자를 두 번 쓰지 않는다.
// 여러 열을 잇는 칸(빈 상태 안내 같은 줄)은 이름표를 비운다. 실시간 갱신으로 새로 그려진 표도 다시 단다.
(function (root) {
    function headNames(table) {
        var head = table.tHead && table.tHead.rows[0];
        if (!head) return null;
        var names = [];
        Array.prototype.forEach.call(head.cells, function (th) {
            var text = th.textContent.replace(/\s+/g, ' ').trim();
            for (var i = 0; i < (th.colSpan || 1); i++) names.push(text);
        });
        return names;
    }

    function labelTable(table) {
        var names = headNames(table);
        if (!names) return;
        Array.prototype.forEach.call(table.tBodies, function (body) {
            Array.prototype.forEach.call(body.rows, function (row) {
                var col = 0;
                Array.prototype.forEach.call(row.cells, function (cell) {
                    var span = cell.colSpan || 1;
                    if (!cell.hasAttribute('data-label')) cell.setAttribute('data-label', span > 1 ? '' : (names[col] || ''));
                    col += span;
                });
            });
        });
    }

    function label(scope) {
        (scope || document).querySelectorAll('table.n-table').forEach(labelTable);
    }

    root.TableCards = { label: label };
    if (typeof document !== 'undefined') {
        document.addEventListener('DOMContentLoaded', function () {
            label(document);
            var queued = false;
            new MutationObserver(function () {
                if (queued) return;
                queued = true;
                requestAnimationFrame(function () { queued = false; label(document); });
            }).observe(document.body, { childList: true, subtree: true });
        });
    }
})(window);
