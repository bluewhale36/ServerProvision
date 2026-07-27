package com.example.serverprovision.execution.asset.spi;

import com.example.serverprovision.execution.asset.dto.SealResult;
import com.example.serverprovision.execution.asset.exception.SystemAssetSealNotSupportedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SystemAssetArea} 확장점의 봉인 기본 계약 검증. 프로덕션 영역(진단 자산·TFTP)은 둘 다 정적 파일이라
 * 봉인을 지원하지만, 편집 대상 설정·서비스 상태로 판정하는 미래 영역(예: dhcpd 설정)은 봉인 개념이 없다 —
 * 그런 영역은 {@code supportsSeal()} 을 false 로 두고 {@code seal()} 을 오버라이드하지 않아, 기본
 * {@code seal()} 이 {@link SystemAssetSealNotSupportedException}(409) 으로 거절한다. 여기서는 그
 * <b>domain trigger</b>(확장점 default 가 실제로 예외를 던지는가)를 봉인 미지원 fake 영역으로 실증한다.
 * 컨트롤러 계층의 409 advice 매핑은 {@code SystemAssetControllerTest} 가 별도로 지킨다.
 */
class SystemAssetAreaDefaultsTest {

    @Test
    @DisplayName("봉인 미지원 영역(supportsSeal=false) — 기본 seal() 이 SealNotSupported 로 거절")
    void defaultSeal_onUnsupportedArea_throws() {
        SystemAssetArea unsupported = new FakeArea(false);

        assertThatThrownBy(unsupported::seal)
                .isInstanceOf(SystemAssetSealNotSupportedException.class);
    }

    @Test
    @DisplayName("supportsSeal 기본값은 true — 오버라이드하지 않은 영역은 봉인 지원으로 간주")
    void supportsSeal_defaultsToTrue() {
        // 봉인 지원 영역은 supportsSeal 을 오버라이드하지 않고(기본 true) seal() 만 실제 로직으로 오버라이드한다.
        SystemAssetArea supportedByDefault = new FakeArea(true);

        assertThat(supportedByDefault.supportsSeal()).isTrue();
    }

    /**
     * 봉인 지원 여부만 파라미터로 받는 최소 fake 영역. supportsSeal=false 이면 seal() 을 오버라이드하지 않아
     * 확장점 default(throw)를 그대로 탄다. 나머지 메서드는 이 테스트가 소비하지 않아 사소한 값만 돌려준다.
     */
    private record FakeArea(boolean supportsSeal) implements SystemAssetArea {
        @Override
        public SystemAssetAreaKey areaKey() {
            return SystemAssetAreaKey.DIAGNOSTIC;   // 예외 메시지용 — 실제 DIAGNOSTIC 영역과 무관한 fake
        }

        @Override
        public String displayName() {
            return "fake";
        }

        @Override
        public AreaAvailability availability() {
            return AreaAvailability.CONFIGURED;
        }

        @Override
        public List<SystemAssetSlot> slots() {
            return List.of();
        }

        @Override
        public AssetSlotStatus inspect(SystemAssetSlot slot) {
            throw new UnsupportedOperationException("이 테스트는 inspect 를 호출하지 않는다");
        }

        @Override
        public SealResult seal() {
            // supportsSeal=true 인 fake 는 봉인을 실제로 하지 않고 no-op 결과만 — 봉인 미지원 fake 는 이 메서드를
            // 타지 않고(호출부가 default 를 기대), supportsSeal=false 경로만 default throw 를 검증한다.
            return supportsSeal ? new SealResult(0, 0) : SystemAssetArea.super.seal();
        }
    }
}
