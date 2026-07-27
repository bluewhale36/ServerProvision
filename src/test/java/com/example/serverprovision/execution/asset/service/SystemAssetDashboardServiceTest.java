package com.example.serverprovision.execution.asset.service;

import com.example.serverprovision.execution.asset.dto.response.SystemAssetAreaGroupResponse;
import com.example.serverprovision.execution.asset.dto.response.SystemAssetOverviewResponse;
import com.example.serverprovision.execution.asset.exception.SystemAssetAreaNotFoundException;
import com.example.serverprovision.execution.asset.spi.AreaAvailability;
import com.example.serverprovision.execution.asset.spi.AssetSlotStatus;
import com.example.serverprovision.execution.asset.spi.SealedFileCondition;
import com.example.serverprovision.execution.asset.spi.SystemAssetArea;
import com.example.serverprovision.execution.asset.spi.SystemAssetAreaKey;
import com.example.serverprovision.execution.asset.spi.SystemAssetSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * E1-I-3-a CP4 — 통합 대시보드 집계·라우팅 서비스 검증. 영역({@link SystemAssetArea})을 mock 으로 주입해,
 * 영역이 늘어도 조건분기 없이 순회·합산·라우팅함을 확인한다: 두 영역 집계 합산, 미등록 키 404, 그리고
 * 주입 순서와 무관한 표시 순서 고정({@code EnumMap} 으로 라우팅 키 선언 순서를 따른다).
 */
class SystemAssetDashboardServiceTest {

    @Test
    @DisplayName("집계 — 두 영역의 healthy·전체 슬롯을 합산하고 영역별 소계도 채운다")
    void loadOverview_sumsAcrossAreas() {
        SystemAssetSlot d1 = new FixedSlot("D1");
        SystemAssetSlot d2 = new FixedSlot("D2");
        SystemAssetArea diagnostic = area(SystemAssetAreaKey.DIAGNOSTIC, "진단 리눅스 부팅 자산", List.of(d1, d2));
        given(diagnostic.inspect(d1)).willReturn(healthy());
        given(diagnostic.inspect(d2)).willReturn(unhealthy());   // 부재 슬롯 — 집계에서 제외

        SystemAssetSlot t1 = new FixedSlot("T1");
        SystemAssetArea tftp = area(SystemAssetAreaKey.TFTP, "TFTP 부팅 인프라 자산", List.of(t1));
        given(tftp.inspect(t1)).willReturn(healthy());

        SystemAssetDashboardService service = new SystemAssetDashboardService(List.of(diagnostic, tftp));

        SystemAssetOverviewResponse overview = service.loadOverview();

        assertThat(overview.totalOk()).isEqualTo(2);
        assertThat(overview.totalCount()).isEqualTo(3);
        assertThat(overview.areas()).hasSize(2);
        SystemAssetAreaGroupResponse diagGroup = overview.areas().get(0);
        assertThat(diagGroup.areaKey()).isEqualTo("DIAGNOSTIC");
        assertThat(diagGroup.okCount()).isEqualTo(1);
        assertThat(diagGroup.totalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("라우팅 — 파싱 실패 키·미등록 영역 키 모두 SystemAssetAreaNotFoundException(주소 위조 방어)")
    void areaOf_unknownKey_throwsNotFound() {
        SystemAssetArea diagnostic = area(SystemAssetAreaKey.DIAGNOSTIC, "진단 리눅스 부팅 자산", List.of());
        SystemAssetDashboardService service = new SystemAssetDashboardService(List.of(diagnostic));

        assertThatThrownBy(() -> service.areaOf("NOPE"))          // enum 파싱조차 실패
                .isInstanceOf(SystemAssetAreaNotFoundException.class);
        assertThatThrownBy(() -> service.areaOf("TFTP"))          // 유효 enum 이나 미등록 영역
                .isInstanceOf(SystemAssetAreaNotFoundException.class);
        assertThatThrownBy(() -> service.seal("NOPE"))
                .isInstanceOf(SystemAssetAreaNotFoundException.class);
    }

    @Test
    @DisplayName("표시 순서 — 주입 순서를 뒤집어도 라우팅 키 선언 순서(DIAGNOSTIC→TFTP)로 고정된다")
    void loadOverview_orderFollowsKeyDeclaration_notInjectionOrder() {
        SystemAssetArea diagnostic = area(SystemAssetAreaKey.DIAGNOSTIC, "진단 리눅스 부팅 자산", List.of());
        SystemAssetArea tftp = area(SystemAssetAreaKey.TFTP, "TFTP 부팅 인프라 자산", List.of());

        // 주입은 TFTP 를 먼저 — 그래도 EnumMap 이 선언 순서로 순회해야 한다.
        SystemAssetDashboardService service = new SystemAssetDashboardService(List.of(tftp, diagnostic));

        SystemAssetOverviewResponse overview = service.loadOverview();

        assertThat(overview.areas())
                .extracting(SystemAssetAreaGroupResponse::areaKey)
                .containsExactly("DIAGNOSTIC", "TFTP");
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    /** 라우팅 키·표시명·슬롯을 갖춘 mock 영역(서빙 활성·봉인 지원 기본). 슬롯별 inspect 는 테스트가 추가 stub. */
    private static SystemAssetArea area(SystemAssetAreaKey key, String displayName, List<SystemAssetSlot> slots) {
        SystemAssetArea area = mock(SystemAssetArea.class);
        given(area.areaKey()).willReturn(key);
        given(area.displayName()).willReturn(displayName);
        given(area.availability()).willReturn(AreaAvailability.CONFIGURED);
        given(area.slots()).willReturn(slots);
        given(area.supportsSeal()).willReturn(true);
        return area;
    }

    private static AssetSlotStatus healthy() {
        return AssetSlotStatus.present(10L, Instant.now(), SealedFileCondition.ORIGINAL);
    }

    private static AssetSlotStatus unhealthy() {
        return AssetSlotStatus.notPresent(SealedFileCondition.MISSING);
    }

    /** 집계·행 조립이 NPE 없이 소비할 수 있도록 모든 표시 필드를 채운 슬롯 스텁. */
    private record FixedSlot(String key) implements SystemAssetSlot {
        @Override
        public String slotKey() {
            return key;
        }

        @Override
        public String label() {
            return key + " 라벨";
        }

        @Override
        public String filename() {
            return key + ".bin";
        }

        @Override
        public String category() {
            return "테스트 자산";
        }

        @Override
        public String layoutLabel() {
            return "단일 파일";
        }

        @Override
        public boolean replaceable() {
            return false;
        }

        @Override
        public String replaceCadence() {
            return "해당 없음";
        }
    }
}
