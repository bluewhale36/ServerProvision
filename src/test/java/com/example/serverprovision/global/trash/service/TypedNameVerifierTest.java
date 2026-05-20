package com.example.serverprovision.global.trash.service;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.service.internal.TypedNameVerifierImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * S5-2-4 — TypedNameVerifier 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TypedNameVerifierTest {

    @Mock MarkableScanner isoScanner;
    @Mock Markable markable;

    @Test
    @DisplayName("(11) 활성 자원 일치 — 통과")
    void verify_activeMatching() {
        given(isoScanner.supportedType()).willReturn(ResourceType.OS_ISO);
        given(isoScanner.findActiveMarkableById(27L)).willReturn(Optional.of(markable));
        given(markable.displayName()).willReturn("Rocky Linux 9.5 dvd.iso");
        TypedNameVerifierImpl verifier = new TypedNameVerifierImpl(List.of(isoScanner));

        assertThatCode(() -> verifier.verify(ResourceType.OS_ISO, 27L, "Rocky Linux 9.5 dvd.iso"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("(12) 휴지통 자원 일치 — 활성에 없으면 휴지통 lookup 후 통과")
    void verify_trashedFallback() {
        given(isoScanner.supportedType()).willReturn(ResourceType.OS_ISO);
        given(isoScanner.findActiveMarkableById(27L)).willReturn(Optional.empty());
        given(isoScanner.findTrashedById(27L)).willReturn(Optional.of(markable));
        given(markable.displayName()).willReturn("Rocky Linux 9.5 dvd.iso");
        TypedNameVerifierImpl verifier = new TypedNameVerifierImpl(List.of(isoScanner));

        assertThatCode(() -> verifier.verify(ResourceType.OS_ISO, 27L, "Rocky Linux 9.5 dvd.iso"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("(13) 자원 부재 — TypedNameMismatchException")
    void verify_notFound() {
        given(isoScanner.supportedType()).willReturn(ResourceType.OS_ISO);
        given(isoScanner.findActiveMarkableById(99L)).willReturn(Optional.empty());
        given(isoScanner.findTrashedById(99L)).willReturn(Optional.empty());
        TypedNameVerifierImpl verifier = new TypedNameVerifierImpl(List.of(isoScanner));

        assertThatThrownBy(() -> verifier.verify(ResourceType.OS_ISO, 99L, "anything"))
                .isInstanceOf(TypedNameMismatchException.class);
    }

    @Test
    @DisplayName("(14) 입력 불일치 — TypedNameMismatchException")
    void verify_mismatch() {
        given(isoScanner.supportedType()).willReturn(ResourceType.OS_ISO);
        given(isoScanner.findActiveMarkableById(27L)).willReturn(Optional.of(markable));
        given(markable.displayName()).willReturn("Rocky Linux 9.5 dvd.iso");
        TypedNameVerifierImpl verifier = new TypedNameVerifierImpl(List.of(isoScanner));

        assertThatThrownBy(() -> verifier.verify(ResourceType.OS_ISO, 27L, "Rocky Linux 9.5 typo.iso"))
                .isInstanceOf(TypedNameMismatchException.class);
    }
}
