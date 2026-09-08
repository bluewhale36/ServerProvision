/* management/bmc/bmc-new.html 전용 — 본문은 management/common/firmware-new-form.js 한 벌(S17-4). 여기서는 이 화면의 설정만 준다. */
(function () {
    const form = window.FirmwareNewForm.create({
        tag: '[bmc-new]',
        formId: 'bmcForm',
        boundFlag: 'bmcNewBound',
        nudgePrefix: 'nudge',
        formUrl: boardId => '/management/bmc/' + encodeURIComponent(boardId) + '/new'
    });
    window.BmcNewForm = form;
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', form.init);
    } else {
        form.init();
    }
})();
