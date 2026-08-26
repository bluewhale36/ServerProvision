package com.example.serverprovision.global.bmcweb;

import tools.jackson.databind.JsonNode;

/**
 * 세션이 묶인 AMI 웹 API 호출면(E3-2 D-3) — 항목({@code BmcSettingItem})은 이것만 보고 쓰고 읽는다.
 * 세션 갱신 · 헤더 부착 · 바디 판독은 {@link AmiWebClient} 가 뒤에서 하므로 항목 코드에는 인증이 보이지 않는다.
 */
public interface AmiWebApi {

    JsonNode get(String path);

    JsonNode put(String path, Object body);

    JsonNode post(String path, Object body);
}
