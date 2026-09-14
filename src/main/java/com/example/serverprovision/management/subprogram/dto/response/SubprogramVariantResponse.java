package com.example.serverprovision.management.subprogram.dto.response;

import com.example.serverprovision.management.subprogram.entity.SubprogramVariant;
import com.example.serverprovision.management.subprogram.enums.InstallEntrypointKind;

public record SubprogramVariantResponse(
        Long id,
        String osVersion,
        String entrypointRelativePath,
        InstallEntrypointKind entrypointKind,
        String arguments,
        boolean rebootRequired
) {
    public static SubprogramVariantResponse of(SubprogramVariant v) {
        return new SubprogramVariantResponse(v.getId(), v.getOsVersion(), v.getEntrypointRelativePath(),
                v.entrypointKind(), v.getArguments(), v.isRebootRequired());
    }
}
