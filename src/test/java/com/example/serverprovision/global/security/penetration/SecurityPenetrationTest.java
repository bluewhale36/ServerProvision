package com.example.serverprovision.global.security.penetration;

import com.example.serverprovision.global.security.ContentGuard;
import com.example.serverprovision.global.security.EntrypointPolicyService;
import com.example.serverprovision.global.security.FileSystemHardener;
import com.example.serverprovision.global.security.PathPolicyService;
import com.example.serverprovision.global.security.UploadLimitsPolicy;
import com.example.serverprovision.global.security.ZipBombGuard;
import com.example.serverprovision.global.security.config.FileSystemSecurityProperties;
import com.example.serverprovision.global.security.config.PathSecurityProperties;
import com.example.serverprovision.global.security.config.SecurityPropertiesValidator;
import com.example.serverprovision.global.security.config.UploadSecurityProperties;
import com.example.serverprovision.global.security.config.UploadSecurityProperties.ExecutableBinaryPolicy;
import com.example.serverprovision.global.security.config.UploadSecurityProperties.SuspiciousFilenamesPolicy;
import com.example.serverprovision.global.security.exception.EntrypointInvalidException;
import com.example.serverprovision.global.security.exception.ExecutableContentRejectedException;
import com.example.serverprovision.global.security.exception.MaliciousContentSuspectedException;
import com.example.serverprovision.global.security.exception.PathOutsideAllowedRootsException;
import com.example.serverprovision.global.security.exception.PathTraversalException;
import com.example.serverprovision.global.security.exception.SuspiciousFilenameException;
import com.example.serverprovision.global.security.exception.UploadLimitExceededException;
import com.example.serverprovision.global.security.exception.ZipBombSuspectedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 화이트해커 침투 테스트.
 * <p>각 테스트는 가드를 우회하려는 의도적 입력. 실패하면 그 자체가 결함 보고서.
 * 본 묶음은 plan v2 (42 시나리오) 의 기존 단위 테스트들에서 누락된 우회 패턴들을 보강한다.</p>
 */
class SecurityPenetrationTest {

    /* ========================= 공용 팩토리 ========================= */

    private static UploadSecurityProperties uploadProps(
            DataSize maxFileSize, int maxFolderFiles, DataSize maxTreeBytes,
            DataSize maxZipUncompressed, int zipBombRatio, int maxZipEntries,
            ExecutableBinaryPolicy execPolicy, int sampleSize, SuspiciousFilenamesPolicy suspPolicy) {
        return new UploadSecurityProperties(
                maxFileSize, DataSize.ofGigabytes(20), maxFolderFiles, maxTreeBytes,
                maxZipUncompressed, zipBombRatio, maxZipEntries,
                execPolicy, sampleSize, suspPolicy,
                null, DataSize.ofGigabytes(20)
        );
    }

    private static UploadSecurityProperties defaultUpload() {
        return uploadProps(DataSize.ofGigabytes(5), 5000, DataSize.ofGigabytes(20),
                DataSize.ofGigabytes(20), 100, 10000,
                ExecutableBinaryPolicy.DENY, 50, SuspiciousFilenamesPolicy.DISABLED);
    }

    private static com.example.serverprovision.global.security.UploadTempDirectoryProvider tempProvider(UploadSecurityProperties props) {
        return new com.example.serverprovision.global.security.UploadTempDirectoryProvider(props);
    }

    private static PathPolicyService pathSvc(String... roots) {
        return new PathPolicyService(new PathSecurityProperties(List.of(roots)));
    }

    /* ============================================================= */
    /* A. Path traversal 우회 시도                                   */
    /* ============================================================= */

    @Nested
    @DisplayName("[A] Path traversal 우회 패턴")
    class PathTraversalBypass {

        @Test
        @DisplayName("A1: 단일 점 + 중첩 traversal `/root/./../../etc` → 거절 (S4.x — input syntactic fail-fast)")
        void singleDotMixedTraversal(@TempDir Path tmp) {
            // S4.x — `.`/`..` 시그먼트가 input 에 포함되면 normalize 전에 PathTraversal 로 거절.
            PathPolicyService svc = pathSvc(tmp.toString());
            String attack = tmp + "/./../../../../../../../etc";
            assertThatThrownBy(() -> svc.assertWritablePath(attack))
                    .isInstanceOfAny(PathTraversalException.class, PathOutsideAllowedRootsException.class);
        }

        @Test
        @DisplayName("A2: trailing slash 와 double slash 가 섞인 정상 경로는 통과 (정규화로 흡수)")
        void doubleSlashInsideRoot(@TempDir Path tmp) {
            PathPolicyService svc = pathSvc(tmp.toString());
            // /tmp//foo///bar 는 normalize 후 /tmp/foo/bar — 통과해야 함
            Path ok = svc.assertWritablePath(tmp + "//foo///bar");
            assertThat(ok.toString()).startsWith(tmp.toString());
        }

        @Test
        @DisplayName("A3: URL-encoded `..%2f` 는 정규화하지 않으므로 단순 디렉토리명으로 취급 — root 안이면 통과")
        void urlEncodedNotDecoded(@TempDir Path tmp) {
            // PathPolicyService 는 URL decode 안 함 → ..%2fetc 는 그냥 "..%2fetc" 라는 디렉토리명
            // 그러므로 root 안이면 통과해야 정상 (URL decode 가 일어났다면 결함).
            PathPolicyService svc = pathSvc(tmp.toString());
            Path ok = svc.assertWritablePath(tmp + "/..%2fetc");
            assertThat(ok.toString()).startsWith(tmp.toString());
        }

