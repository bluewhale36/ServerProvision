package com.example.serverprovision.execution.engine.setting.step;

import com.example.serverprovision.execution.engine.worker.WorkerStepRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** 설정 행 수집 — 정렬 · 중복 fail-fast 는 공통 registry 가, 여기는 타입과 로그 태그만. */
@Component
public class SettingStepRegistry {

    private final WorkerStepRegistry<SettingContext, SettingStep> delegate;

    public SettingStepRegistry(List<SettingStep> steps) {
        this.delegate = new WorkerStepRegistry<>("setting", steps);
    }

    public Optional<SettingStep> firstMatching(SettingContext context) {
        return delegate.firstMatching(context);
    }
}
