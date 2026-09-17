# essential report 공용 SVG 도식 도구(정본 · .claude/essential-html-spec.md 가 규약) — 시퀀스 · 블록 · 분기 · 트리. 색은 문서의 :root 토큰(class)만 쓴다.
import html

MAXW = 1170
FS = 11

def tw(s, fs=FS):
    """문자열 폭 추정(mono ASCII 0.62em · 한글 1.0em)."""
    return sum((fs * 1.0) if ord(c) > 0x2E80 else (fs * 0.62) for c in s)

def esc(s):
    return html.escape(s, quote=False)

def text_block(x, y, lines, anchor="middle", muted=False, bg=True, fs=FS, lh=13):
    w = max(tw(l, fs) for l in lines) + 10
    h = lh * len(lines) + 4
    bx = x - w / 2 if anchor == "middle" else (x - 5 if anchor == "start" else x - w + 5)
    out = []
    if bg:
        out.append(f'<rect class="lbl-bg" x="{bx:.1f}" y="{y - fs - 1:.1f}" width="{w:.1f}" height="{h:.1f}" rx="3"/>')
    cls = ' class="muted"' if muted else ''
    for i, l in enumerate(lines):
        out.append(f'<text x="{x:.1f}" y="{y + i * lh:.1f}" text-anchor="{anchor}"{cls} font-size="{fs}">{esc(l)}</text>')
    return "\n".join(out)

def node(x, y, w, h, lines, kind="part", fs=10, lh=12.5, rx=6):
    out = [f'<g class="n-{kind}"><rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}"/>']
    total = lh * len(lines)
    ty = y + (h - total) / 2 + fs - 1
    for i, l in enumerate(lines):
        weight = ' font-weight="700"' if i == 0 else ''
        out.append(f'<text x="{x + w / 2:.1f}" y="{ty + i * lh:.1f}" text-anchor="middle" font-size="{fs}"{weight}>{esc(l)}</text>')
    out.append("</g>")
    return "\n".join(out)

def defs(prefix):
    return (f'<defs>'
            f'<marker id="{prefix}-arr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" class="arr-fill"/></marker>'
            f'<marker id="{prefix}-arro" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10" class="arr-open"/></marker>'
            f'</defs>')

def edge(prefix, pts, label=None, kind="call", lpos=None, anchor="middle", marker=True):
    d = "M" + " L".join(f"{x},{y}" for x, y in pts)
    mk = f' marker-end="url(#{prefix}-{"arr" if kind == "call" else "arro"})"' if marker else ""
    out = [f'<path class="{kind}" d="{d}"{mk}/>']
    if label:
        if lpos is None:
            (ax, ay), (bx, by) = pts[0], pts[-1]
            lpos = ((ax + bx) / 2, (ay + by) / 2 - 5)
        out.append(text_block(lpos[0], lpos[1], label if isinstance(label, list) else [label], anchor=anchor, fs=10, lh=12.5))
    return "\n".join(out)

def diagram(prefix, W, H, body):
    assert W <= MAXW, W
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">' + defs(prefix) + "\n" + "\n".join(body) + "</svg>")

def seq_svg(prefix, lanes, msgs, lane_w=128, left=8):
    """lanes: [(kind, [label lines])] · msgs: (kind, frm, to, [lines]) kind ∈ call · ret · self"""
    n = len(lanes)
    xs = [left + lane_w / 2 + i * lane_w for i in range(n)]
    head_h = 56
    y = 16 + head_h + 24
    rows = []
    for kind, frm, to, lines in msgs:
        th = 13 * len(lines)
        if kind == "self":
            ay = y + 4
            rows.append((kind, frm, to, lines, ay)); y = ay + max(20, th) + 12
        else:
            ay = y + th + 2
            rows.append((kind, frm, to, lines, ay)); y = ay + 16
    H = y + 8
    W = left * 2 + lane_w * n
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">', defs(prefix)]
    for i, (kind, label) in enumerate(lanes):
        x = xs[i]
        out.append(f'<line class="lane" x1="{x}" y1="{16 + head_h}" x2="{x}" y2="{H - 4}"/>')
        out.append(node(x - lane_w / 2 + 4, 16, lane_w - 8, head_h, label, kind=kind, fs=10, lh=12.5))
    for kind, frm, to, lines, ay in rows:
        x1 = xs[frm]; x2 = xs[to] if to is not None else x1
        if kind == "self":
            if frm <= n // 2:
                out.append(f'<path class="call" d="M{x1},{ay} h16 v13 h-16" marker-end="url(#{prefix}-arr)"/>')
                out.append(text_block(x1 + 26, ay + 4, lines, anchor="start"))
            else:
                out.append(f'<path class="call" d="M{x1},{ay} h-16 v13 h16" marker-end="url(#{prefix}-arr)"/>')
                out.append(text_block(x1 - 26, ay + 4, lines, anchor="end"))
        else:
            cls = "call" if kind == "call" else "ret"
            mk = f"{prefix}-arr" if kind == "call" else f"{prefix}-arro"
            out.append(f'<line class="{cls}" x1="{x1}" y1="{ay}" x2="{x2}" y2="{ay}" marker-end="url(#{mk})"/>')
            mid = (x1 + x2) / 2
            half = (max(tw(l) for l in lines) + 10) / 2
            mid = min(max(mid, half + 2), W - half - 2)
            out.append(text_block(mid, ay - 5 - 13 * (len(lines) - 1), lines, anchor="middle", muted=(kind == "ret")))
    out.append("</svg>")
    assert W <= MAXW, W
    return "\n".join(out)

