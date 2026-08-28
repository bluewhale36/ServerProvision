package com.example.serverprovision.provisioning.biossetting.service;

import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.provisioning.biossetting.entity.BiosRegistrySnapshot;
import com.example.serverprovision.provisioning.biossetting.enums.BiosRegistrySource;
import com.example.serverprovision.provisioning.biossetting.repository.BiosRegistrySnapshotRepository;
import com.example.serverprovision.provisioning.biossetting.vo.ResolvedBiosRegistry;
import com.example.serverprovision.provisioning.config.BiosResourceProperties;
import com.example.serverprovision.provisioning.domain.BiosSetupMenu;
import com.example.serverprovision.provisioning.service.BiosSetupLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * E3-3 R2 · D-3 — "이 보드의 레지스트리" 해석 순서: 굽기 목표 버전 채집본 → 최신 채집본 → 자료 파일.
 * 목표 버전 술어는 {@code BoardBiosCatalog.latestEnabled}(순위 1위 활성)와 같다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BiosRegistryResolverTest {

    private static final String KEY = "MD72-HB3";
    private static final LocalDateTime AT = LocalDateTime.of(2026, 8, 27, 15, 31);

    @Mock BiosRegistrySnapshotRepository snapshotRepository;
    @Mock BiosRepository biosRepository;
    @Mock BiosSetupLoader loader;

    private final BiosResourceProperties properties = new BiosResourceProperties("redfish_materials",
            List.of(new BiosResourceProperties.Board(KEY, "r.json", "s.xml")));
    private final BiosSetupMenu fileMenu = new BiosSetupMenu(KEY, List.of(), Map.of(), Map.of(), List.of());
    private final BiosSetupMenu snapshotMenu = new BiosSetupMenu(KEY, List.of(), Map.of(), Map.of(), List.of());

    private BiosRegistryResolver resolver() {
        return new BiosRegistryResolver(snapshotRepository, biosRepository, properties, loader);
    }

    private static BoardModel board() {
        BoardModel board = mock(BoardModel.class);
        given(board.getId()).willReturn(5L);
        given(board.getModelName()).willReturn(KEY);
        return board;
    }

    private static BoardBIOS bios(String version, boolean enabled) {
        BoardBIOS bios = mock(BoardBIOS.class);
        given(bios.getVersion()).willReturn(version);
        given(bios.isEnabled()).willReturn(enabled);
        given(bios.isDeleted()).willReturn(false);
        return bios;
    }

    private static BiosRegistrySnapshot snapshot(long id, String version) {
        BiosRegistrySnapshot s = mock(BiosRegistrySnapshot.class);
        given(s.getId()).willReturn(id);
        given(s.getBiosVersion()).willReturn(version);
        given(s.getCapturedAt()).willReturn(AT);
        given(s.getSourceBmcIp()).willReturn("192.168.1.130");
        given(s.getRegistryJson()).willReturn("{}");
        return s;
    }

    @Test
    @DisplayName("목표 버전 채집본이 있으면 그것 — 배지에 버전 · 채집일 · BMC 가 실린다")
    void targetSnapshot_wins() {
        List<BoardBIOS> ranked = List.of(bios("F45", false), bios("F44", true), bios("F33", true));   // 1위는 비활성 → F44 가 목표
        BiosRegistrySnapshot f44 = snapshot(9L, "F44");
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionRankAsc(5L)).willReturn(ranked);
        given(snapshotRepository.findByBoardModel_IdAndBiosVersion(5L, "F44")).willReturn(Optional.of(f44));
        given(loader.load(eq(KEY), eq(9L), any())).willReturn(snapshotMenu);

        ResolvedBiosRegistry resolved = resolver().resolve(board());

        assertThat(resolved.source()).isEqualTo(BiosRegistrySource.SNAPSHOT_TARGET);
        assertThat(resolved.menu()).isSameAs(snapshotMenu);
        assertThat(resolved.label()).isEqualTo("F44 · 2026-08-27 채집 · 192.168.1.130");
        verify(loader, never()).load(KEY);
    }

    @Test
    @DisplayName("목표 버전이 미채집이면 보드의 최신 채집본 — 배지가 목표 미채집을 알린다")
    void latestSnapshot_fallback() {
        List<BoardBIOS> ranked = List.of(bios("F45", true));
        BiosRegistrySnapshot f44 = snapshot(9L, "F44");
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionRankAsc(5L)).willReturn(ranked);
        given(snapshotRepository.findByBoardModel_IdAndBiosVersion(5L, "F45")).willReturn(Optional.empty());
        given(snapshotRepository.findFirstByBoardModel_IdOrderByCapturedAtDesc(5L)).willReturn(Optional.of(f44));
        given(loader.load(eq(KEY), eq(9L), any())).willReturn(snapshotMenu);

        ResolvedBiosRegistry resolved = resolver().resolve(board());

        assertThat(resolved.source()).isEqualTo(BiosRegistrySource.SNAPSHOT_LATEST);
        assertThat(resolved.label()).isEqualTo("최신 채집 F44 · 목표 F45 미채집");
    }

    @Test
    @DisplayName("채집본이 하나도 없으면 자료 파일 — 어느 버전의 것인지 모른다는 배지")
    void file_fallback() {
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionRankAsc(5L)).willReturn(List.of());
        given(snapshotRepository.findFirstByBoardModel_IdOrderByCapturedAtDesc(5L)).willReturn(Optional.empty());
        given(loader.load(KEY)).willReturn(fileMenu);

        ResolvedBiosRegistry resolved = resolver().resolve(board());

        assertThat(resolved.source()).isEqualTo(BiosRegistrySource.FILE);
        assertThat(resolved.menu()).isSameAs(fileMenu);
        assertThat(resolved.label()).isEqualTo("파일 · 버전 미상");
        verify(snapshotRepository, never()).findByBoardModel_IdAndBiosVersion(anyLong(), any());
    }

    @Test
    @DisplayName("편집 가능 판정(Q3) — 자료 항목(XML)이 있고 채집본 또는 레지스트리 파일이 있으면 가능")
    void available_requiresXmlEntry_andSnapshotOrFile() {
        BoardModel md72 = board();
        given(snapshotRepository.existsByBoardModel_Id(5L)).willReturn(true);
        given(loader.registryFileExists(KEY)).willReturn(false);
        assertThat(resolver().available(md72)).isTrue();

        given(snapshotRepository.existsByBoardModel_Id(5L)).willReturn(false);
        assertThat(resolver().available(md72)).isFalse();

        BoardModel unknown = mock(BoardModel.class);
        given(unknown.getId()).willReturn(6L);
        given(unknown.getModelName()).willReturn("NO-SUCH");
        given(snapshotRepository.existsByBoardModel_Id(6L)).willReturn(true);
        assertThat(resolver().available(unknown)).isFalse();   // 채집본이 있어도 메뉴 골격(XML)이 없으면 열 수 없다
    }
}
