#!/usr/bin/env bash
# CP7 마무리 전 점검. 아무것도 바꾸지 않고 읽기만 한다(git fetch 제외).
# [STOP] = 진행 불가(종료 코드 1). [CHECK] = 세션이 파일을 직접 열어 판정해야 함(종료 코드 2).
# 비밀값 의심 줄은 값이 아니라 파일:행만 찍는다 — 값이 대화 · 로그에 남지 않게.
set -uo pipefail

stop=0
check=0
say()  { printf '%s\n' "$*"; }
halt() { printf '[STOP] %s\n' "$*"; stop=1; }
warn() { printf '[WARN] %s\n' "$*"; }

top=$(git rev-parse --show-toplevel 2>/dev/null) || { halt "git 저장소가 아닙니다."; exit 1; }
cd "$top"
branch=$(git branch --show-current)

say "== 위치"
say "워크트리: $top"
say "브랜치:   ${branch:-'(detached HEAD)'}"

# 앵커는 .git 실체를 가진 워크트리다(git-dir 와 common-dir 가 같다).
if [ "$(git rev-parse --git-dir)" = "$(git rev-parse --git-common-dir)" ]; then
  halt "앵커 워크트리(ServerProvision-dev)입니다. 이 skill 은 다른 스트림 세션용입니다."
fi
case "$branch" in
  ""|dev|main) halt "작업 브랜치가 아닙니다($branch). dev · main 에는 직접 커밋 · push 하지 않습니다." ;;
  refine/*)    halt "refine/* 는 로컬 전용 브랜치라 push 하지 않습니다." ;;
esac
if [ -n "$branch" ] && ! printf '%s' "$branch" | grep -Eq '^(feat|fix|refactor|docs|chore)/[A-Za-z0-9.-]+_[a-z0-9.-]+$'; then
  warn "브랜치 이름이 <type>/<인벤토리코드>_<슬러그> 꼴이 아닙니다: $branch"
fi

say ""
say "== origin/dev 대비"
git fetch -q origin dev 2>/dev/null || warn "origin/dev 를 가져오지 못했습니다(네트워크)."
if git rev-parse -q --verify origin/dev >/dev/null; then
  read -r behind ahead < <(git rev-list --left-right --count origin/dev...HEAD)
  say "앞선 커밋 $ahead 개 · 뒤처진 커밋 $behind 개"
  [ "$behind" -gt 0 ] && warn "origin/dev 가 $behind 커밋 앞서 있습니다. 병합은 앵커 몫이므로 PR 본문과 통보에 적습니다."
  base=$(git merge-base origin/dev HEAD)
else
  base=HEAD
fi

say ""
say "== 작업 트리"
git status --short --untracked-files=all | sed 's/^/  /'
risky=$(git status --short --untracked-files=all | awk '{print $NF}' \
  | grep -E '(^|/)(sbx-[^/]*|shots[^/]*|term|\.claude/worktrees|\.claude/tools/__pycache__)/|\.(log|jar|env)$|(^|/)env$|CLAUDE\.local\.md$' || true)
[ -n "$risky" ] && { warn "커밋하면 안 되는 자리로 보이는 파일:"; printf '  %s\n' $risky; }

say ""
say "== 기존 PR"
if [ -n "$branch" ] && command -v gh >/dev/null; then
  gh pr list --head "$branch" --state all --json number,state,title,url \
    -q '.[] | "  #\(.number) [\(.state)] \(.title) \(.url)"' 2>/dev/null || warn "gh 조회 실패"
fi

say ""
say "== 비밀값 의심 줄(추가된 줄만, 값은 출력하지 않음)"
pat='(password|passwd|pwd|secret|token|api[_-]?key|product[_-]?key|BMC_PASS|DB_PASSWORD)[[:space:]]*[:=][[:space:]]*[^[:space:]$<{]'
key='-----BEGIN [A-Z ]*PRIVATE KEY-----|[A-Z0-9]{5}(-[A-Z0-9]{5}){4}'
hits=$( { git diff "$base" --unified=0 --no-color; \
          git ls-files --others --exclude-standard -z | xargs -0 -I{} git diff --no-index --unified=0 --no-color /dev/null {} 2>/dev/null; } \
  | awk -v pat="$pat" -v key="$key" '
      /^\+\+\+ / { f=substr($0,7); next }
      /^@@/      { split($3,a,/[+,]/); n=a[2]-1; next }
      /^\+/      { n++; line=tolower($0)
                   if (line ~ pat || $0 ~ key) { c[f]++; if (c[f] <= 3) l[f] = l[f] (c[f]>1 ? "," : "") n } }
      END { for (f in c) printf "  %s:%s%s\n", f, l[f], (c[f] > 3 ? " 외 " c[f]-3 "줄" : "") }
    ' | sort)
if [ -n "$hits" ]; then
  printf '[CHECK] %s\n' "비밀값으로 보이는 추가 줄입니다. 커밋할 파일이면 직접 열어 실제 값인지 판정합니다:"; check=1
  printf '%s\n' "$hits"
else
  say "  없음"
fi

say ""
if [ $stop -ne 0 ]; then say "결과: 중단 사유 있음"; exit 1; fi
if [ $check -ne 0 ]; then say "결과: 판정 필요 항목 있음"; exit 2; fi
say "결과: 통과"
exit 0
