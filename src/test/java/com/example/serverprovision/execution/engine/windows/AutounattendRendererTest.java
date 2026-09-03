package com.example.serverprovision.execution.engine.windows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** E4-1-a-3 CP4 — 응답 파일 렌더: 치환 완전성 · XML 이스케이프 · 언어 4자리 · ComputerName 규칙 · 비밀번호 인코딩. */
class AutounattendRendererTest {

    private static AutounattendRenderer.AutounattendValues values(String imageName, String password) {
        return new AutounattendRenderer.AutounattendValues("ko-KR", "TVRH6-WHNXV-R9WG3-9XRFY-MY832", imageName,
                "SPV-14174000", "Korea Standard Time", password, "http://10.0.0.7:8080", "a3f9d2c8b41e4f7a9c0d5e6f7a8b9c1d");
    }

    @Test
    @DisplayName("치환 — 자리표시자 0 · 언어 태그가 UILanguage(3) · InputLocale(2) · SystemLocale(2) · UserLocale(2) 에 그대로")
    void render_replacesEveryPlaceholder_andLanguageInAllSlots() {
        String xml = AutounattendRenderer.render(values("Windows Server 2025 SERVERSTANDARD", "P@ssw0rd!"));

        assertThat(xml).doesNotContain("__");
        assertThat(xml.split("<UILanguage>ko-KR</UILanguage>", -1)).hasSize(4);
        assertThat(xml.split("<InputLocale>ko-KR</InputLocale>", -1)).hasSize(3);
        assertThat(xml.split("<SystemLocale>ko-KR</SystemLocale>", -1)).hasSize(3);
        assertThat(xml.split("<UserLocale>ko-KR</UserLocale>", -1)).hasSize(3);
        assertThat(xml).contains("<Key>/IMAGE/NAME</Key>")
                .contains("<Value>Windows Server 2025 SERVERSTANDARD</Value>")
                .contains("<ComputerName>SPV-14174000</ComputerName>")
                .contains("<TimeZone>Korea Standard Time</TimeZone>")
                .contains("<ProductKey><Key>TVRH6-WHNXV-R9WG3-9XRFY-MY832</Key>");
    }

    @Test
    @DisplayName("비밀번호 — 평문은 없고 Base64(평문 + 노드명) 둘이 각 노드에 실린다")
    void render_encodesPasswordTwice_neverPlain() {
        String xml = AutounattendRenderer.render(values("Windows Server 2025 SERVERSTANDARD", "P@ssw0rd!"));

        assertThat(xml).doesNotContain("P@ssw0rd!")
                .contains("<AdministratorPassword>\n          <Value>UABAAHMAcwB3ADAAcgBkACEAQQBkAG0AaQBuAGkAcwB0AHIAYQB0AG8AcgBQAGEAcwBzAHcAbwByAGQA</Value>")
                .contains("<Password>\n          <Value>UABAAHMAcwB3ADAAcgBkACEAUABhAHMAcwB3AG8AcgBkAA==</Value>");
    }

    @Test
    @DisplayName("XML 이스케이프 — 이미지 이름의 & < > 가 엔티티로(파일이 깨지지 않는다)")
    void render_escapesXml() {
        String xml = AutounattendRenderer.render(values("A & B <C>", "x\"y'z"));
        assertThat(xml).contains("<Value>A &amp; B &lt;C&gt;</Value>").doesNotContain("<Value>A & B");
    }

    @Test
    @DisplayName("구조 — FirstLogonCommands 는 표식 파일(1) + 완료 보고 스크립트 실행(2, E4-1-a-4) — base URL · 토큰이 인자로 실린다")
    void render_firstLogonMarkerAndReport() {
        String xml = AutounattendRenderer.render(values("Windows Server 2025 SERVERSTANDARD", "P@ssw0rd!"));
        assertThat(xml.split("<SynchronousCommand", -1)).hasSize(3);
        assertThat(xml).contains("spv-firstlogon.txt")
                .contains("-File C:\\SPV\\spv-report.ps1 -BaseUrl \"http://10.0.0.7:8080\" -Token \"a3f9d2c8b41e4f7a9c0d5e6f7a8b9c1d\"")
                .doesNotContain("__REPORT_BASE_URL__").doesNotContain("__GUEST_TOKEN__")
                .doesNotContain("SetupComplete");   // 스크립트 본체는 $OEM$ 로 간다 — 응답 파일에는 실행 명령만
    }

    @Test
    @DisplayName("ComputerName — SPV- + systemUUID 뒤 8 hex 대문자 · 15자 안 · 결정적")
    void computerName_isDeterministicSuffix() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        String name = AutounattendRenderer.computerNameFor(uuid);
        assertThat(name).isEqualTo("SPV-14174000").hasSizeLessThanOrEqualTo(15);
        assertThat(AutounattendRenderer.computerNameFor(uuid)).isEqualTo(name);
        assertThat(AutounattendRenderer.computerNameFor(UUID.fromString("00000000-0000-0000-0000-0000deadbeef"))).isEqualTo("SPV-DEADBEEF");
    }
}