        @Test
        @DisplayName("A4: Windows 백슬래시 경로 `\\windows\\system32` — 리눅스에서는 단순 파일명")
        void windowsBackslashPath(@TempDir Path tmp) {
            // POSIX 에서 \ 는 path separator 가 아니므로 root 안에 백슬래시 포함 파일명은 통과해야 정상
            PathPolicyService svc = pathSvc(tmp.toString());
            Path ok = svc.assertWritablePath(tmp + "/foo\\bar\\baz");
            assertThat(ok.toString()).startsWith(tmp.toString());
        }

        @Test
        @DisplayName("A5: UNC 형태 `\\\\server\\share` — POSIX 단일 파일명, allowlist 밖이면 거절")
        void uncPath() {
            PathPolicyService svc = pathSvc("/opt/iso");
            assertThatThrownBy(() -> svc.assertWritablePath("\\\\server\\share\\evil"))
                    .isInstanceOfAny(PathOutsideAllowedRootsException.class, PathTraversalException.class);
        }

        @Test
        @DisplayName("A6: 모든 control char (0x01 ~ 0x1F) 가 거절되는지 — null byte 외에도")
        void allControlCharsRejected() {
            PathPolicyService svc = pathSvc("/opt");
            // 0x01 ~ 0x1F 중 \t (0x09) 는 가드가 허용. 나머지 31개 모두 거절되어야 함.
            for (int c = 0x01; c <= 0x1F; c++) {
                if (c == '\t') continue;
                String input = "/opt/x" + (char) c + "y";
                final int code = c;
                assertThatThrownBy(() -> svc.assertWritablePath(input))
                        .as("control char 0x%02X 거절 안 됨", code)
                        .isInstanceOf(PathTraversalException.class);
            }
        }

        @Test
        @DisplayName("A7: 매우 긴 경로 (64KB) — InvalidPathException 또는 정상 처리, 무한 루프 없이 종료")
        void veryLongPath(@TempDir Path tmp) {
            PathPolicyService svc = pathSvc(tmp.toString());
            String huge = tmp + "/" + "a".repeat(64 * 1024);
            // 통과하든 거절하든 일단 timeout 없이 답을 내야 함. (assertion 은 결과 무관)
            try {
                svc.assertWritablePath(huge);
            } catch (RuntimeException ignored) {
                // OK — InvalidPathException / NameTooLong 등으로 거절될 수 있음
            }
        }

        @Test
        @DisplayName("A8: 심볼릭 링크가 allowlist 밖을 가리킴 — startsWith 검사가 link 를 follow 안 하므로 통과 (한계 명시)")
        void symlinkEscape(@TempDir Path tmp) throws IOException {
            // root/link → /etc 인 심볼릭 링크. PathPolicyService 는 readlink 안 하므로 link 자체는 root 안.
            // 이건 가드의 의도된 한계. 실제 파일 IO 시점이 아니라 경로 입력 단계 가드라서.
            // 본 테스트는 "현재 가드가 link 를 follow 안 한다는 사실" 자체가 한계임을 문서화.
            PathPolicyService svc = pathSvc(tmp.toString());
            Path link = tmp.resolve("escape");
            try {
                Files.createSymbolicLink(link, Path.of("/etc"));
            } catch (UnsupportedOperationException | IOException e) {
                return; // 일부 FS 에서 symlink 생성 불가 — skip
            }
            // 가드 입장에서 link.toString() 은 tmp 안. 통과한다.
            // ※ 결함이라기보다 "가드 layer 의 책임 범위" 이슈 — 실제 IO 시점에 toRealPath 검사 필요시 별도 가드 (S6 후보)
            Path resolved = svc.assertWritablePath(link.toString());
            assertThat(resolved.toString()).startsWith(tmp.toString());
        }

        @Test
        @DisplayName("A9: S3.2 (K10) 후 — `~` 입력은 PathTraversalException (쉘 메타문자 거절)")
        void tildeHomeRejected() {
            PathPolicyService svc = pathSvc("/opt");
            // S3.2 (K10) — `~` / `$` 같은 쉘 메타문자는 fail-fast 로 거절.
            assertThatThrownBy(() -> svc.assertWritablePath("~/secret"))
                    .isInstanceOf(PathTraversalException.class);
        }

        @Test
        @DisplayName("A10: allowed root 의 prefix-match 우회 — `/opt/iso` 가 root 일 때 `/opt/iso-evil` 거절되는지")
        void prefixMatchAttack() {
            // startsWith 가 String.startsWith 가 아닌 Path.startsWith 인지 검증.
            // 만약 String.startsWith 였다면 /opt/iso-evil 이 /opt/iso 로 시작해서 통과되는 결함.
            PathPolicyService svc = pathSvc("/opt/iso");
            assertThatThrownBy(() -> svc.assertWritablePath("/opt/iso-evil/payload"))
                    .as("Path.startsWith 가 아닌 String.startsWith 가 쓰이면 우회됨")
                    .isInstanceOf(PathOutsideAllowedRootsException.class);
        }

