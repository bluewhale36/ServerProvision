/**
 * MK2 WAVE 3 — ISO 파일의 정식 SHA-256 fingerprint 를 Web Worker 에서 streaming 으로 계산.
 *
 * 입력 (main thread):
 *   worker.postMessage({ requestId, file })
 *
 * 출력 (worker → main):
 *   { requestId, progress: 0.0~1.0, processedBytes, totalBytes }   — 진행 보고 (~250ms 간격)
 *   { requestId, fingerprint: "<64-hex>", totalBytes }              — 완료
 *   { requestId, error: "<message>" }                               — 실패
 *
 * Web Crypto API (subtle.digest) 는 streaming 미지원 → 외부 hash-wasm CDN 사용.
 * hash-wasm 의 createSHA256() 는 chunked update + 최종 hex digest 지원 → 12GB 도 OOM 없이 처리.
 */

importScripts('https://cdn.jsdelivr.net/npm/hash-wasm@4.11.0/dist/sha256.umd.min.js');

const CHUNK_BYTES = 4 * 1024 * 1024; // 4MB — 메모리/속도 균형
const PROGRESS_REPORT_INTERVAL_MS = 250;

self.addEventListener('message', async (event) => {
    const data = event.data || {};
    const requestId = data.requestId;
    const file = data.file;

    if (!file) {
        self.postMessage({requestId, error: '파일이 전달되지 않았습니다.'});
        return;
    }
    if (typeof self.hashwasm === 'undefined' || !self.hashwasm.createSHA256) {
        self.postMessage({requestId, error: 'hash-wasm 라이브러리 로드 실패. 네트워크 / CDN 확인.'});
        return;
    }

    try {
        const hasher = await self.hashwasm.createSHA256();
        const total = file.size;
        let processed = 0;
        let lastReport = Date.now();

        // 진행 0% 시작 보고 (UI 가 시간 표기 시작)
        self.postMessage({requestId, progress: 0, processedBytes: 0, totalBytes: total});

        for (let offset = 0; offset < total; offset += CHUNK_BYTES) {
            const end = Math.min(offset + CHUNK_BYTES, total);
            const slice = file.slice(offset, end);
            const buf = await slice.arrayBuffer();
            hasher.update(new Uint8Array(buf));
            processed = end;

            const now = Date.now();
            if (now - lastReport >= PROGRESS_REPORT_INTERVAL_MS || processed === total) {
                self.postMessage({
                    requestId,
                    progress: total > 0 ? processed / total : 1,
                    processedBytes: processed,
                    totalBytes: total
                });
                lastReport = now;
            }
        }

        const fingerprint = hasher.digest('hex');
        self.postMessage({requestId, fingerprint, totalBytes: total});
    } catch (err) {
        self.postMessage({requestId, error: (err && err.message) || String(err)});
    }
});
