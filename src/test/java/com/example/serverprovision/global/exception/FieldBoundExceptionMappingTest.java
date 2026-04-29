package com.example.serverprovision.global.exception;

import com.example.serverprovision.management.bios.exception.DuplicateBiosVersionException;
import com.example.serverprovision.management.bmc.exception.BmcPathConflictException;
import com.example.serverprovision.management.bmc.exception.DuplicateBmcVersionException;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.exception.DuplicateBoardModelException;
import com.example.serverprovision.management.common.filesystem.exception.MarkerConflictException;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.exception.DuplicateSubprogramVersionException;
import com.example.serverprovision.management.subprogram.exception.SubprogramPathConflictException;
import com.example.serverprovision.management.subprogram.vo.BoardScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S4 — 도메인 예외 7건의 base-class 변경 회귀 가드. 각 예외가 {@link FieldBoundConflictException} 의
 * 인스턴스이며 {@code fieldName()} 이 plan 명시 값과 일치하는지 검증.
 *
 * <p>회귀 시 즉시 실패 — 누군가 base 를 ConflictException 으로 되돌리거나 fieldName 을 바꾸면
 * 본 테스트가 잡는다.</p>
 */
class FieldBoundExceptionMappingTest {

    @Test
    @DisplayName("DuplicateBoardModelException — field=modelName")
    void boardModel_modelName() {
        DuplicateBoardModelException ex = new DuplicateBoardModelException(Vendor.GIGABYTE, "MS03-CE0");
        assertThat(ex).isInstanceOf(FieldBoundConflictException.class);
        assertThat(ex.fieldName()).isEqualTo("modelName");
    }

    @Test
    @DisplayName("DuplicateBiosVersionException — field=version")
    void biosVersion_version() {
        DuplicateBiosVersionException ex = new DuplicateBiosVersionException(1L, "2.03");
        assertThat(ex).isInstanceOf(FieldBoundConflictException.class);
        assertThat(ex.fieldName()).isEqualTo("version");
    }

    @Test
    @DisplayName("DuplicateBmcVersionException — field=version")
    void bmcVersion_version() {
        DuplicateBmcVersionException ex = new DuplicateBmcVersionException(1L, "12.61.32");
        assertThat(ex).isInstanceOf(FieldBoundConflictException.class);
        assertThat(ex.fieldName()).isEqualTo("version");
    }

    @Test
    @DisplayName("DuplicateSubprogramVersionException — field=version")
    void subprogramVersion_version() {
        DuplicateSubprogramVersionException ex = new DuplicateSubprogramVersionException(
                SubprogramKind.DRIVER, BoardScope.COMMON, "Realtek r8169", "1.2.0");
        assertThat(ex).isInstanceOf(FieldBoundConflictException.class);
        assertThat(ex.fieldName()).isEqualTo("version");
    }

    @Test
    @DisplayName("BmcPathConflictException — field=targetDirectory")
    void bmcPath_targetDirectory() {
        BmcPathConflictException ex = new BmcPathConflictException("/opt/firmware/bmc/x");
        assertThat(ex).isInstanceOf(FieldBoundConflictException.class);
        assertThat(ex.fieldName()).isEqualTo("targetDirectory");
    }

    @Test
    @DisplayName("SubprogramPathConflictException — field=targetDirectory")
    void subprogramPath_targetDirectory() {
        SubprogramPathConflictException ex = new SubprogramPathConflictException("/opt/subprogram/x");
        assertThat(ex).isInstanceOf(FieldBoundConflictException.class);
        assertThat(ex.fieldName()).isEqualTo("targetDirectory");
    }

    @Test
    @DisplayName("MarkerConflictException — field=targetDirectory")
    void markerConflict_targetDirectory() {
        MarkerConflictException ex = new MarkerConflictException("/opt/iso");
        assertThat(ex).isInstanceOf(FieldBoundConflictException.class);
        assertThat(ex.fieldName()).isEqualTo("targetDirectory");
    }
}