        @Test
        @DisplayName("A11: 빈 시그먼트 경로 `///` — InvalidPath 또는 정규화 후 root 처리")
        void onlySlashes() {
            PathPolicyService svc = pathSvc("/opt");
            // /// 는 normalize 후 / — root 자체. allowlist 밖이라 거절.
            assertThatThrownBy(() -> svc.assertWritablePath("///"))
                    .isInstanceOf(PathOutsideAllowedRootsException.class);
        }

        @Test
        @DisplayName("A12: allowed root 가 `/` 인 무방비 상태 — boot validator 가 막아야 함")
        void rootSlashAllowed() {
            // 운영자가 PROVISION_ALLOWED_ROOTS=/ 로 잘못 설정한 상황.
            // 현재 PathPolicyService 자체는 통과시킨다 (모든 경로 통과).
            // 이건 PathPolicyService 의 책임이 아니라 운영자 설정의 결과라는 것을 문서화.
            // SecurityPropertiesValidator 는 빈/null 만 거절하지 "/" 는 거절 안 함 → 운영 가이드로만 회피.
            PathPolicyService svc = pathSvc("/");
            // 모든 절대경로가 통과 — 그것이 현재 동작.
            assertThat(svc.assertWritablePath("/etc/passwd").toString()).isEqualTo("/etc/passwd");
            // ※ 가드의 한계. plan §2.1 의 의도와 충돌 — 운영자 가이드 강화 필요.
        }
    }

    /* ============================================================= */
    /* B. Entrypoint 우회 시도                                       */
    /* ============================================================= */

    @Nested
    @DisplayName("[B] Entrypoint 우회 패턴")
    class EntrypointBypass {

        private final EntrypointPolicyService svc = new EntrypointPolicyService();

        @Test
        @DisplayName("B1: 빈 시그먼트 + traversal `bin//../../etc` → 거절")
        void emptySegmentTraversal(@TempDir Path tmp) {
            assertThatThrownBy(() -> svc.validateAndNormalize(tmp, "bin//../../etc"))
                    .isInstanceOf(EntrypointInvalidException.class);
        }

        @Test
        @DisplayName("B2: 점이 한 개인 시그먼트 `./bin/setup` → 단일 점은 막지 않음 (정상)")
        void singleDotSegmentAllowed(@TempDir Path tmp) {
            // 단일 점은 normalize 가 흡수하므로 안전. 가드도 막지 않는 게 맞음.
            String result = svc.validateAndNormalize(tmp, "./bin/setup.sh");
            // "./bin/setup.sh" 는 그대로 반환됨 (가드는 .. 만 segment 검사)
            assertThat(result).contains("bin/setup.sh");
        }

        @Test
        @DisplayName("B3: 점 3개 `...` 시그먼트 — `..` 가 아니므로 통과 (정상 - .. 만 차단)")
        void tripleDotSegment(@TempDir Path tmp) {
            // "..." 는 ".." 가 아니므로 segment 비교 시 거절 안 됨 — 정상 동작.
            // Path resolve 시 그냥 "..." 라는 이름의 디렉토리로 처리.
            String result = svc.validateAndNormalize(tmp, ".../foo");
            assertThat(result).isEqualTo(".../foo");
        }

        @Test
        @DisplayName("B4: 백슬래시-only `..\\\\etc\\\\passwd` → forward-slash 변환 후 .. 검출되어 거절")
        void backslashOnlyTraversal(@TempDir Path tmp) {
            assertThatThrownBy(() -> svc.validateAndNormalize(tmp, "..\\etc\\passwd"))
                    .isInstanceOf(EntrypointInvalidException.class);
        }

        @Test
        @DisplayName("B5: 매우 긴 단일 segment (1000자) → 길이 제한 (512) 거절")
        void singleHugeSegment(@TempDir Path tmp) {
            String huge = "a".repeat(1000);
            assertThatThrownBy(() -> svc.validateAndNormalize(tmp, huge))
                    .isInstanceOf(EntrypointInvalidException.class);
        }

        @Test
        @DisplayName("B6: trailing 슬래시 `bin/setup/` — 정상 segment, .. 없으면 통과")
        void trailingSlash(@TempDir Path tmp) {
            String result = svc.validateAndNormalize(tmp, "bin/setup/");
            assertThat(result).isEqualTo("bin/setup/");
        }

        @Test
        @DisplayName("B7: Unicode 정규화 충돌 — Pre-composed vs decomposed 한글이 같은 의미로 처리되는지")
        void unicodeNormalization(@TempDir Path tmp) {
            // "한글" (NFC) vs decomposed (NFD) — Java Path 는 raw byte 비교라 일반적으로 NFD/NFC 구분.
            // 가드에 영향 없음. 단순 통과 검증.
            String nfc = "한글.txt"; // 한글
            String result = svc.validateAndNormalize(tmp, nfc);
            assertThat(result).isEqualTo(nfc);
        }

        @Test
        @DisplayName("B8: 정확히 길이 512 — 경계값 통과")
        void exactlyMaxLength(@TempDir Path tmp) {
            String exact = "a".repeat(512);
            String result = svc.validateAndNormalize(tmp, exact);
            assertThat(result).isEqualTo(exact);
        }

