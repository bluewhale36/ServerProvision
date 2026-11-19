#!/usr/bin/env bash
# plan, report html 을 Notion 파일 업로드 URL 로 보내고 받은 크기를 원본과 대조한다.
# 사용: notion-upload.sh <upload_url> <authorization 헤더 값> <파일 경로>
#   upload_url 과 authorization 값은 notion-create-file-upload 의 반환값을 그대로 넘긴다.
# 성공하면 마지막 줄에 'EMBED <embed 태그>' 를 출력하고 0 으로 끝난다.
# 크기가 다르거나 응답이 200 이 아니면 그 사유를 출력하고 1 로 끝난다. 이때는 embed 하지 않는다.
set -euo pipefail

if [ $# -ne 3 ]; then
  echo "사용: $0 <upload_url> <authorization> <파일>" >&2
  exit 1
fi
url=$1
auth=$2
file=$3

[ -f "$file" ] || { echo "[STOP] 파일이 없다: $file" >&2; exit 1; }

case "$file" in
  *.html) type=text/html ;;
  *.md) type=text/markdown ;;
  *) type=application/octet-stream ;;
esac

expected=$(wc -c < "$file" | tr -d ' ')
body=$(mktemp)
trap 'rm -f "$body"' EXIT

status=$(curl -s -o "$body" -w '%{http_code}' -X POST "$url" \
  -H "authorization: $auth" \
  -F "file=@${file};type=${type}")

if [ "$status" != "200" ]; then
  echo "[STOP] 업로드 응답 $status" >&2
  head -c 500 "$body" >&2
  echo >&2
  exit 1
fi

python3 - "$body" "$expected" <<'PY'
import json, sys
data = json.load(open(sys.argv[1]))
expected = int(sys.argv[2])
got = data.get("content_length")
if data.get("status") != "uploaded" or got != expected:
    print(f"[STOP] 상태 {data.get('status')}, 받은 크기 {got}, 원본 {expected}", file=sys.stderr)
    sys.exit(1)
print(f"[OK] {data['filename']} {got} 바이트 일치")
print(f"EMBED <embed source=\"{data['markdown_source']}\" />")
PY
