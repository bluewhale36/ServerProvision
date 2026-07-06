package com.example.serverprovision.provisioning.biossetting.controller;

import com.example.serverprovision.provisioning.biossetting.dto.request.BiosSettingTemplateCreateRequest;
import com.example.serverprovision.provisioning.biossetting.dto.request.BiosSettingTemplateUpdateRequest;
import com.example.serverprovision.provisioning.biossetting.dto.response.BiosSettingTemplateSummaryResponse;
import com.example.serverprovision.provisioning.biossetting.service.BiosSettingTemplateCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * BIOS 세팅 템플릿 XHR 엔드포인트. 형제 관례대로 SSR 과 같은 prefix 를 공유한다.
 */
@RestController
@RequestMapping("/provisioning/bios-setting")
@RequiredArgsConstructor
public class BiosSettingTemplateRestController {

    private final BiosSettingTemplateCommandService biosSettingTemplateCommandService;

    @PostMapping
    public ResponseEntity<BiosSettingTemplateSummaryResponse> create(
            @Valid @RequestBody BiosSettingTemplateCreateRequest request) {
        BiosSettingTemplateSummaryResponse body = biosSettingTemplateCommandService.create(request);
        return ResponseEntity
                .created(URI.create("/provisioning/bios-setting/" + body.id()))
                .body(body);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BiosSettingTemplateSummaryResponse> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody BiosSettingTemplateUpdateRequest request) {
        return ResponseEntity.ok(biosSettingTemplateCommandService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        biosSettingTemplateCommandService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