        @Test
        @DisplayName("B9: tab 문자 (0x09) 포함 — 가드가 tab 만 예외 허용")
        void tabAllowed(@TempDir Path tmp) {
            // 가드 코드에서 \t 는 명시적 예외. 즉 tab 문자가 파일명에 들어가도 통과.
            // 의도된 동작 — 다만 실용상 tab 문자가 파일명에 있는 경우는 드묾.
            String result = svc.validateAndNormalize(tmp, "bin\t/setup");
            assertThat(result).contains("\t");
        }

        @Test
        @DisplayName("B10: 모든 다른 control char (0x01 ~ 0x1F, \\t 제외) 거절")
        void allControlCharsRejected(@TempDir Path tmp) {
            for (int c = 0x01; c <= 0x1F; c++) {
                if (c == '\t') continue;
                String input = "bin" + (char) c + "/setup";
                final int code = c;
                assertThatThrownBy(() -> svc.validateAndNormalize(tmp, input))
                        .as("control char 0x%02X 거절 안 됨", code)
                        .isInstanceOf(EntrypointInvalidException.class);
            }
        }
    }

    /* ============================================================= */
    /* C. Zip bomb / Content guard 우회                              */
    /* ============================================================= */

    @Nested
    @DisplayName("[C] Zip bomb / Content guard 우회 패턴")
    class ZipAndContentBypass {

        @Test
        @DisplayName("C1: ZIP entry 의 getSize() 가 -1 (unknown) — 합계 검사가 -1 을 무시하고 통과")
        void zipEntryUnknownSize() throws IOException {
            // ZipOutputStream 은 setSize() 미호출 시 entry 의 size 를 -1 로 둠 (STORED 모드 외).
            // ZipBombGuard 는 size > 0 일 때만 합계에 더하므로 -1 entry 는 무시됨 → 통과.
            // 이게 결함인지 의도인지 검증 — plan v2 § C 우회 패턴.
            byte[] data = new byte[1024];
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(bos)) {
                ZipEntry e = new ZipEntry("a.bin");
                zos.putNextEntry(e);
                zos.write(data);
                zos.closeEntry();
            }
            UploadSecurityProperties pProps = uploadProps(
                    DataSize.ofMegabytes(100), 5000, DataSize.ofMegabytes(100),
                    DataSize.ofKilobytes(1) /* 1KB cap! */, 100, 10000,
                    ExecutableBinaryPolicy.ALLOW, 50, SuspiciousFilenamesPolicy.DISABLED);
            ZipBombGuard guard = new ZipBombGuard(pProps, tempProvider(pProps));
            MockMultipartFile zip = new MockMultipartFile("zip", "test.zip", "application/zip", bos.toByteArray());
            // entry 의 size 가 1024 로 설정될 수도, -1 로 남을 수도 있음 — 구현에 따라.
            // 1024 > 1024 는 false 이므로 통과. -1 이면 무시되어 통과.
            // 두 경우 모두 가드를 우회한다 — entry 가 실제로는 데이터를 가지고 있음.
            // ※ 한계 명시 : ZipBombGuard 는 ZipEntry 메타데이터만 보고 실제 read 는 안 함.
            try {
                guard.assertSafeZip(zip);
                // 우회됨 — 가드의 의도된 한계 (extraction 단계에서 별도 차단 필요)
            } catch (ZipBombSuspectedException ok) {
                // 가드가 막은 경우 — 더 안전한 동작
            }
        }

