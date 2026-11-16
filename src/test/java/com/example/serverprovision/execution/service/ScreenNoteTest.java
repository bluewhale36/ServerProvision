package com.example.serverprovision.execution.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenNoteTest {

    @Test
    @DisplayName("요청 경로를 빼고 항목 상수를 항목 이름으로 바꾼다 — 원장 원문은 그대로 둔다")
    void cleans() {
        assertThat(ScreenNote.of("POST /api/session — 자격증명 거부(CSRFToken 없음)")).isEqualTo("자격증명 거부(CSRFToken 미수신)");
        assertThat(ScreenNote.of("DATE_TIME — PUT /api/settings/date-time — BMC 가 데이터를 거절했습니다(invalid, code 1)"))
                .isEqualTo("시간 설정: BMC 가 데이터를 거절했습니다(invalid, code 1)");
        assertThat(ScreenNote.of(null)).isNull();
    }

    @Test
    @DisplayName("저장된 엔진 · 에이전트 문장(옛 문장 포함)을 사람 말로 바꾼다")
    void rewritesStoredSentences() {
        assertThat(ScreenNote.of("no raid-class pci device")).isEqualTo("RAID 카드(PCI)를 찾지 못했습니다");
        assertThat(ScreenNote.of("no family driver bound (server hint: mpt3sas megaraid_sas)"))
                .isEqualTo("RAID 카드 드라이버가 올라오지 않았습니다(기대한 드라이버: mpt3sas megaraid_sas)");
        assertThat(ScreenNote.of("다음 부팅 PXE 강제 : 반영 확인 · 재시작(ForceRestart) 발행 — 게스트가 PXE 로 돌아오면 이어서 진행합니다."))
                .isEqualTo("재시작 명령을 보냈습니다.");
        assertThat(ScreenNote.of("다음 부팅 PXE 강제 : 반영 확인 · 전원이 켜졌습니다 (Reset(On) → PowerState 폴링 확인)."))
                .isEqualTo("전원을 켰습니다.");
        assertThat(ScreenNote.of("전송 완료")).isEqualTo("굽기 완료");
        assertThat(ScreenNote.of("목표 F29 · 확인 F27")).isEqualTo("구운 뒤 다시 읽은 버전이 목표와 다릅니다(목표 F29, 읽은 값 F27).");
        assertThat(ScreenNote.of("재진입 6회 — 상한 5회 초과")).isEqualTo("재부팅이 상한(5회)을 넘겨 반복됐습니다");
        assertThat(ScreenNote.of("hold-ttl")).isEqualTo("자원 대기 시한이 지나 실패했습니다. 자원을 되살린 뒤 재시도하십시오.");
        assertThat(ScreenNote.of("BIOS F45 레지스트리 허용값 밖 : PCIS007 = Disabled — 허용 {Enabled}"))
                .isEqualTo("BIOS 설정 템플릿이 이 보드의 BIOS(F45)와 맞지 않습니다: PCIS007 = Disabled(허용 값: Enabled)");
    }
}
