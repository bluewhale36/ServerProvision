package com.example.serverprovision.execution.engine.setting;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 보드별 Fan Profile 자원(E3-2 D-6) — {@code classpath:bmc/fanprofile/<보드 모델명>.json}. 정본은 E0-3 이 채집한
 * 단일행 JSON(Notion 복사본은 탭 · NBSP 오염으로 1010 을 냈던 실측)이라 파일을 그대로 싣는다. 저장소가 SSOT 이고
 * 보드 추가는 커밋이다 — 운영자 교체 요구가 생기면 그때 외부 override 를 연다(미리 분리 금지).
 */
@Component
@RequiredArgsConstructor
public class FanProfileResources {

    static final String LOCATION = "bmc/fanprofile/";

    private final ObjectMapper objectMapper;
    private final Map<String, Optional<FanProfile>> cache = new ConcurrentHashMap<>();

    /** 보드 모델명(카탈로그 {@code BoardModel.modelName})의 프로파일 — 없으면 empty(항목 SKIPPED 의 근거). */
    public Optional<FanProfile> forBoard(String boardModelName) {
        if (boardModelName == null || boardModelName.isBlank()) {
            return Optional.empty();
        }
        return cache.computeIfAbsent(boardModelName, this::load);
    }

    private Optional<FanProfile> load(String boardModelName) {
        ClassPathResource resource = new ClassPathResource(LOCATION + boardModelName + ".json");
        if (!resource.exists()) {
            return Optional.empty();
        }
        try {
            JsonNode body = objectMapper.readTree(resource.getContentAsString(StandardCharsets.UTF_8));
            return Optional.of(new FanProfile(boardModelName, body, body.path("strMode").asString()));
        } catch (IOException e) {
            throw new UncheckedIOException("Fan Profile 자원을 읽지 못했습니다 : " + resource.getPath(), e);
        }
    }

    /** 전송 전문(JSON 그대로)과 readback 기준({@code strMode}). */
    public record FanProfile(String boardModelName, JsonNode body, String mode) {
    }
}
