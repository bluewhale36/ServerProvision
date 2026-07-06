package com.example.serverprovision.provisioning.setting.controller;

import com.example.serverprovision.provisioning.setting.dto.request.SettingSaveRequest;
import com.example.serverprovision.provisioning.setting.dto.response.PartitionPresetResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSaveResponse;
import com.example.serverprovision.provisioning.setting.service.SettingCommandService;
import com.example.serverprovision.provisioning.setting.service.SettingQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * 세팅 정의서 XHR 엔드포인트. 폼 JS 가 조립한 다형 JSON({@code type}/{@code osFamily} 판별)을 수신한다.
 * SSR 페이지는 {@link SettingController} 가 담당 — 형제 관례대로 같은 URL prefix 를 공유하고
 * {@code /api} 세그먼트는 두지 않는다.
 *
 * <p>{@code GET /default-partitions} 는 SSR 의 {@code GET /{id}} 와 prefix 를 공유하지만
 * Spring MVC 패턴 매칭에서 리터럴 세그먼트가 템플릿 변수보다 우선이라 정상 라우팅된다
 * (통합 테스트로 명시 검증).</p>
 */
@RestController
@RequestMapping("/provisioning/setting")
@RequiredArgsConstructor
public class SettingRestController {

    private final SettingCommandService settingCommandService;
    private final SettingQueryService settingQueryService;

    @PostMapping
    public ResponseEntity<SettingSaveResponse> create(@Valid @RequestBody SettingSaveRequest request) {
        SettingSaveResponse body = settingCommandService.create(request);
        return ResponseEntity
                .created(URI.create("/provisioning/setting/" + body.id()))
                .body(body);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SettingSaveResponse> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody SettingSaveRequest request
    ) {
        return ResponseEntity.ok(settingCommandService.update(id, request));
    }

    @GetMapping("/default-partitions")
    public ResponseEntity<List<PartitionPresetResponse>> defaultPartitions(
            @RequestParam("osName") String osName
    ) {
        return ResponseEntity.ok(settingQueryService.findDefaultPartitions(osName));
    }
}
