package com.example.serverprovision.execution.wininstall.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** E4-1-a-2 CP4 — 실측 1호 install.wim XML(이미지 4종) 해석. */
class WimXmlReaderTest {

    private static byte[] utf16(String xml) {
        byte[] body = xml.getBytes(StandardCharsets.UTF_16LE);
        byte[] out = new byte[body.length + 2];
        out[0] = (byte) 0xFF;
        out[1] = (byte) 0xFE;
        System.arraycopy(body, 0, out, 2, body.length);
        return out;
    }

    @Test
    @DisplayName("실측 XML → 4종 · index 2 = SERVERSTANDARD(데스크톱 환경 · ServerStandard · ko-KR · 10.0.26100.1742)")
    void read_fixture() {
        List<WindowsImage> images = WimXmlReader.read(utf16(FakeWim.fixtureXml()));

        assertThat(images).hasSize(4);
        WindowsImage standard = images.get(1);
        assertThat(standard.index()).isEqualTo(2);
        assertThat(standard.name().value()).isEqualTo("Windows Server 2025 SERVERSTANDARD");
        assertThat(standard.displayName()).isEqualTo("Windows Server 2025 Standard (데스크톱 환경)");
        assertThat(standard.editionId()).isEqualTo("ServerStandard");
        assertThat(standard.installationType()).isEqualTo("Server");
        assertThat(standard.desktopExperience()).isTrue();
        assertThat(standard.language()).isEqualTo("ko-KR");
        assertThat(standard.build()).isEqualTo("10.0.26100.1742");
        assertThat(images.get(0).desktopExperience()).isFalse(); // SERVERSTANDARDCORE = Server Core
        assertThat(images.get(3).editionId()).isEqualTo("ServerDatacenter");
    }

    @Test
    @DisplayName("DOCTYPE 포함 XML → 거절(외부 엔티티 차단) · NAME 없는 IMAGE → 거절")
    void read_rejectsDoctypeAndNameless() {
        String doctype = "<!DOCTYPE WIM [<!ENTITY x \"y\">]><WIM><IMAGE INDEX=\"1\"><NAME>&x;</NAME></IMAGE></WIM>";
        assertThatThrownBy(() -> WimXmlReader.read(utf16(doctype))).isInstanceOf(WimFormatException.class);

        String nameless = "<WIM><IMAGE INDEX=\"1\"><DISPLAYNAME>x</DISPLAYNAME></IMAGE></WIM>";
        assertThatThrownBy(() -> WimXmlReader.read(utf16(nameless))).isInstanceOf(WimFormatException.class)
                .hasMessageContaining("NAME");
    }
}
