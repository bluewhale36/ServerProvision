package com.example.serverprovision.execution.engine.diagnose;

import com.example.serverprovision.execution.vo.HardwareSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** HF15-5 — lsblk 보고 해석: WWN(0x 접두 · 대문자) 정규화, 누락 필드 관용(구 에이전트), device 없는 항목 무시. */
class OsVisibleDiskParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("wwn 이 있으면 소문자 · 0x 제거로 싣고, 없으면 null(구 에이전트 호환)")
    void parse_wwnNormalized_andTolerant() {
        List<HardwareSpec.DiskInfo> disks = OsVisibleDiskParser.parse(mapper.readTree("""
                [{"device":"sda","size":"446.6G","rota":"1","tran":"","wwn":"0x600605B0AA11"},
                 {"device":"sdb","size":"18.2T","rota":"1","tran":"sas"},
                 {"size":"0B"}]"""));

        assertThat(disks).hasSize(2);
        assertThat(disks.get(0).wwn()).isEqualTo("600605b0aa11");
        assertThat(disks.get(0).transport()).isNull();
        assertThat(disks.get(0).type()).isEqualTo("HDD");
        assertThat(disks.get(1).wwn()).isNull();
        assertThat(disks.get(1).transport()).isEqualTo("SAS");
    }

    @Test
    @DisplayName("배열이 아니거나 null 이면 빈 목록")
    void parse_nonArray_empty() {
        assertThat(OsVisibleDiskParser.parse(null)).isEmpty();
        assertThat(OsVisibleDiskParser.parse(mapper.readTree("{}"))).isEmpty();
    }
}
