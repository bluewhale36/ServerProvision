# plan · report · essential html 의 코드 블록 색칠 도구(정본 · 규약은 .claude/plan-report-html-spec.md 2-6).
# 파일을 만들 때 한 번 돌려 <pre data-lang="java|json"> 안을 토큰 span 으로 감싼다. 브라우저 JS · 외부 라이브러리 없음.
# 쓰는 법: python3 .claude/tools/codehl.py <html 파일...>   (같은 파일에 다시 돌려도 이미 칠한 블록은 건너뛴다)
import html
import re
import sys

JAVA_KEYWORDS = (
    "abstract assert boolean break byte case catch char class const continue default do double else enum "
    "extends final finally float for goto if implements import instanceof int interface long native new "
    "package private protected public record return sealed short static strictfp super switch synchronized "
    "this throw throws transient try var void volatile while yield permits non-sealed true false null"
).split()

JAVA = re.compile(
    r'(?P<com>//[^\n]*|/\*.*?\*/)'
    r'|(?P<str>"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])\')'
    r'|(?P<ann>@[A-Za-z_][A-Za-z0-9_]*)'
    r'|(?P<kw>\b(?:' + '|'.join(re.escape(k) for k in JAVA_KEYWORDS) + r')\b)'
    r'|(?P<type>\b[A-Z][a-z][A-Za-z0-9_]*\b)',
    re.S,
)

JSON = re.compile(
    r'(?P<key>"(?:\\.|[^"\\\n])*"(?=\s*:))'
    r'|(?P<str>"(?:\\.|[^"\\\n])*")'
    r'|(?P<kw>\b(?:true|false|null)\b)',
)

GRAMMARS = {"java": JAVA, "json": JSON}

PRE = re.compile(r'(<pre\b[^>]*\bdata-lang="(?P<lang>java|json)"[^>]*>)(?P<body>.*?)(</pre>)', re.S)


def highlight(source, lang):
    """평문 코드를 받아 토큰 span 을 두른 html 조각을 돌려준다."""
    grammar = GRAMMARS[lang]
    out, pos = [], 0
    for m in grammar.finditer(source):
        out.append(html.escape(source[pos:m.start()], quote=False))
        kind = m.lastgroup
        out.append(f'<span class="tok-{kind}">{html.escape(m.group(), quote=False)}</span>')
        pos = m.end()
    out.append(html.escape(source[pos:], quote=False))
    return "".join(out)


def process(text):
    """html 문서 안의 data-lang 코드 블록을 칠한다. 이미 칠한 블록은 그대로 둔다."""
    count = 0

    def repl(m):
        nonlocal count
        body = m.group("body")
        if 'class="tok-' in body:
            return m.group(0)
        count += 1
        return m.group(1) + highlight(html.unescape(body), m.group("lang")) + m.group(4)

    return PRE.sub(repl, text), count


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("쓰는 법: python3 .claude/tools/codehl.py <html 파일...>")
    for path in sys.argv[1:]:
        with open(path, encoding="utf-8") as f:
            text = f.read()
        result, n = process(text)
        with open(path, "w", encoding="utf-8") as f:
            f.write(result)
        print(f"{path}: 코드 블록 {n} 개 칠함")