def tree_svg(prefix, rows, min_w=900):
    """rows: (depth, kind, name, desc_lines, group_id) — 텍스트 트리의 순서 · 들여쓰기를 상자 · 괘선 · 영역 테두리로 그린다."""
    b = []
    NAME_FS, DESC_FS = 11, 10.5
    INDENT, GAP, PAD = 34, 8, 10
    y = 14
    geo = []
    for depth, kind, name, desc, gid in rows:
        h = 30 if len(desc) == 1 else 44
        x = 16 + depth * INDENT
        w = PAD + tw(name, NAME_FS) * 1.06 + 14 + max(tw(d, DESC_FS) for d in desc) + PAD
        geo.append((x, y, w, h))
        y += h + GAP
    H = y + 6
    for i, (depth, kind, name, desc, gid) in enumerate(rows):
        if not gid: continue
        j = i + 1
        while j < len(rows) and rows[j][0] > depth: j += 1
        x0 = geo[i][0] - 8
        x1 = max(geo[k][0] + geo[k][2] for k in range(i, j)) + 10
        y0 = geo[i][1] - 7
        y1 = geo[j - 1][1] + geo[j - 1][3] + 8
        cls = "stage2" if gid.startswith("pm") else "stage"
        b.append(f'<rect class="{cls} grp" x="{x0}" y="{y0}" width="{x1 - x0:.0f}" height="{y1 - y0}" rx="8"/>')
    for i, (depth, *_rest) in enumerate(rows):
        kids = []
        j = i + 1
        while j < len(rows) and rows[j][0] > depth:
            if rows[j][0] == depth + 1: kids.append(j)
            j += 1
        if not kids: continue
        xp, yp, wp, hp = geo[i]
        spine_x = xp + 16
        last = kids[-1]
        b.append(f'<path class="tree" d="M{spine_x},{yp + hp} V{geo[last][1] + geo[last][3] / 2}"/>')
        for k in kids:
            xk, yk, wk, hk = geo[k]
            b.append(f'<path class="tree" d="M{spine_x},{yk + hk / 2} H{xk}"/>')
    for (depth, kind, name, desc, gid), (x, y0, w, h) in zip(rows, geo):
        b.append(f'<g class="n-{kind}"><rect x="{x}" y="{y0}" width="{w:.0f}" height="{h}" rx="5"/>')
        nx = x + PAD
        base = y0 + (h - 13 * len(desc)) / 2 + 11
        b.append(f'<text x="{nx:.1f}" y="{y0 + h / 2 + 4:.1f}" font-size="{NAME_FS}" font-weight="700">{esc(name)}</text>')
        dx = nx + tw(name, NAME_FS) * 1.06 + 14
        for li, d in enumerate(desc):
            b.append(f'<text x="{dx:.1f}" y="{base + li * 13:.1f}" font-size="{DESC_FS}" class="desc">{esc(d)}</text>')
        b.append("</g>")
    W = max(gx + gw for gx, gy, gw, gh in geo) + 24
    return diagram(prefix, max(W, min_w), H, b)

def wrap(svg, caption):
    return f'<figure class="dg">{svg}<figcaption>{caption}</figcaption></figure>'
