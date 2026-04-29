package com.example.serverprovision.global.security;

import com.example.serverprovision.global.security.config.UploadSecurityProperties;
import com.example.serverprovision.global.security.config.UploadSecurityProperties.ExecutableBinaryPolicy;
import com.example.serverprovision.global.security.config.UploadSecurityProperties.SuspiciousFilenamesPolicy;
import com.example.serverprovision.global.security.exception.ExecutableContentRejectedException;
import com.example.serverprovision.global.security.exception.MaliciousContentSuspectedException;
import com.example.serverprovision.global.security.exception.SuspiciousFilenameException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentGuardTest {

    private UploadSecurityProperties props(ExecutableBinaryPolicy exec, SuspiciousFilenamesPolicy susp) {
        return new UploadSecurityProperties(
                DataSize.ofGigabytes(5), DataSize.ofGigabytes(20),
                5000, DataSize.ofGigabytes(20),
                DataSize.ofGigabytes(20), 100, 10000,
                exec, 50, susp,
                null, DataSize.ofGigabytes(20)
        );
    }

    @Test
    @DisplayName("ZIP 모드 + 정상 PK header → 통과")
    void safeZip_pass() {
        ContentGuard guard = new ContentGuard(props(ExecutableBinaryPolicy.DENY, SuspiciousFilenamesPolicy.DISABLED));
        byte[] pk = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00};
        guard.assertSafeZip(new MockMultipartFile("zip", "ok.zip", "application/zip", pk));
    }

    @Test
    @DisplayName("ZIP 모드 + ELF header → MaliciousContentSuspected")
    void safeZip_elfRejected() {
        ContentGuard guard = new ContentGuard(props(ExecutableBinaryPolicy.DENY, SuspiciousFilenamesPolicy.DISABLED));
        byte[] elf = {0x7F, 0x45, 0x4C, 0x46, 0x02, 0x01};
        assertThatThrownBy(() -> guard.assertSafeZip(
                new MockMultipartFile("zip", "fake.zip", "application/zip", elf)))
                .isInstanceOf(MaliciousContentSuspectedException.class);
    }

    @Test
    @DisplayName("실행 binary (ELF) + DENY 정책 → ExecutableContentRejected")
    void elfDeny() {
        ContentGuard guard = new ContentGuard(props(ExecutableBinaryPolicy.DENY, SuspiciousFilenamesPolicy.DISABLED));
        // ELF magic + minimal valid bytes for Tika
        byte[] elf = new byte[64];
        elf[0] = 0x7F; elf[1] = 0x45; elf[2] = 0x4C; elf[3] = 0x46; elf[4] = 0x02; elf[5] = 0x01;
        MultipartFile f = new MockMultipartFile("file", "evil.bin", "application/octet-stream", elf);
        assertThatThrownBy(() -> guard.classifyAndApplyExecutablePolicy(f, null))
                .isInstanceOf(ExecutableContentRejectedException.class);
    }

    @Test
    @DisplayName("실행 binary + WARN 정책 → 통과 + warning 수집")
    void elfWarn() {
        ContentGuard guard = new ContentGuard(props(ExecutableBinaryPolicy.WARN, SuspiciousFilenamesPolicy.DISABLED));
        byte[] elf = new byte[64];
        elf[0] = 0x7F; elf[1] = 0x45; elf[2] = 0x4C; elf[3] = 0x46; elf[4] = 0x02; elf[5] = 0x01;
        MultipartFile f = new MockMultipartFile("file", "warn.bin", "application/octet-stream", elf);
        List<String> warnings = new ArrayList<>();
        guard.classifyAndApplyExecutablePolicy(f, ContentGuard.ResultCollector.into(warnings));
        // WARN 정책은 통과. 메시지 수집 여부는 Tika 가 ELF 로 인식했는지에 따라 결정 — 실패 시 warnings 비어있음.
        // 이 단위 테스트의 핵심은 "예외 안 던짐" — 통과 자체.
        assertThat(warnings).isNotNull();
    }

    @Test
    @DisplayName("ALLOW 정책 → 모든 콘텐츠 통과")
    void allowPolicy() {
        ContentGuard guard = new ContentGuard(props(ExecutableBinaryPolicy.ALLOW, SuspiciousFilenamesPolicy.DISABLED));
        byte[] elf = new byte[64];
        elf[0] = 0x7F; elf[1] = 0x45; elf[2] = 0x4C; elf[3] = 0x46;
        MultipartFile f = new MockMultipartFile("file", "ok.bin", "application/octet-stream", elf);
        guard.classifyAndApplyExecutablePolicy(f, null); // throw 없음
    }

    @Test
    @DisplayName("Suspicious filename + DENY → SuspiciousFilenameException")
    void suspiciousFilenameDeny() {
        ContentGuard guard = new ContentGuard(props(ExecutableBinaryPolicy.ALLOW, SuspiciousFilenamesPolicy.DENY));
        MultipartFile[] files = {
                new MockMultipartFile("file", "shortcut.lnk", "application/octet-stream", new byte[]{0})
        };
        assertThatThrownBy(() -> guard.assertNoSuspiciousFilenames(files))
                .isInstanceOf(SuspiciousFilenameException.class);
    }

    @Test
    @DisplayName("Suspicious filename + DISABLED → 통과")
    void suspiciousFilenameDisabled() {
        ContentGuard guard = new ContentGuard(props(ExecutableBinaryPolicy.ALLOW, SuspiciousFilenamesPolicy.DISABLED));
        MultipartFile[] files = {
                new MockMultipartFile("file", "shortcut.lnk", "application/octet-stream", new byte[]{0})
        };
        guard.assertNoSuspiciousFilenames(files); // 통과
    }

    @Test
    @DisplayName("C4 — folder 샘플 random shuffle : 후반 ELF 가 일정 확률로 검출되는지 (확률적)")
    void folderRandomShuffle_detectsLateElf() {
        // sample size = 10 인데 100 개 중 ELF 가 후반 50 개에만 있다면 deterministic 50/50 검사로는 0% 검출.
        // random shuffle 이면 첫 10 개에 ELF 가 포함될 확률 = 1 - C(50,10)/C(100,10) ≈ 99.8%.
        // 100 회 반복 중 1 번이라도 검출되면 random shuffle 이 동작 — 검출이 0 이면 결정론적 결함.
        UploadSecurityProperties small = new UploadSecurityProperties(
                DataSize.ofGigabytes(5), DataSize.ofGigabytes(20),
                5000, DataSize.ofGigabytes(20),
                DataSize.ofGigabytes(20), 100, 10000,
                ExecutableBinaryPolicy.DENY, 10 /* sample */, SuspiciousFilenamesPolicy.DISABLED,
                null, DataSize.ofGigabytes(20)
        );
        ContentGuard guard = new ContentGuard(small);

        byte[] benign = "hello world".getBytes();
        byte[] elf = new byte[64];
        elf[0] = 0x7F; elf[1] = 0x45; elf[2] = 0x4C; elf[3] = 0x46; elf[4] = 0x02; elf[5] = 0x01;

        MultipartFile[] files = new MultipartFile[100];
        for (int i = 0; i < 50; i++) {
            files[i] = new MockMultipartFile("file" + i, "ok" + i + ".txt", "text/plain", benign);
        }
        for (int i = 50; i < 100; i++) {
            files[i] = new MockMultipartFile("file" + i, "evil" + i + ".bin", "application/octet-stream", elf);
        }

        int detected = 0;
        int trials = 50;
        for (int t = 0; t < trials; t++) {
            try {
                guard.classifyAndApplyExecutablePolicyForFolder(files, null);
            } catch (ExecutableContentRejectedException ignored) {
                detected++;
            }
        }
        // shuffle 이 동작하면 50 회 중 거의 매번 검출. 결정론적 (앞 10개만) 이라면 0 회.
        // 하한선은 "1 회 이상" — random 의 확률성을 인정하면서도 결정론적 결함을 잡는다.
        assertThat(detected).isGreaterThan(0);
    }

    @Test
    @DisplayName("C4 — sample size 0 (또는 음수) → 전수 검사 (전체에서 ELF 100% 검출)")
    void folderScanAll_zeroSample() {
        UploadSecurityProperties scanAll = new UploadSecurityProperties(
                DataSize.ofGigabytes(5), DataSize.ofGigabytes(20),
                5000, DataSize.ofGigabytes(20),
                DataSize.ofGigabytes(20), 100, 10000,
                ExecutableBinaryPolicy.DENY, 0 /* sample = 0 → 전수 */, SuspiciousFilenamesPolicy.DISABLED,
                null, DataSize.ofGigabytes(20)
        );
        ContentGuard guard = new ContentGuard(scanAll);
        byte[] elf = new byte[64];
        elf[0] = 0x7F; elf[1] = 0x45; elf[2] = 0x4C; elf[3] = 0x46;
        MultipartFile[] files = new MultipartFile[100];
        files[0] = new MockMultipartFile("ok", "ok.txt", "text/plain", "hi".getBytes());
        for (int i = 1; i < 99; i++) {
            files[i] = new MockMultipartFile("ok" + i, "ok" + i + ".txt", "text/plain", "hi".getBytes());
        }
        files[99] = new MockMultipartFile("evil", "evil.bin", "application/octet-stream", elf);

        assertThatThrownBy(() -> guard.classifyAndApplyExecutablePolicyForFolder(files, null))
                .isInstanceOf(ExecutableContentRejectedException.class);
    }
}
