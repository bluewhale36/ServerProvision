package com.example.serverprovision.execution.wininstall.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 테스트용 가짜 WIM — 헤더 208 B + UTF-16LE XML 만 갖는다(scripts/wininstall-fixture/make-fake-source.py 와 같은 바이트).
 * 앱은 그 두 구간만 읽으므로 4.8 GB 실물 없이 카탈로그 · 영역 · 검사기를 검증할 수 있다.
 */
public final class FakeWim {

    public static final String FIXTURE = "/wininstall/install.wim.xml";
    public static final String STANDARD_DESKTOP = "Windows Server 2025 SERVERSTANDARD";
    private static final int RESHDR_FLAG_METADATA = 0x2;
    private static final byte[] MAGIC = {'M', 'S', 'W', 'I', 'M', 0, 0, 0};

    private FakeWim() {
    }

    /** 실측 1호 install.wim 의 XML(이미지 4종). */
    public static String fixtureXml() {
        try (InputStream in = FakeWim.class.getResourceAsStream(FIXTURE)) {
            if (in == null) {
                throw new IllegalStateException("픽스처 없음: " + FIXTURE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 이미지 2종(Standard Core · Standard)만 가진 최소 XML — 소스 교체 시나리오용. */
    public static String twoImageXml() {
        return """
                <WIM><TOTALBYTES>1</TOTALBYTES>
                <IMAGE INDEX="1"><NAME>Windows Server 2025 SERVERSTANDARDCORE</NAME><DISPLAYNAME>Windows Server 2025 Standard</DISPLAYNAME>
                <WINDOWS><EDITIONID>ServerStandard</EDITIONID><INSTALLATIONTYPE>Server Core</INSTALLATIONTYPE>
                <LANGUAGES><LANGUAGE>ko-KR</LANGUAGE><DEFAULT>ko-KR</DEFAULT></LANGUAGES>
                <VERSION><MAJOR>10</MAJOR><MINOR>0</MINOR><BUILD>26100</BUILD><SPBUILD>1742</SPBUILD></VERSION></WINDOWS></IMAGE>
                <IMAGE INDEX="2"><NAME>Windows Server 2025 SERVERSTANDARD</NAME><DISPLAYNAME>Windows Server 2025 Standard (데스크톱 환경)</DISPLAYNAME>
                <WINDOWS><EDITIONID>ServerStandard</EDITIONID><INSTALLATIONTYPE>Server</INSTALLATIONTYPE>
                <LANGUAGES><LANGUAGE>ko-KR</LANGUAGE><DEFAULT>ko-KR</DEFAULT></LANGUAGES>
                <VERSION><MAJOR>10</MAJOR><MINOR>0</MINOR><BUILD>26100</BUILD><SPBUILD>1742</SPBUILD></VERSION></WINDOWS></IMAGE>
                </WIM>""";
    }

    public static byte[] bytes(String xml) {
        return bytes(xml, RESHDR_FLAG_METADATA);
    }

    public static byte[] bytes(String xml, int resourceFlags) {
        byte[] body = xml.getBytes(StandardCharsets.UTF_16LE);
        byte[] payload = new byte[body.length + 2];
        payload[0] = (byte) 0xFF;
        payload[1] = (byte) 0xFE;
        System.arraycopy(body, 0, payload, 2, body.length);
        ByteBuffer header = ByteBuffer.allocate(WimHeader.SIZE).order(ByteOrder.LITTLE_ENDIAN);
        header.put(0, MAGIC);
        header.putInt(8, WimHeader.SIZE);
        header.putInt(12, 0x10D00);
        header.putInt(16, 0x40082);
        header.putInt(20, 32768);
        header.putInt(44, countImages(xml));
        header.putLong(72, payload.length | ((long) resourceFlags << 56));
        header.putLong(80, WimHeader.SIZE);
        header.putLong(88, payload.length);
        byte[] out = new byte[WimHeader.SIZE + payload.length];
        System.arraycopy(header.array(), 0, out, 0, WimHeader.SIZE);
        System.arraycopy(payload, 0, out, WimHeader.SIZE, payload.length);
        return out;
    }

    /** {@code <root>/sources/{install.wim, boot.wim, setup.exe}} 를 만든다. */
    public static Path writeSource(Path root, String xml) throws IOException {
        Path sources = root.resolve("sources");
        Files.createDirectories(sources);
        Files.write(sources.resolve("install.wim"), bytes(xml));
        Files.writeString(sources.resolve("boot.wim"), "FAKE-BOOT-WIM");
        Files.writeString(sources.resolve("setup.exe"), "MZ");
        return root;
    }

    private static int countImages(String xml) {
        int count = 0;
        int idx = 0;
        while ((idx = xml.indexOf("<IMAGE INDEX=", idx)) >= 0) {
            count++;
            idx++;
        }
        return count;
    }

    /** E4-1-a-4 — 조립 액션이 만드는 설치 후 스크립트 둘(내용은 존재 판정에만 쓰인다). */
    public static void writeOemScripts(java.nio.file.Path root) throws java.io.IOException {
        java.nio.file.Path oem = root.resolve("sources").resolve("$OEM$");
        java.nio.file.Path cmd = oem.resolve("$$").resolve("Setup").resolve("Scripts").resolve("SetupComplete.cmd");
        java.nio.file.Path ps1 = oem.resolve("$1").resolve("SPV").resolve("spv-report.ps1");
        java.nio.file.Files.createDirectories(cmd.getParent());
        java.nio.file.Files.createDirectories(ps1.getParent());
        java.nio.file.Files.writeString(cmd, "@echo off\r\n");
        java.nio.file.Files.writeString(ps1, "param($BaseUrl,$Token)\n");
    }
}
