package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.engine.osinstall.OsInstallOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** E4-1-b-4-2 M-1 — 카드 계열 내용은 origin 의 조립기가 만든다. 계열 없음 · 조립기 없음은 미지원 카드, 한 계열에 둘이면 기동 실패. */
class OsInstallCardAssemblersTest {

    private static final OsInstallCardAssembler.Context CONTEXT =
            new OsInstallCardAssembler.Context(UUID.randomUUID(), Optional.empty(), Optional.empty(), Optional.empty());

    private static OsInstallCardAssembler assembler(OsInstallOrigin origin, GuestServerDetailResponse.OsInstallCardDetail detail) {
        OsInstallCardAssembler assembler = mock(OsInstallCardAssembler.class);
        given(assembler.origin()).willReturn(origin);
        given(assembler.assemble(any())).willReturn(detail);
        return assembler;
    }

    @Test
    @DisplayName("origin 의 조립기 결과를 돌려주고, 그 조각 이름이 카드를 정한다")
    void delegatesByOrigin() {
        var rhel = new GuestServerDetailResponse.RhelInstall("Rocky Linux 9.5", null, null);
        var assemblers = new OsInstallCardAssemblers(List.of(
                assembler(OsInstallOrigin.WINDOWS_INSTALL, mock(GuestServerDetailResponse.WindowsInstall.class)),
                assembler(OsInstallOrigin.RHEL_INSTALL, rhel)));

        var detail = assemblers.detailOf(Optional.of(OsInstallOrigin.RHEL_INSTALL), CONTEXT);

        assertThat(detail).isSameAs(rhel);
        assertThat(detail.fragment()).isEqualTo("rhelCard");
    }

    @Test
    @DisplayName("카드 계열이 없거나(맡을 설치기 없음) 조립기가 없는 계열이면 미지원 카드")
    void unsupportedWhenAbsent() {
        var assemblers = new OsInstallCardAssemblers(List.of());

        assertThat(assemblers.detailOf(Optional.empty(), CONTEXT).fragment()).isEqualTo("unsupportedCard");
        assertThat(assemblers.detailOf(Optional.of(OsInstallOrigin.RHEL_INSTALL), CONTEXT))
                .isInstanceOf(GuestServerDetailResponse.UnsupportedOsInstall.class);
    }

    @Test
    @DisplayName("한 계열에 조립기가 둘이면 생성에서 실패한다")
    void duplicateFailsFast() {
        assertThatThrownBy(() -> new OsInstallCardAssemblers(List.of(
                assembler(OsInstallOrigin.RHEL_INSTALL, null), assembler(OsInstallOrigin.RHEL_INSTALL, null))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("RHEL_INSTALL");
    }
}
