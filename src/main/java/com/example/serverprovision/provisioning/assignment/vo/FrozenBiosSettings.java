package com.example.serverprovision.provisioning.assignment.vo;

import com.example.serverprovision.provisioning.biossetting.vo.BiosSettingValues;

import java.util.List;

/**
 * BASIC_SETTING 단계의 <b>deep-freeze 스냅샷</b>(결정 D-C) — 할당 시점에 참조 BIOS 세팅 템플릿을 resolve 해
 * 값을 인라인 복사한다.
 *
 * <p>근거: BIOS 세팅 템플릿은 U2 에서 편집 가능하므로 얕은 참조(templateId)만 두면 소비 스냅샷의 재현성이
 * 소급 drift 한다. 각 템플릿의 {@code id} · 소속 {@code boardModel id} · {@link BiosSettingValues} 를 복사해
 * 동결하면 스냅샷이 자족(템플릿 FK 없음)하고, 이후 템플릿 편집/삭제와 무관하게 이력이 불변이다.</p>
 */
public record FrozenBiosSettings(List<FrozenBiosTemplate> templates) {

    public FrozenBiosSettings {
        if (templates == null || templates.isEmpty()) {
            throw new IllegalArgumentException("동결할 BIOS 세팅 템플릿이 최소 1개 있어야 합니다.");
        }
        templates = List.copyOf(templates);
    }

    /** 동결된 단일 템플릿 — {@code boardModelId} 는 실행 시 감지 보드 일치 판정에 쓰인다(E2). */
    public record FrozenBiosTemplate(Long templateId, Long boardModelId, BiosSettingValues values) {

        public FrozenBiosTemplate {
            if (templateId == null || boardModelId == null || values == null) {
                throw new IllegalArgumentException("동결 BIOS 템플릿 필드는 null 일 수 없습니다.");
            }
        }
    }
}
