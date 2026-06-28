package com.example.serverprovision.execution.controller;

import com.example.serverprovision.execution.dto.BootIPXEInfoRequest;
import com.example.serverprovision.execution.service.GuestServerRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/pxe/v1")
@RequiredArgsConstructor
public class ExecutionRestController {

    private final GuestServerRegistrationService registrationService;

    @GetMapping("/boot")
    public ResponseEntity<?> initialBoot(@ModelAttribute BootIPXEInfoRequest initialRequest) {
        log.info("신규 서버 등록 요청 : info={}", initialRequest.toString());
        registrationService.initialRegistry(initialRequest);
        return ResponseEntity.ok(null);
    }
}
