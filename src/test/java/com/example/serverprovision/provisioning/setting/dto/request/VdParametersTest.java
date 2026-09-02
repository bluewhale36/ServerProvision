package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.VdAccessPolicy;
import com.example.serverprovision.provisioning.setting.enums.VdBackgroundInit;
import com.example.serverprovision.provisioning.setting.enums.VdDriveCache;
import com.example.serverprovision.provisioning.setting.enums.VdInitialization;
import com.example.serverprovision.provisioning.setting.enums.VdIoPolicy;
import com.example.serverprovision.provisioning.setting.enums.VdReadPolicy;
import com.example.serverprovision.provisioning.setting.enums.VdStripSize;
import com.example.serverprovision.provisioning.setting.enums.VdWritePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** VD 파라미터 조립 진리표(E3.5-6 plan D4 · 2026-09-02 기본값 개정) — 폼 데모 JS 와 같은 규칙의 서버측 SSOT 검증. */
class VdParametersTest {

    private static final String DEFAULT_CREATE = "wb ra direct strip=256 pdcache=default";

    private static VdParameters of(VdWritePolicy wp, VdReadPolicy rp, VdIoPolicy iop, VdStripSize strip,
                                   VdAccessPolicy ap, VdDriveCache dc, VdBackgroundInit bgi, VdInitialization init) {
        return new VdParameters(wp, rp, iop, strip, ap, dc, bgi, init);
    }

    @Test
    @DisplayName("비운 축(null)은 HII 기본값으로 채워진다 — 전부 null 이면 DEFAULTS 와 같고 8축이 전부 명시 조립된다")
    void nulls_fillWithHiiDefaults() {
        VdParameters p = of(null, null, null, null, null, null, null, null);
        assertThat(p).isEqualTo(VdParameters.DEFAULTS);
        assertThat(p.createOpts()).isEqualTo(DEFAULT_CREATE);
        assertThat(p.setOps()).containsExactly("bgi=on", "accesspolicy=rw");
        assertThat(p.initToken()).isEqualTo("none");
        assertThat(p.overridesDriveCache()).isFalse();
    }

    @Test
    @DisplayName("각 enum 의 DEFAULT = 9361-8i HII 기본 선택(256KB · RA · WB · Direct · RW · Unchanged · Disable BGI No · Init No) — 축마다 정확히 하나")
    void defaults_matchHiiSelection() {
        assertThat(VdStripSize.DEFAULT).isEqualTo(VdStripSize.KB_256);
        assertThat(VdReadPolicy.DEFAULT).isEqualTo(VdReadPolicy.READ_AHEAD);
        assertThat(VdWritePolicy.DEFAULT).isEqualTo(VdWritePolicy.WRITE_BACK);
        assertThat(VdIoPolicy.DEFAULT).isEqualTo(VdIoPolicy.DIRECT);
        assertThat(VdAccessPolicy.DEFAULT).isEqualTo(VdAccessPolicy.READ_WRITE);
        assertThat(VdDriveCache.DEFAULT).isEqualTo(VdDriveCache.UNCHANGED);
        assertThat(VdBackgroundInit.DEFAULT).isEqualTo(VdBackgroundInit.ON);
        assertThat(VdInitialization.DEFAULT).isEqualTo(VdInitialization.NONE);
        assertThat(Arrays.stream(VdStripSize.values()).filter(VdStripSize::isDefault)).hasSize(1);
        assertThat(Arrays.stream(VdDriveCache.values()).filter(VdDriveCache::isDefault)).hasSize(1);
        assertThat(Arrays.stream(VdInitialization.values()).filter(VdInitialization::isDefault)).hasSize(1);
    }

    @Test
    @DisplayName("생성 시 계열(Write · Read · I/O · Strip · Drive Cache)은 createOpts 한 문자열로 — 고른 값이 기본값 자리를 대체한다")
    void createTimeAxes_goInline() {
        VdParameters p = of(VdWritePolicy.WRITE_THROUGH, VdReadPolicy.NO_READ_AHEAD, VdIoPolicy.CACHED,
                VdStripSize.KB_64, null, VdDriveCache.OFF, null, null);
        assertThat(p.createOpts()).isEqualTo("wt nora cached strip=64 pdcache=off");
        assertThat(p.setOps()).containsExactly("bgi=on", "accesspolicy=rw");
        assertThat(p.overridesDriveCache()).isTrue();
    }

    @Test
    @DisplayName("생성 후 계열(BGI · Access)은 setOps 로 — add vd 인라인이 없는 축, 순서는 bgi → accesspolicy 고정")
    void postCreateAxes_goSetOps() {
        VdParameters p = of(null, null, null, null,
                VdAccessPolicy.READ_ONLY, null, VdBackgroundInit.OFF, null);
        assertThat(p.createOpts()).isEqualTo(DEFAULT_CREATE);
        assertThat(p.setOps()).containsExactly("bgi=off", "accesspolicy=ro");
    }

    @Test
    @DisplayName("초기화 축 — none/fast/full 토큰, 비우면 none(HII 'Default Initialization: No')")
    void initAxis_tokens() {
        assertThat(of(null, null, null, null, null, null, null, VdInitialization.FAST).initToken()).isEqualTo("fast");
        assertThat(of(null, null, null, null, null, null, null, VdInitialization.FULL).initToken()).isEqualTo("full");
        assertThat(of(null, null, null, null, null, null, null, null).initToken()).isEqualTo("none");
    }

    @Test
    @DisplayName("toDisplay — 8축 전부를 HII 항목 순서로 ' · ' 나열(상세 카드 부기 = 집행에 실리는 값 그대로)")
    void toDisplay_listsAllAxes() {
        VdParameters p = of(VdWritePolicy.WRITE_BACK, null, null, null, null,
                VdDriveCache.OFF, VdBackgroundInit.OFF, null);
        assertThat(p.toDisplay()).isEqualTo("Strip 256 KB · Read Ahead · Write Back · Direct IO · Access Read Write"
                + " · Drive Cache Disable · Disable BGI Yes (BGI 끔) · Init No (초기화 안 함)");
    }
}
