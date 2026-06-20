package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

/**
 * R4-2 — BiosMarkerWriter 의 marker 4-step (조립 → 서명 → reissue → write) 위임이
 * 추출 전과 동일한 서명 대상 바이트·기록 순서를 보존하는지 검증한다.
 *
 * <p>ProvisionMarkerService 는 mock. computeSignature 가 결정적 문자열을 반환하도록 stub 해
 * write 로 넘어간 signed MarkerContent 와 엔티티 reissueMarker 인자를 ArgumentCaptor 로 확인한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class BiosMarkerWriterTest {

    @Mock
    ProvisionMarkerService provisionMarkerService;

    @InjectMocks
    BiosMarkerWriter biosMarkerWriter;

    private static final String SIG = "sig-xyz";

    @Test
    @DisplayName("writeSignedMarker(happy) : unsigned 조립 → computeSignature → reissueMarker → signed write 순서·필드 보존")
    void writeSignedMarker_happy() {
        BoardBIOS bios = org.mockito.Mockito.mock(BoardBIOS.class);
        given(bios.getId()).willReturn(77L);
        given(provisionMarkerService.computeSignature(any(MarkerContent.class))).willReturn(SIG);

        Path targetDir = Path.of("/tmp/bios/77");
        biosMarkerWriter.writeSignedMarker(bios, targetDir, 10L, "1.0", "X99E-WS.CAP", "abc123");

        // (a) computeSignature 에 넘어간 unsigned MarkerContent 검증
        ArgumentCaptor<MarkerContent> unsignedCaptor = ArgumentCaptor.forClass(MarkerContent.class);
        verify(provisionMarkerService).computeSignature(unsignedCaptor.capture());
        MarkerContent unsigned = unsignedCaptor.getValue();
        assertThat(unsigned.resourceType()).isEqualTo(ResourceType.BIOS_BUNDLE.name());
        assertThat(unsigned.resourceId()).isEqualTo(77L);
        assertThat(unsigned.attributes())
                .containsEntry("boardId", "10")
                .containsEntry("version", "1.0")
                .containsEntry("entrypointRelativePath", "X99E-WS.CAP")
                .hasSize(3);
        assertThat(unsigned.manifestHash()).isEqualTo("abc123");
        assertThat(unsigned.signature()).isNull();

        // (b) 엔티티 reissueMarker(manifestHash, signature) 1회 호출
        verify(bios).reissueMarker("abc123", SIG);

        // (c) write 로 넘어간 signed MarkerContent — signature 만 채워지고 나머지는 unsigned 와 동일
        ArgumentCaptor<MarkerContent> signedCaptor = ArgumentCaptor.forClass(MarkerContent.class);
        verify(provisionMarkerService).write(org.mockito.ArgumentMatchers.eq(targetDir),
                org.mockito.ArgumentMatchers.eq(MarkerLayout.IN_TREE), signedCaptor.capture());
        MarkerContent signed = signedCaptor.getValue();
        assertThat(signed.signature()).isEqualTo(SIG);
        assertThat(signed.resourceType()).isEqualTo(unsigned.resourceType());
        assertThat(signed.resourceId()).isEqualTo(unsigned.resourceId());
        assertThat(signed.attributes()).isEqualTo(unsigned.attributes());
        assertThat(signed.createdAt()).isEqualTo(unsigned.createdAt());
        assertThat(signed.manifestHash()).isEqualTo(unsigned.manifestHash());

        // (d) 호출 순서 : computeSignature → reissueMarker → write
        InOrder order = inOrder(provisionMarkerService, bios);
        order.verify(provisionMarkerService).computeSignature(any(MarkerContent.class));
        order.verify(bios).reissueMarker("abc123", SIG);
        order.verify(provisionMarkerService).write(any(), any(), any());
    }

    @Test
    @DisplayName("writeSignedMarker(경계) : entrypoint 빈 문자열도 attributes 에 그대로 보존된다")
    void writeSignedMarker_emptyEntrypoint() {
        BoardBIOS bios = org.mockito.Mockito.mock(BoardBIOS.class);
        given(bios.getId()).willReturn(5L);
        given(provisionMarkerService.computeSignature(any(MarkerContent.class))).willReturn(SIG);

        biosMarkerWriter.writeSignedMarker(bios, Path.of("/tmp/bios/5"), 1L, "2.0", "", "hash");

        ArgumentCaptor<MarkerContent> unsignedCaptor = ArgumentCaptor.forClass(MarkerContent.class);
        verify(provisionMarkerService).computeSignature(unsignedCaptor.capture());
        // 빈 entrypoint 가 누락·치환 없이 그대로 들어가는지 — Map.of 가 빈 값을 거부하지 않음을 회귀 고정
        assertThat(unsignedCaptor.getValue().attributes()).containsEntry("entrypointRelativePath", "");
        verify(bios).reissueMarker("hash", SIG);
    }
}