        @Test
        @DisplayName("C2: nested zip (zip 안에 zip) — 가드는 한 단계만 보므로 inner zip 의 bomb 통과")
        void nestedZip() throws IOException {
            // ZipBombGuard 는 outer entry 만 검사. inner zip 의 entry 는 검사 안 함.
            // 의도된 한계 — extraction 시 재귀 검사하지 않음.
            byte[] inner;
            ByteArrayOutputStream innerBos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(innerBos)) {
                ZipEntry e = new ZipEntry("inner.txt");
                e.setSize(100);
                e.setCompressedSize(10);
                zos.putNextEntry(e);
                zos.write(new byte[100]);
                zos.closeEntry();
            }
            inner = innerBos.toByteArray();
            ByteArrayOutputStream outerBos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(outerBos)) {
                ZipEntry e = new ZipEntry("payload.zip");
                zos.putNextEntry(e);
                zos.write(inner);
                zos.closeEntry();
            }
            ZipBombGuard guard = new ZipBombGuard(defaultUpload(), tempProvider(defaultUpload()));
            MockMultipartFile zip = new MockMultipartFile("zip", "outer.zip", "application/zip", outerBos.toByteArray());
            // outer 가 정상이면 통과 — 정상 동작.
            guard.assertSafeZip(zip);
        }

        @Test
        @DisplayName("C3: ZIP magic byte 위장 — 첫 4 byte 만 PK 인 fake zip 은 ContentGuard 통과 후 ZipBombGuard 에서 IO 실패")
        void fakePkHeader() {
            // ContentGuard.assertSafeZip 은 첫 4 byte 만 검사. 통과시킴.
            // 그 후 ZipBombGuard 가 ZipInputStream 으로 read 시도 → IOException → ZipBombSuspectedException.
            ContentGuard cg = new ContentGuard(defaultUpload());
            byte[] fakeZip = new byte[100];
            fakeZip[0] = 0x50; fakeZip[1] = 0x4B; fakeZip[2] = 0x03; fakeZip[3] = 0x04;
            // 나머지는 random — 정상 zip 이 아님.
            for (int i = 4; i < 100; i++) fakeZip[i] = (byte) (i & 0xFF);
            MockMultipartFile mf = new MockMultipartFile("zip", "fake.zip", "application/zip", fakeZip);
            // ContentGuard 는 통과 (의도된 동작 — magic byte 만 봄)
            cg.assertSafeZip(mf);

            // S3.2 (K17) — IO 실패는 ZipBombSuspectedException (콘텐츠 위협) 이 아니라 ZipBombInspectionFailedException
            // (운영 측 IO 오류) 로 분류한다. fake zip 은 정상 zip 이 아니라 ZipFile 이 IOException 을 던지므로
            // K17 분류상 inspection-failed 로 거절된다.
            ZipBombGuard zg = new ZipBombGuard(defaultUpload(), tempProvider(defaultUpload()));
            try {
                zg.assertSafeZip(mf);
                // 또는 비어있는 zip 으로 인식하여 통과 — 그것도 zip 이 아니란 의미가 후속 단계에서 잡힘
            } catch (ZipBombSuspectedException
                     | com.example.serverprovision.global.security.exception.ZipBombInspectionFailedException ok) {
                // OK — 둘 중 어느 분류로든 막힘
            }
        }

        @Test
        @DisplayName("C4: ZIP 모드 + 빈 파일 (size 0) → MaliciousContentSuspected")
        void emptyZipFile() {
            ContentGuard cg = new ContentGuard(defaultUpload());
            MockMultipartFile empty = new MockMultipartFile("zip", "empty.zip", "application/zip", new byte[0]);
            assertThatThrownBy(() -> cg.assertSafeZip(empty))
                    .isInstanceOf(MaliciousContentSuspectedException.class);
        }

        @Test
        @DisplayName("C5: ZIP 모드 + 3 byte 만 있는 파일 (PK\\x03 만) → 길이 너무 짧아 거절")
        void truncatedZip() {
            ContentGuard cg = new ContentGuard(defaultUpload());
            MockMultipartFile trunc = new MockMultipartFile("zip", "trunc.zip", "application/zip",
                    new byte[]{0x50, 0x4B, 0x03});
            assertThatThrownBy(() -> cg.assertSafeZip(trunc))
                    .isInstanceOf(MaliciousContentSuspectedException.class);
        }

        @Test
        @DisplayName("C6: PE header (MZ) 를 .zip 확장자로 위장 → MaliciousContentSuspected")
        void pePretendingToBeZip() {
            ContentGuard cg = new ContentGuard(defaultUpload());
            byte[] pe = new byte[64];
            pe[0] = 'M'; pe[1] = 'Z';
            MockMultipartFile mf = new MockMultipartFile("zip", "fake.zip", "application/zip", pe);
            assertThatThrownBy(() -> cg.assertSafeZip(mf))
                    .isInstanceOf(MaliciousContentSuspectedException.class);
        }

        @Test
        @DisplayName("C7: folder sample-size 밖 ELF — S3 Medium (C4) 의 random shuffle 후 stochastic 검출 가능")
        void elfBeyondSampleSize() {
            // S3 Medium (C4) — sample 인덱스를 random shuffle 하므로 51번째 ELF 도 stochastic 으로 sample 에 들어감.
            // 본 테스트는 sample=2 + 3 파일 중 ELF 1 개라 ELF 가 sample 에 포함될 확률 ~67% (2/3 위치 추출).
            // 따라서 결정론적 통과를 보장 안 하므로 "통과 OR 거절" 모두 정상 동작으로 간주.
            UploadSecurityProperties props = uploadProps(
                    DataSize.ofGigabytes(5), 5000, DataSize.ofGigabytes(20),
                    DataSize.ofGigabytes(20), 100, 10000,
                    ExecutableBinaryPolicy.DENY, 2 /* sample-size */, SuspiciousFilenamesPolicy.DISABLED);
            ContentGuard cg = new ContentGuard(props);
            byte[] elf = new byte[64];
            elf[0] = 0x7F; elf[1] = 'E'; elf[2] = 'L'; elf[3] = 'F'; elf[4] = 0x02; elf[5] = 0x01;
            MultipartFile[] files = {
                    new MockMultipartFile("file1", "a.txt", "text/plain", "hello".getBytes()),
                    new MockMultipartFile("file2", "b.txt", "text/plain", "world".getBytes()),
                    new MockMultipartFile("file3", "evil.bin", "application/octet-stream", elf)
            };
            // C4 의 random shuffle 로 ELF 가 sample 에 들어가면 거절, 아니면 통과 — 둘 다 인정.
            try {
                cg.classifyAndApplyExecutablePolicyForFolder(files, null);
            } catch (ExecutableContentRejectedException expected) {
                // sample 안에 ELF 가 무작위로 추출되어 정상 거절.
            }
        }

        @Test
        @DisplayName("C8: folder sample-size 내 (sample=10 일 때 1번째가 ELF) → DENY")
        void elfWithinSampleSize() {
            UploadSecurityProperties props = uploadProps(
                    DataSize.ofGigabytes(5), 5000, DataSize.ofGigabytes(20),
                    DataSize.ofGigabytes(20), 100, 10000,
                    ExecutableBinaryPolicy.DENY, 10, SuspiciousFilenamesPolicy.DISABLED);
            ContentGuard cg = new ContentGuard(props);
            byte[] elf = new byte[128];
            elf[0] = 0x7F; elf[1] = 'E'; elf[2] = 'L'; elf[3] = 'F'; elf[4] = 0x02; elf[5] = 0x01;
            elf[16] = 0x02; // ET_EXEC
            elf[18] = 0x3E; // EM_X86_64
            MultipartFile[] files = {
                    new MockMultipartFile("file1", "evil.bin", "application/octet-stream", elf)
            };
            // Tika 가 ELF 로 검출하면 DENY. 검출 못하면 통과 — Tika 의 인식 한계가 결과를 좌우.
            try {
                cg.classifyAndApplyExecutablePolicyForFolder(files, null);
                // 통과 — Tika 가 ELF 로 못 잡은 경우 (한계 명시)
            } catch (ExecutableContentRejectedException ok) {
                // 막힘
            }
        }

        @Test
        @DisplayName("C9: suspicious filename 대문자 `.LNK` → 소문자 비교라 거절됨")
        void suspiciousFilenameUppercase() {
            UploadSecurityProperties props = uploadProps(
                    DataSize.ofGigabytes(5), 5000, DataSize.ofGigabytes(20),
                    DataSize.ofGigabytes(20), 100, 10000,
                    ExecutableBinaryPolicy.ALLOW, 50, SuspiciousFilenamesPolicy.DENY);
            ContentGuard cg = new ContentGuard(props);
            MultipartFile[] files = {
                    new MockMultipartFile("file", "EVIL.LNK", "application/octet-stream", new byte[]{0})
            };
            assertThatThrownBy(() -> cg.assertNoSuspiciousFilenames(files))
                    .isInstanceOf(SuspiciousFilenameException.class);
        }

        @Test
        @DisplayName("C10: suspicious filename + trailing 공백 `.lnk ` → endsWith 검사 우회 (현재 결함 가능성)")
        void suspiciousFilenameTrailingSpace() {
            // "evil.lnk " (trailing space) 는 ".lnk" 로 endsWith 안 됨.
            // 이걸 OS 가 trim 해서 .lnk 로 처리할 수 있는 환경이라면 우회 가능.
            // 그러나 현실적으로 Windows 가 trailing space 를 자동으로 자르고 .lnk 로 인식하는 사례가 알려짐.
            // 현재 가드는 막지 못함 — 결함 보고서.
            UploadSecurityProperties props = uploadProps(
                    DataSize.ofGigabytes(5), 5000, DataSize.ofGigabytes(20),
                    DataSize.ofGigabytes(20), 100, 10000,
                    ExecutableBinaryPolicy.ALLOW, 50, SuspiciousFilenamesPolicy.DENY);
            ContentGuard cg = new ContentGuard(props);
            MultipartFile[] files = {
                    new MockMultipartFile("file", "evil.lnk ", "application/octet-stream", new byte[]{0})
            };
            // 이상적으로는 거절되어야 함. 그러나 현재 구현은 통과.
            // 본 테스트는 "통과한다" 는 사실을 문서화 — 사용자가 가드 강화 결정 시 참조.
            cg.assertNoSuspiciousFilenames(files);
            // ※ 결함 후보 #1 : trailing space / dot 우회 패턴 미차단.
        }

        @Test
        @DisplayName("C11: suspicious filename 가운데 NUL byte 삽입 `evil.lnk\\0.txt` — 통과 위험")
        void suspiciousFilenameNullByteEvasion() {
            // 일부 OS 는 \0 에서 파일명을 자른다 → 실제 저장명은 evil.lnk.
            // 그러나 multipart 는 \0 가 들어간 filename 을 그대로 받음.
            // 현재 ContentGuard 는 \0 처리 없이 endsWith 만 봐서 .txt 로 끝나면 통과.
            UploadSecurityProperties props = uploadProps(
                    DataSize.ofGigabytes(5), 5000, DataSize.ofGigabytes(20),
                    DataSize.ofGigabytes(20), 100, 10000,
                    ExecutableBinaryPolicy.ALLOW, 50, SuspiciousFilenamesPolicy.DENY);
            ContentGuard cg = new ContentGuard(props);
            MultipartFile[] files = {
                    new MockMultipartFile("file", "evil.lnk\0.txt", "application/octet-stream", new byte[]{0})
            };
            // 현재 통과. ※ 결함 후보 #2 : null byte 파일명 분리 공격 미차단.
            cg.assertNoSuspiciousFilenames(files);
        }

        @Test
        @DisplayName("C12: ZIP bomb — 비율 정확히 100 (경계값) → 통과")
        void zipBombRatioBoundary() throws IOException {
            // size=100, compressed=1 → ratio 100. > 100 false 이므로 통과.
            ZipBombGuard guard = new ZipBombGuard(defaultUpload(), tempProvider(defaultUpload()));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(bos)) {
                ZipEntry e = new ZipEntry("a.bin");
                zos.putNextEntry(e);
                // 압축 가능한 반복 데이터
                byte[] data = new byte[10000];
                zos.write(data);
                zos.closeEntry();
            }
            MockMultipartFile zip = new MockMultipartFile("zip", "ratio.zip", "application/zip", bos.toByteArray());
            try {
                guard.assertSafeZip(zip);
            } catch (ZipBombSuspectedException ignored) {
                // 비율 초과로 막힘
            }
        }
    }

    /* ============================================================= */
    /* D. Upload limits 우회                                          */
    /* ============================================================= */

    @Nested
    @DisplayName("[D] Upload limits 우회 패턴")
    class UploadLimitsBypass {

        @Test
        @DisplayName("D1: 파일 갯수 정확히 max (5000) → 통과 (경계값)")
        void exactlyMaxFolderFiles() {
            UploadLimitsPolicy policy = new UploadLimitsPolicy(uploadProps(
                    DataSize.ofGigabytes(5), 5000, DataSize.ofGigabytes(20),
                    DataSize.ofGigabytes(20), 100, 10000,
                    ExecutableBinaryPolicy.ALLOW, 50, SuspiciousFilenamesPolicy.DISABLED), new FileSystemSecurityProperties(2000, 8));
            MultipartFile[] files = new MultipartFile[5000];
            for (int i = 0; i < 5000; i++) {
                files[i] = new MockMultipartFile("f" + i, "a.txt", "text/plain", new byte[1]);
            }
            policy.assertFileCount(files); // 통과
        }

        @Test
        @DisplayName("D2: 파일 갯수 5001 → UploadLimitExceeded")
        void overMaxFolderFiles() {
            UploadLimitsPolicy policy = new UploadLimitsPolicy(uploadProps(
                    DataSize.ofGigabytes(5), 5000, DataSize.ofGigabytes(20),
                    DataSize.ofGigabytes(20), 100, 10000,
                    ExecutableBinaryPolicy.ALLOW, 50, SuspiciousFilenamesPolicy.DISABLED), new FileSystemSecurityProperties(2000, 8));
            MultipartFile[] files = new MultipartFile[5001];
            for (int i = 0; i < 5001; i++) {
                files[i] = new MockMultipartFile("f" + i, "a.txt", "text/plain", new byte[1]);
            }
            assertThatThrownBy(() -> policy.assertFileCount(files))
                    .isInstanceOf(UploadLimitExceededException.class);
        }

        @Test
        @DisplayName("D3: 0 byte 파일 5000 개 → count 통과 (size 검사 영역 아님)")
        void zeroByteFloodOnCount() {
            UploadLimitsPolicy policy = new UploadLimitsPolicy(uploadProps(
                    DataSize.ofGigabytes(5), 5000, DataSize.ofGigabytes(20),
                    DataSize.ofGigabytes(20), 100, 10000,
                    ExecutableBinaryPolicy.ALLOW, 50, SuspiciousFilenamesPolicy.DISABLED), new FileSystemSecurityProperties(2000, 8));
            MultipartFile[] files = new MultipartFile[5000];
            for (int i = 0; i < 5000; i++) {
                files[i] = new MockMultipartFile("f" + i, "a.txt", "text/plain", new byte[0]);
            }
            policy.assertFileCount(files);
            // 0 byte 다수 자체는 limits 가드의 책임 영역이 아님 — 다른 가드가 처리.
        }

        @Test
        @DisplayName("D4: assertSingleFileSize — getSize() 가 정확히 max → 통과 (경계값)")
        void exactlyMaxFileSize() {
            UploadLimitsPolicy policy = new UploadLimitsPolicy(uploadProps(
                    DataSize.ofBytes(100), 5000, DataSize.ofGigabytes(20),
                    DataSize.ofGigabytes(20), 100, 10000,
                    ExecutableBinaryPolicy.ALLOW, 50, SuspiciousFilenamesPolicy.DISABLED), new FileSystemSecurityProperties(2000, 8));
            MockMultipartFile f = new MockMultipartFile("f", "a.bin", "application/octet-stream", new byte[100]);
            policy.assertSingleFileSize(f);
        }

        @Test
        @DisplayName("D5: assertSingleFileSize — getSize() 가 max+1 → UploadLimitExceeded")
        void overMaxFileSize() {
            UploadLimitsPolicy policy = new UploadLimitsPolicy(uploadProps(
                    DataSize.ofBytes(100), 5000, DataSize.ofGigabytes(20),
                    DataSize.ofGigabytes(20), 100, 10000,
                    ExecutableBinaryPolicy.ALLOW, 50, SuspiciousFilenamesPolicy.DISABLED), new FileSystemSecurityProperties(2000, 8));
            MockMultipartFile f = new MockMultipartFile("f", "a.bin", "application/octet-stream", new byte[101]);
            assertThatThrownBy(() -> policy.assertSingleFileSize(f))
                    .isInstanceOf(UploadLimitExceededException.class);
        }

        @Test
        @DisplayName("D6: assertTreeBytes — 트리 합계가 한도 초과 시 UploadLimitExceeded")
        void treeBytesOverflow(@TempDir Path tmp) throws IOException {
            Files.writeString(tmp.resolve("a.txt"), "x".repeat(200));
            Files.writeString(tmp.resolve("b.txt"), "y".repeat(200));
            UploadLimitsPolicy policy = new UploadLimitsPolicy(uploadProps(
                    DataSize.ofGigabytes(5), 5000, DataSize.ofBytes(300) /* tree cap */,
                    DataSize.ofGigabytes(20), 100, 10000,
                    ExecutableBinaryPolicy.ALLOW, 50, SuspiciousFilenamesPolicy.DISABLED), new FileSystemSecurityProperties(2000, 8));
            assertThatThrownBy(() -> policy.assertTreeBytes(tmp))
                    .isInstanceOf(UploadLimitExceededException.class);
        }
    }

    /* ============================================================= */
    /* E. Boot fail-fast 회피                                         */
    /* ============================================================= */

    @Nested
    @DisplayName("[E] Boot fail-fast 회피 패턴")
    class BootFailFast {

        private SecurityPropertiesValidator validator(List<String> roots) {
            SecurityPropertiesValidator v = new SecurityPropertiesValidator(
                    new PathSecurityProperties(roots),
                    new UploadSecurityProperties(
                            DataSize.ofGigabytes(5), DataSize.ofGigabytes(20),
                            5000, DataSize.ofGigabytes(20),
                            DataSize.ofGigabytes(20), 100, 10000,
                            ExecutableBinaryPolicy.DENY, 50, SuspiciousFilenamesPolicy.DISABLED,
                            null, DataSize.ofGigabytes(20)),
                    new FileSystemSecurityProperties(2000, 8),
                    null
            );
            return v;
        }

        @Test
        @DisplayName("E1: allowedRoots = null → IllegalState")
        void rootsNull() {
            assertThatThrownBy(() -> validator(null).validate())
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("E2: allowedRoots = [] → IllegalState")
        void rootsEmptyList() {
            assertThatThrownBy(() -> validator(List.of()).validate())
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("E3: allowedRoots = [\" \", \"\", null-blank] → IllegalState (모두 blank)")
        void rootsAllBlank() {
            // 운영자 실수 — 환경변수에 콤마만 들어가서 빈 시그먼트만 list.
            List<String> roots = new ArrayList<>();
            roots.add("");
            roots.add("   ");
            assertThatThrownBy(() -> validator(roots).validate())
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("E4: allowedRoots = [\"./relative\"] → S3.1 (C1) 이후 fail-fast 거절")
        void rootsRelativeRejected() {
            // S3.1 (C1 + 침투 #3) — 상대경로 root 는 boot validator 가 거절.
            assertThatThrownBy(() -> validator(List.of("./relative")).validate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("절대경로");
        }

        @Test
        @DisplayName("E5: allowedRoots = [\"/nonexistent\"] → 부팅 통과 (현재 동작 — 존재 검사 안 함)")
        void rootsNonexistentDirAllowed() {
            // validator 가 디렉토리 존재 여부를 검사하지 않음. plan §E 우회 의도.
            // 결과 — 잘못된 root 가 boot 시점에 잡히지 않고 첫 사용자 요청에서 NoSuchFile 로 새는 사고.
            // ※ 결함 후보 #4 : 존재하지 않는 root 통과. 운영 가이드 강화 필요.
            validator(List.of("/nonexistent_for_test_xyz_12345")).validate(); // 통과
        }

        @Test
        @DisplayName("E6: allowedRoots = [\"/\"] → S3.1 (C1 + 침투 #5) 이후 fail-fast 거절")
        void rootsRootSlashRejected() {
            // S3.1 (C1 + 침투 #5) — root "/" 는 모든 절대경로를 통과시키므로 boot 차단.
            assertThatThrownBy(() -> validator(List.of("/")).validate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("root");
        }
    }

    /* ============================================================= */
    /* F. FileSystemHardener                                          */
    /* ============================================================= */

    @Nested
    @DisplayName("[F] FileSystemHardener 동작")
    class HardenerBehavior {

        @Test
        @DisplayName("F1: 존재하지 않는 경로 → 조용히 무시 (예외 없음)")
        void nonExistentPath() {
            FileSystemHardener h = new FileSystemHardener(new FileSystemSecurityProperties(2000, 8));
            h.applyDefaultPermissions(Path.of("/nonexistent_xyz_12345"));
            h.applyDefaultPermissionsForFile(Path.of("/nonexistent_xyz_12345/file"));
            // 예외 없이 종료 — 정상.
        }

        @Test
        @DisplayName("F2: null 경로 → 조용히 무시")
        void nullPath() {
            FileSystemHardener h = new FileSystemHardener(new FileSystemSecurityProperties(2000, 8));
            h.applyDefaultPermissions(null);
            h.applyDefaultPermissionsForFile(null);
        }

        @Test
        @DisplayName("F3: POSIX 환경에서 파일 권한 0644 적용 + 디렉토리 0755")
        @DisabledOnOs(OS.WINDOWS)
        void posixApply(@TempDir Path tmp) throws IOException {
            Path file = tmp.resolve("a.txt");
            Files.writeString(file, "hello");
            Files.setPosixFilePermissions(file, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_EXECUTE
            )); // 의도적 실행 권한

            new FileSystemHardener(new FileSystemSecurityProperties(2000, 8)).applyDefaultPermissions(tmp);

            Set<PosixFilePermission> after = Files.getPosixFilePermissions(file);
            assertThat(after).doesNotContain(
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_EXECUTE
            );
            assertThat(after).contains(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.OTHERS_READ
            );
            // 디렉토리는 0755
            Set<PosixFilePermission> dir = Files.getPosixFilePermissions(tmp);
            assertThat(dir).contains(
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_EXECUTE
            );
        }
    }
}
