# proxy-lab

실기 스테이징의 nginx TLS 종단(`docs/staging-vm-bootstrap.md` §12)을 TLS 만 빼고 로컬 docker 로 재현하는 하네스다. 브라우저가 nginx 를 지날 때만 드러나는 결함(SSE 응답 버퍼링, S7-1)을 샌드박스에서 재현하고 정정을 검증하는 데 쓴다. mock-guest · pxe-lab 과 같은 지위의 git 추적 자산이며 Step 8 테스트 규율을 대체하지 않는다.

- `nginx.conf` — 런북 설정의 거울. 80 은 런북 그대로, 81 은 `proxy_buffering off` 대조군. 런북을 고치면 함께 고친다.
- `run.sh <앱 포트>` — `nginx:1.20` 컨테이너 `spv-proxy-lab` 을 세운다(기본 호스트 포트 7819 · 7820).

정정 여부는 스트림을 런북 경로로 직접 들어 보면 안다 — `curl -sN --max-time 10 http://localhost:7819/provisioning/server/stream` 에 `:connected` 가 곧바로 오면 앱이 `X-Accel-Buffering: no` 를 선언하고 있는 것이고, 10 초 동안 아무것도 오지 않으면 nginx 가 붙들고 있는 것이다.
