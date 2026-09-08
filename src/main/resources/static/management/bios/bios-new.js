/* management/bios/bios-new.html 전용 — 본문은 management/common/firmware-new-form.js 한 벌(S17-4). 여기서는 이 화면의 설정만 준다. */
(function () {
    const form = window.FirmwareNewForm.create({
        tag: '[bios-new]',
        formId: 'biosForm',
        boundFlag: 'biosNewBound',
        nudgePrefix: 'biosNudge',
        formUrl: boardId => '/management/bios/' + encodeURIComponent(boardId) + '/new'
    });
    window.BiosNewForm = form;
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', form.init);
    } else {
        form.init();
    }
})();
