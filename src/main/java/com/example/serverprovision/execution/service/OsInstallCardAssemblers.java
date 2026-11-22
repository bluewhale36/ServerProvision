package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.engine.osinstall.OsInstallOrigin;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 계열별 카드 조립기를 origin 으로 찾는다(E4-1-b-4-2). 한 계열에 조립기가 둘이면 어느 쪽이 그려질지 모르므로 기동을 멈춘다.
 * 카드 계열이 없거나(맡을 설치기 없음) 조립기가 없는 계열이면 "지원하지 않는 계열" 내용을 돌려준다.
 */
@Component
public class OsInstallCardAssemblers {

    private final Map<OsInstallOrigin, OsInstallCardAssembler> byOrigin = new EnumMap<>(OsInstallOrigin.class);

    public OsInstallCardAssemblers(List<OsInstallCardAssembler> assemblers) {
        assemblers.forEach(assembler -> {
            if (byOrigin.put(assembler.origin(), assembler) != null) {
                throw new IllegalStateException("OS 설치 카드 조립기가 한 계열에 둘이다 : " + assembler.origin());
            }
        });
    }

    public GuestServerDetailResponse.OsInstallCardDetail detailOf(Optional<OsInstallOrigin> origin,
                                                                  OsInstallCardAssembler.Context context) {
        return origin.map(byOrigin::get)
                .<GuestServerDetailResponse.OsInstallCardDetail>map(assembler -> assembler.assemble(context))
                .orElseGet(GuestServerDetailResponse.UnsupportedOsInstall::new);
    }
}
