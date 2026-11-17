#!/usr/bin/env bash
# 새 단계 시작 전 브랜치 점검. 읽기만 한다(git fetch 제외).
# 사용: branch-check.sh [만들 브랜치 이름]
# [STOP] = 브랜치를 바꾸면 안 됨(종료 코드 1). 통과는 0.
set -uo pipefail

stop=0
say()  { printf '%s\n' "$*"; }
halt() { printf '[STOP] %s\n' "$*"; stop=1; }
warn() { printf '[WARN] %s\n' "$*"; }

target="${1:-}"
top=$(git rev-parse --show-toplevel 2>/dev/null) || { halt "git 저장소가 아닙니다."; exit 1; }
cd "$top"
current=$(git branch --show-current)

say "== 위치"
say "워크트리: $top"
if [ "$(git rev-parse --git-dir)" = "$(git rev-parse --git-common-dir)" ]; then
  say "역할:     앵커(끝나면 dev 로 돌아온다)"
else
  say "역할:     스트림"
fi
say "현재:     ${current:-'(detached HEAD)'}"

say ""
say "== 작업 트리"
# 추적 파일 변경만 막는다. 미추적 파일은 브랜치를 바꿔도 따라오므로 목록만 보인다.
dirty=$(git status --porcelain --untracked-files=no)
if [ -n "$dirty" ]; then
  halt "커밋하지 않은 추적 파일 변경이 있습니다. 브랜치를 바꾸면 다른 슬라이스 작업이 섞입니다:"
  printf '%s\n' "$dirty" | head -10 | sed 's/^/  /'
  n=$(printf '%s\n' "$dirty" | wc -l | tr -d ' '); [ "$n" -gt 10 ] && say "  … 외 $((n-10)) 개"
else
  say "  추적 파일 변경 없음"
fi
untracked=$(git status --porcelain --untracked-files=normal | grep '^??' | wc -l | tr -d ' ')
[ "$untracked" -gt 0 ] && say "  미추적 항목 $untracked 개(브랜치를 바꿔도 남는다)"

# 지금 브랜치가 아직 병합되지 않은 작업을 들고 있으면 알린다.
if [ -n "$current" ] && [ "$current" != dev ] && [ "$current" != main ]; then
  if command -v gh >/dev/null; then
    pr=$(gh pr list --head "$current" --state all --json number,state -q '.[0] // empty | "#\(.number) \(.state)"' 2>/dev/null)
  fi
  unpushed=$(git rev-list --count "@{u}..HEAD" 2>/dev/null || echo "?")
  say "  현재 브랜치 PR: ${pr:-없음} · push 안 한 커밋: $unpushed"
  case "${pr:-}" in
    *MERGED*) ;;
    *) warn "현재 브랜치가 병합 전입니다. 떠나도 되는지 사용자에게 확인합니다." ;;
  esac
fi

say ""
say "== origin/dev"
if git fetch -q origin dev 2>/dev/null; then
  say "  $(git log -1 --format='%h %s' origin/dev)"
else
  halt "origin/dev 를 가져오지 못했습니다. 낡은 기준으로 브랜치를 만들지 않습니다."
fi

if [ -n "$target" ]; then
  say ""
  say "== 만들 브랜치: $target"
  if ! printf '%s' "$target" | grep -Eq '^(feat|fix|refactor|docs|chore)/[A-Za-z0-9.-]+_[a-z0-9.-]+$'; then
    halt "이름이 <type>/<인벤토리코드>_<슬러그> 꼴이 아닙니다. 슬러그는 영문 소문자 · 숫자 · 하이픈입니다."
  fi
  if git show-ref -q --verify "refs/heads/$target"; then
    warn "같은 이름의 로컬 브랜치가 이미 있습니다. 새로 만들지 말고 이어 쓸지 확인합니다."
  fi
  if git ls-remote -q --exit-code --heads origin "$target" >/dev/null 2>&1; then
    warn "같은 이름의 원격 브랜치가 이미 있습니다."
  fi
  # 다른 워크트리가 이미 체크아웃한 브랜치는 여기서 쓸 수 없다.
  if git worktree list --porcelain | grep -qx "branch refs/heads/$target"; then
    halt "다른 워크트리가 이 브랜치를 체크아웃하고 있습니다."
  fi
fi

say ""
if [ $stop -ne 0 ]; then say "결과: 중단 사유 있음"; exit 1; fi
say "결과: 통과"
exit 0
