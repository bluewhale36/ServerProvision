/*
  병기형 조사 해소 — window.KoreanParticle
  ─────────────────────────────────────
  확인창·안내 문구는 자원명을 런타임에 끼워 넣으므로 조사를 미리 고를 수 없어
  '{resource} 을(를) 삭제할까요?' 처럼 병기형으로 적어 두었고, 그대로 사용자에게 노출됐다.
  본 모듈은 치환이 끝난 문자열을 받아 조사 앞 글자의 받침으로 한쪽을 고르고,
  조사 앞 띄어쓰기까지 정규화한다. 문구 템플릿은 그대로 두고 이 한 곳만 통과시킨다.

  판정 규칙 (HF5 plan §4 가 SSOT — 7 항):
    1. 조사 바로 앞 글자를 본다. 공백·괄호·따옴표는 건너뛴다.
    2. 한글 음절은 (코드 - 0xAC00) % 28 != 0 이면 받침 있음.
    3. 숫자는 한국어 읽기 기준 — 받침 있음 = 0 1 3 6 7 8.
    4. 라틴 문자는 글자 이름의 끝소리 기준 — 받침 있음은 l(엘) m(엠) n(엔) 셋뿐.
    5. 판정 불가(기호 등)는 받침 없는 쪽.
    6. '으로(로)' 만 ㄹ 받침을 받침 없음처럼 다룬다.
    7. 앞 글자가 한글이면 조사를 붙이고(앞 공백 제거), 아니면 한 칸 띄운다.
*/
(function () {
    'use strict';

    const PAIRS = [
        {token: '을(를)', on: '을', off: '를'},
        {token: '이(가)', on: '이', off: '가'},
        {token: '은(는)', on: '은', off: '는'},
        {token: '와(과)', on: '과', off: '와'},
        {token: '아(야)', on: '아', off: '야'},
        {token: '으로(로)', on: '으로', off: '로'}
    ];

    const DIGIT_BATCHIM = {'0': true, '1': true, '2': false, '3': true, '4': false,
                           '5': false, '6': true, '7': true, '8': true, '9': false};
    // ㄹ 받침으로 끝나는 숫자 읽기 — 일 · 칠 · 팔 (규칙 6 의 '으로(로)' 판정에만 쓰인다)
    const DIGIT_RIEUL = {'1': true, '7': true, '8': true};
    const LATIN_BATCHIM = ['l', 'm', 'n'];
    const SKIP = /[\s()\[\]{}'"“”‘’`]/;

    const HANGUL_START = 0xAC00;
    const HANGUL_END = 0xD7A3;
    const JONG_RIEUL = 8;

    /** 조사 바로 앞의 판정 대상 글자. 건너뛸 문자만 있거나 비면 null. */
    function lastChar(prefix) {
        for (let i = prefix.length - 1; i >= 0; i--) {
            const ch = prefix.charAt(i);
            if (!SKIP.test(ch)) return ch;
        }
        return null;
    }

    /** 한 글자의 받침 여부 판정. hangul 은 규칙 7 의 띄어쓰기 분기에 쓰인다. */
    function judge(ch) {
        if (ch === null) return {hangul: false, batchim: false, rieul: false};
        const code = ch.charCodeAt(0);
        if (code >= HANGUL_START && code <= HANGUL_END) {
            const jong = (code - HANGUL_START) % 28;
            return {hangul: true, batchim: jong !== 0, rieul: jong === JONG_RIEUL};
        }
        if (ch >= '0' && ch <= '9') {
            return {hangul: false, batchim: DIGIT_BATCHIM[ch], rieul: !!DIGIT_RIEUL[ch]};
        }
        const lower = ch.toLowerCase();
        if (lower >= 'a' && lower <= 'z') {
            return {hangul: false, batchim: LATIN_BATCHIM.indexOf(lower) !== -1, rieul: lower === 'l'};
        }
        return {hangul: false, batchim: false, rieul: false};
    }

    /**
     * 문자열 안의 병기형 조사를 모두 해소한다. 해소할 것이 없으면 원문 그대로 돌려준다.
     * 이미 해소된 문자열을 다시 넣어도 같은 결과다 (중복 호출 안전).
     */
    function resolve(text) {
        if (typeof text !== 'string' || text.length === 0) return text;
        let out = text;
        PAIRS.forEach(pair => {
            let idx = out.indexOf(pair.token);
            while (idx !== -1) {
                const head = out.slice(0, idx);
                const j = judge(lastChar(head));
                const chosen = (pair.token === '으로(로)')
                        ? ((!j.batchim || j.rieul) ? pair.off : pair.on)
                        : (j.batchim ? pair.on : pair.off);
                const trimmed = head.replace(/\s+$/, '');
                const glue = (trimmed === '' || j.hangul) ? '' : ' ';
                out = trimmed + glue + chosen + out.slice(idx + pair.token.length);
                idx = out.indexOf(pair.token);
            }
        });
        return out;
    }

    window.KoreanParticle = {resolve};
})();
