package com.example.serverprovision.execution.wininstall.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** E4-1-a-2 CP4 — WIM 헤더 208 B 해석(매직 · 이미지 수 · XML 자원 위치). */
class WimHeaderTest {

    @Test
    @DisplayName("정상 헤더 → 이미지 수 · XML 오프셋(208) · XML 크기")
    void parse_readsImageCountAndXmlResource() {
        byte[] wim = FakeWim.bytes(FakeWim.twoImageXml());

        WimHeader header = WimHeader.parse(ByteBuffer.wrap(wim));

        assertThat(header.imageCount()).isEqualTo(2);
        assertThat(header.xmlOffset()).isEqualTo(WimHeader.SIZE);
        assertThat(header.xmlSize()).isEqualTo(wim.length - WimHeader.SIZE);
    }

    @Test
    @DisplayName("매직 불일치 · 208 바이트 미만 · 압축 XML 플래그 → WimFormatException")
    void parse_rejectsMalformed() {
        byte[] wim = FakeWim.bytes(FakeWim.twoImageXml());
        byte[] badMagic = wim.clone();
        badMagic[0] = 'X';
        assertThatThrownBy(() -> WimHeader.parse(ByteBuffer.wrap(badMagic))).isInstanceOf(WimFormatException.class);

        assertThatThrownBy(() -> WimHeader.parse(ByteBuffer.wrap(new byte[100]))).isInstanceOf(WimFormatException.class);

        byte[] compressed = FakeWim.bytes(FakeWim.twoImageXml(), 0x4);
        assertThatThrownBy(() -> WimHeader.parse(ByteBuffer.wrap(compressed)))
                .isInstanceOf(WimFormatException.class).hasMessageContaining("압축");
    }
}
