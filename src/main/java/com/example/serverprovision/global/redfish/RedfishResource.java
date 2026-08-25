package com.example.serverprovision.global.redfish;

import tools.jackson.databind.JsonNode;

/** GET 응답 한 벌 — 본문과 ETag. ETag 는 후속 PATCH 의 If-Match 선행 조건이다(fresh 필수 · 재사용 시 412 — E0-4-1 실측). */
public record RedfishResource(JsonNode body, String etag) {
}
