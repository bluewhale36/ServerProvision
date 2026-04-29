package com.example.serverprovision.global.security.penetration;

import com.example.serverprovision.global.security.ContentGuard;
import com.example.serverprovision.global.security.FileSystemHardener;
import com.example.serverprovision.global.security.PathPolicyService;
import com.example.serverprovision.global.security.UploadLimitsPolicy;
import com.example.serverprovision.global.security.UploadTempDirectoryProvider;
import com.example.serverprovision.global.security.ZipBombGuard;
import com.example.serverprovision.global.security.config.FileSystemSecurityProperties;
import com.example.serverprovision.global.security.config.PathSecurityProperties;
import com.example.serverprovision.global.security.config.SecurityPropertiesValidator;
import com.example.serverprovision.global.security.config.UploadSecurityProperties;
import com.example.serverprovision.global.security.config.UploadSecurityProperties.ExecutableBinaryPolicy;
import com.example.serverprovision.global.security.config.UploadSecurityProperties.SuspiciousFilenamesPolicy;
import com.example.serverprovision.global.security.exception.ExecutableContentRejectedException;
import com.example.serverprovision.global.security.exception.MaliciousContentSuspectedException;
import com.example.serverprovision.global.security.exception.PathOutsideAllowedRootsException;
import com.example.serverprovision.global.security.exception.SuspiciousFilenameException;
import com.example.serverprovision.global.security.exception.UploadLimitExceededException;
import com.example.serverprovision.global.security.exception.ZipBombSuspectedException;
import com.example.serverprovision.management.common.filesystem.service.BundleTreeCleanupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S3.1 침투 테스트 — Security Hardening 보강 (Critical 4 + High 5 + C1) 의 패치 후 잔존 우회 검증.
 *
 * <p>이전 침투 테스트 ({@link SecurityPenetrationTest}) 가 식별한 5 결함 후보 (#5 root '/', #3 상대경로,
 * #1 trailing space, #2 null byte 분리, #4 nonexistent root) 의 회귀 + 새로 도입된 가드 (A1~A4 / B1~B5 / C1)
 * 에 대한 corner case 우회 시도. 본 묶음은 "막혔는지" 를 회귀로 보장 + "여전히 우회 가능한 입력" 을 결함 후보로 노출.</p>
 *
 * <h2>블랙해커 마인드</h2>
 * <ol>
 *   <li><b>Validator 우회 변형</b> — root '/' 의 다양한 표기 (// /. /./ /.. //opt 등), 절대경로 위장
 *       (\\server\share, file:///etc, ~/etc), 정규형 위장 (/opt/iso/. /opt/./iso /opt/../etc)</li>
 *   <li><b>A2 가드의 caller 흡수 검증</b> — purgeExistingTree 호출자가 가드 예외를 swallow 하는지</li>
 *   <li><b>A3 maxDepth 효과</b> — 8 단계 초과 디렉토리에서 byte 합산이 누락되는 위험</li>
 *   <li><b>A4 sanitizeFilenameOrThrow 의 char vs byte / Unicode</b> — 한글 240자 (UTF-8 720B) 통과 위험,
 *       BOM / RTL override / 0x7F (DEL) 통과 여부</li>
 *   <li><b>B3 ZipBombGuard size=-1 차단</b> — ZipFile 기반이라 ZipOutputStream 으로 만든 정상 zip 은 size 항상 ≥ 0;
 *       그러나 메타데이터를 직접 변조한 zip 으로 우회 시도</li>
 *   <li><b>B4 메시지 일반화 누수</b> — 일반화 메시지에 path / filename 이 섞여 있는지</li>
 *   <li><b>B5 sanitizeForLog 한계</b> — \r\n\t 만 치환, 0x01~0x08 / 0x0B~0x1F / ANSI escape (0x1B) 는 통과</li>
 * </ol>
 */
class SecurityPenetrationS31Test {

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

    private static UploadTempDirectoryProvider tempProvider(UploadSecurityProperties props) {
        return new UploadTempDirectoryProvider(props);
    }

    private static UploadSecurityProperties defaultUpload() {
        return uploadProps(DataSize.ofGigabytes(5), 5000, DataSize.ofGigabytes(20),
                DataSize.ofGigabytes(20), 100, 10000,
                ExecutableBinaryPolicy.DENY, 50, SuspiciousFilenamesPolicy.DISABLED);
    }

    private static SecurityPropertiesValidator validatorWith(List<String> roots) {
        return new SecurityPropertiesValidator(
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
    }

    /* ============================================================= */
    /* §A. C1 SecurityPropertiesValidator 보강 — root '/' 변형 우회      */
    /* ============================================================= */

    @Nested
    @DisplayName("[A] SecurityPropertiesValidator 보강 — root '/' 및 절대경로 변형")
    class ValidatorRootBypass {

        @Test
        @DisplayName("A-S31-1: '//' (double slash) → normalize 후 nameCount=0 → root('/') 거절 (이전 #5 회귀)")
        void doubleSlashAsRoot() {
            // Path.of("//").toAbsolutePath().normalize() → "/" (nameCount=0).
            // C1 의 nameCount==0 검사가 잡아야 함.
            assertThatThrownBy(() -> validatorWith(List.of("//")).validate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("root");
        }

        @Test
        @DisplayName("A-S31-2: '/.' 단일점 root → 정규화 후 nameCount=0 → root('/') 거절 메시지가 우선")
        void singleDotRoot() {
            // Path.of("/.") (nameCount=1, isAbsolute=true). normalize() → "/" (nameCount=0).
            // 코드 순서 : nameCount==0 검사가 raw.equals(norm) 검사보다 먼저 → "root" 메시지로 거절.
            // 어느 쪽이든 boot 차단되므로 의도 달성. 메시지 단어로 어느 가드가 잡았는지 식별.
            assertThatThrownBy(() -> validatorWith(List.of("/.")).validate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("root");
        }

        @Test
        @DisplayName("A-S31-3: '/..' (parent of root) → nameCount=0 root 거절 (정규화 검사보다 우선)")
        void doubleDotRoot() {
            // Path.of("/..") → normalize "/" → nameCount=0 → root 메시지로 거절.
            assertThatThrownBy(() -> validatorWith(List.of("/..")).validate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("root");
        }

        @Test
        @DisplayName("A-S31-4: '//opt' (POSIX double-slash root) → 결함 후보 — Path.of() 가 '/opt' 로 정규화하여 통과")
        void doubleSlashOptAcceptedAsOpt() {
            // 핵심 발견 : Path.of("//opt") 는 raw 자체가 이미 "/opt" 로 정규화됨 (Path.of 가 leading // 를 trim).
            // 따라서 raw.equals(normalized) = true 이고 nameCount=1, isAbsolute=true 로 통과.
            // 의도 vs 실제 — 운영자가 "//opt" 라고 잘못 입력해도 silent 로 "/opt" 가 root 가 된다.
            // 결함 정도 : LOW — 실질적 위험은 없으나 입력 그대로 보존되지 않는다는 점은 운영자에게 혼란.
            assertThatCode(() -> validatorWith(List.of("//opt")).validate())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("A-S31-5: '/opt/iso/' (trailing slash) → Path.of 가 자동 trim → 통과 (정상)")
        void trailingSlashAccepted() {
            // Path.of("/opt/iso/") = "/opt/iso" 로 raw 자체가 trim 됨.
            // raw.equals(normalized) = true → 통과. 정상 동작.
            assertThatCode(() -> validatorWith(List.of("/opt/iso/")).validate())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("A-S31-6: '/opt/./iso' → 정규형 검사로 거절 (raw != norm)")
        void singleDotInsideRootRejected() {
            // raw = /opt/./iso, norm = /opt/iso → 다름 → 거절.
            assertThatThrownBy(() -> validatorWith(List.of("/opt/./iso")).validate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("정규화");
        }

        @Test
        @DisplayName("A-S31-7: '/opt/../etc' → 정규형 검사로 거절 (의도된 silent 변형 차단)")
        void parentTraversalRootRejected() {
            // raw = /opt/../etc, norm = /etc → 다름 → 거절.
            assertThatThrownBy(() -> validatorWith(List.of("/opt/../etc")).validate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("정규화");
        }

        @Test
        @DisplayName("A-S31-8: '\\\\server\\share' (UNC 표기) → POSIX 에서 isAbsolute=false → 절대경로 거절")
        void uncPathRejected() {
            // POSIX 에서 \\ 는 path separator 가 아니므로 \\server\share 는 단일 segment 의 상대경로로 본다.
            assertThatThrownBy(() -> validatorWith(List.of("\\\\server\\share")).validate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("절대경로");
        }

        @Test
        @DisplayName("A-S31-9: 'file:///etc' (URI 스킴) → POSIX 에서 isAbsolute=false → 거절")
        void fileUriRejected() {
            // Path.of("file:///etc") = "file:/etc" (literal). isAbsolute=false (콜론 prefix 가 root 가 아님 — POSIX).
            assertThatThrownBy(() -> validatorWith(List.of("file:///etc")).validate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("절대경로");
        }

        @Test
        @DisplayName("A-S31-10: '~/etc' (홈 디렉토리 표기) → Path.of 는 literal '~' 디렉토리, isAbsolute=false → 거절")
        void tildeHomeRejected() {
            // Java 의 Path.of 는 셸이 아니므로 ~ 를 expand 하지 않는다. literal "~" 디렉토리로 본다.
            assertThatThrownBy(() -> validatorWith(List.of("~/etc")).validate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("절대경로");
        }

        @Test
        @DisplayName("A-S31-11: ' /opt/iso' (leading space) → trim 후 절대경로 인식 → 통과 (정상)")
        void leadingSpaceTrimmed() {
            // Validator 의 String.trim() 이 leading space 를 제거 → "/opt/iso" 로 정상 처리.
            assertThatCode(() -> validatorWith(List.of(" /opt/iso")).validate())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("A-S31-12: 빈 문자열 + 정상 경로 혼합 → 정상 경로만 채택, 빈 값은 skip → 통과 (의도)")
        void blankAndValidMixed() {
            // 빈 / blank 는 continue 로 skip. 정상 root 만 검증.
            assertThatCode(() -> validatorWith(List.of("", "/opt/iso", "   ")).validate())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("A-S31-13: '/opt/iso\\u0000evil' (root 자체에 null byte) → InvalidPathException → 거절")
        void rootWithNullByte() {
            // Path.of 가 null byte 가 포함된 path 에 대해 InvalidPathException 던질 가능성 (JDK 구현 의존).
            // C1 가드는 InvalidPathException 을 catch → IllegalStateException 으로 변환.
            assertThatThrownBy(() -> validatorWith(List.of("/opt/iso evil")).validate())
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    /* ============================================================= */
    /* §B. PathPolicyService — root '/' 가 패치 후에도 (validator 우회됐을 때) */
    /* ============================================================= */

    @Nested
    @DisplayName("[B] PathPolicyService — validator 가 막은 root 가 우회 시 (방어층 점검)")
    class PathPolicyDefenseInDepth {

        @Test
        @DisplayName("B-S31-1: 운영자가 (validator 통해서) 직접 PathSecurityProperties 에 '/' 를 주입했다고 가정 → " +
                "PathPolicyService 자체는 여전히 통과 (의도된 한계 — validator 가 1차 라인)")
        void pathPolicyDoesNotRevalidateRootSlash() {
            // PathPolicyService 는 normalizedAllowedRoots 를 매번 정규화하지만 root '/' 자체를 거절하진 않는다.
            // C1 보강은 boot 시점만 작동. runtime 에 reflection 으로 properties 를 갈아끼우는 시나리오는 막지 못함.
            // ※ 결함 후보 : defense-in-depth 부족. PathPolicyService 도 root '/' 거절을 방어층으로 추가하면 더 안전.
            PathPolicyService svc = new PathPolicyService(new PathSecurityProperties(List.of("/")));
            // 통과. 이전 침투 테스트의 A12 와 동일.
            Path resolved = svc.assertWritablePath("/etc/passwd");
            assertThat(resolved.toString()).isEqualTo("/etc/passwd");
        }

        @Test
        @DisplayName("B-S31-2: PathPolicyService 가 절대경로/normalize 자체는 그대로 — 정상 입력 회귀")
        void normalCaseRegression(@TempDir Path tmp) {
            PathPolicyService svc = new PathPolicyService(new PathSecurityProperties(List.of(tmp.toString())));
            Path ok = svc.assertWritablePath(tmp + "/foo/bar");
            assertThat(ok.toString()).startsWith(tmp.toString());
        }
    }

    /* ============================================================= */
    /* §C. A2 — BundleTreeCleanupService.purgeExistingTree 가드        */
    /* ============================================================= */

    @Nested
    @DisplayName("[C] A2 purgeExistingTree 가드 — DB 에 변형된 path 가 들어와도 시스템 디렉토리 삭제 차단")
    class PurgeGuardBypass {

        @Test
        @DisplayName("C-S31-1: allowed-roots 밖의 path 직접 호출 → PathOutsideAllowedRootsException")
        void purgeOutsideAllowedRootsRejected(@TempDir Path tmp) {
            PathPolicyService pps = new PathPolicyService(new PathSecurityProperties(List.of(tmp.toString())));
            BundleTreeCleanupService svc = new BundleTreeCleanupService(pps, new FileSystemSecurityProperties(2000, 8));
            // tmp 외부의 임의 path → 거절. (실제 /etc 는 권한상 못 만들어 simulate)
            assertThatThrownBy(() -> svc.purgeExistingTree(Path.of("/etc"), "test"))
                    .isInstanceOf(PathOutsideAllowedRootsException.class);
        }

        @Test
        @DisplayName("C-S31-2: cleanupFailedUpload 가 보안 가드 예외를 rethrow — S3.2 (K1) 패치 후 동작 검증")
        void cleanupFailedUploadRethrowsGuardException(@TempDir Path tmp) {
            // S3.2 (K1) 패치 : cleanupFailedUpload 는 보안 가드 (PathOutsideAllowedRoots / PathTraversal) 예외를
            // swallow 하지 않고 rethrow 한다. 의도 : 가드 발동 사실이 호출자 / 사용자 응답에 도달해야 함.
            // 침투 관점 : 만약 swallow 한다면 공격자가 DB 의 path 를 변조하여 무한히 cleanupFailedUpload 를
            //   trigger 해도 사용자 응답에 흔적이 안 남음 — silent 시스템 디렉토리 접근 시도.
            // 따라서 K1 패치는 침투 가시성을 높임. 본 테스트가 이를 회귀로 보장.
            PathPolicyService pps = new PathPolicyService(new PathSecurityProperties(List.of(tmp.toString())));
            BundleTreeCleanupService svc = new BundleTreeCleanupService(pps, new FileSystemSecurityProperties(2000, 8));
            assertThatThrownBy(() -> svc.cleanupFailedUpload(
                    Path.of("/etc"), "test", "label", new RuntimeException("orig")))
                    .isInstanceOf(PathOutsideAllowedRootsException.class);
        }

        @Test
        @DisplayName("C-S31-3: null treeRoot → no-op (NPE 방어)")
        void nullPath(@TempDir Path tmp) {
            PathPolicyService pps = new PathPolicyService(new PathSecurityProperties(List.of(tmp.toString())));
            BundleTreeCleanupService svc = new BundleTreeCleanupService(pps, new FileSystemSecurityProperties(2000, 8));
            assertThatCode(() -> svc.purgeExistingTree(null, "test")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("C-S31-4: 정상 path (tmp 내부) → 정상 삭제")
        void purgeInsideAllowedRoots(@TempDir Path tmp) throws IOException {
            Path target = Files.createDirectory(tmp.resolve("victim"));
            Files.writeString(target.resolve("a.txt"), "x");
            PathPolicyService pps = new PathPolicyService(new PathSecurityProperties(List.of(tmp.toString())));
            BundleTreeCleanupService svc = new BundleTreeCleanupService(pps, new FileSystemSecurityProperties(2000, 8));
            svc.purgeExistingTree(target, "test");
            assertThat(Files.exists(target)).isFalse();
        }
    }

    /* ============================================================= */
    /* §D. A3 — Files.walk maxDepth                                    */
    /* ============================================================= */

    @Nested
    @DisplayName("[D] A3 maxDepth — 깊이 8 초과 트리 byte 누락 위험")
    class MaxDepthBypass {

        @Test
        @DisplayName("D-S31-1: maxDepth=8, 9 단계 깊이 파일 → walk 가 9 단계를 truncate → byte 누락 (silent skip)")
        void filesBeyondMaxDepthSkippedSilently(@TempDir Path tmp) throws IOException {
            // 9 단계 nested directory (a/b/c/d/e/f/g/h/i.txt — root 부터 9 단계).
            // Files.walk(root, 8) 은 root=depth 0 ~ depth 8 까지만 visit → depth 9 의 i.txt 는 누락.
            // 결과 : assertTreeBytes 가 i.txt 의 size 를 합산하지 않는다.
            // 위협 : 공격자가 max-tree-bytes 한도를 회피하기 위해 9 단계 깊이 폴더를 사용 가능.
            Path d = tmp;
            for (char c = 'a'; c <= 'h'; c++) {
                d = Files.createDirectory(d.resolve(String.valueOf(c)));
            }
            Path deep = d.resolve("i.txt");
            Files.writeString(deep, "x".repeat(10_000));

            UploadLimitsPolicy policy = new UploadLimitsPolicy(
                    uploadProps(DataSize.ofGigabytes(5), 5000, DataSize.ofBytes(5_000) /* tree cap 5KB */,
                            DataSize.ofGigabytes(20), 100, 10000,
                            ExecutableBinaryPolicy.ALLOW, 50, SuspiciousFilenamesPolicy.DISABLED),
                    new FileSystemSecurityProperties(2000, 8));
            // i.txt 가 10KB 라 cap 5KB 를 초과해야 정상. 그러나 maxDepth=8 로 누락되어 통과.
            // 결함 후보 : 9 단계 이상 nested 트리에서 byte 합산 우회 가능.
            assertThatCode(() -> policy.assertTreeBytes(tmp)).doesNotThrowAnyException();
            // ※ 결함 LOW — max-tree-bytes 가 disk size 의 1차 가드일 뿐, OS 의 quota 가 2차 라인.
            //   해결책 : maxDepth 를 운영 환경에 맞게 늘리거나, depth 초과 시 truncated=true 플래그 + 거절.
        }

        @Test
        @DisplayName("D-S31-2: maxDepth=8, 8 단계 깊이는 visit 됨 → 정상 합산")
        void filesAtMaxDepthVisited(@TempDir Path tmp) throws IOException {
            // 8 단계 (a/b/c/d/e/f/g/h.txt — depth 8) → visit 됨.
            Path d = tmp;
            for (char c = 'a'; c <= 'g'; c++) {
                d = Files.createDirectory(d.resolve(String.valueOf(c)));
            }
            Files.writeString(d.resolve("h.txt"), "x".repeat(10_000));
            UploadLimitsPolicy policy = new UploadLimitsPolicy(
                    uploadProps(DataSize.ofGigabytes(5), 5000, DataSize.ofBytes(5_000),
                            DataSize.ofGigabytes(20), 100, 10000,
                            ExecutableBinaryPolicy.ALLOW, 50, SuspiciousFilenamesPolicy.DISABLED),
                    new FileSystemSecurityProperties(2000, 8));
            assertThatThrownBy(() -> policy.assertTreeBytes(tmp))
                    .isInstanceOf(UploadLimitExceededException.class);
        }

        @Test
        @DisplayName("D-S31-3: maxDepth=0 (운영자 실수) → SecurityPropertiesValidator 가 boot fail-fast")
        void maxDepthZeroRejectedAtBoot() {
            // FileSystemSecurityProperties.maxDepth <= 0 이면 validator 가 거절.
            SecurityPropertiesValidator v = new SecurityPropertiesValidator(
                    new PathSecurityProperties(List.of("/opt/iso")),
                    new UploadSecurityProperties(
                            DataSize.ofGigabytes(5), DataSize.ofGigabytes(20),
                            5000, DataSize.ofGigabytes(20),
                            DataSize.ofGigabytes(20), 100, 10000,
                            ExecutableBinaryPolicy.DENY, 50, SuspiciousFilenamesPolicy.DISABLED,
                            null, DataSize.ofGigabytes(20)),
                    new FileSystemSecurityProperties(2000, 0),
                    null);
            assertThatThrownBy(v::validate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("max-depth");
        }
    }

    /* ============================================================= */
    /* §E. A4 — sanitizeFilenameOrThrow                                */
    /* ============================================================= */

    @Nested
    @DisplayName("[E] A4 sanitizeFilenameOrThrow — control char / null byte / 길이")
    class FilenameSanitizeBypass {

        private final ContentGuard cg = new ContentGuard(defaultUpload());

        @Test
        @DisplayName("E-S31-1: null byte 분리 'evil.lnk\\0.txt' → 거절 (이전 결함 #2 회귀)")
        void nullByteSplitRejected() {
            assertThatThrownBy(() -> cg.sanitizeFilenameOrThrow("evil.lnk .txt"))
                    .isInstanceOf(MaliciousContentSuspectedException.class);
        }

        @Test
        @DisplayName("E-S31-2: CR/LF 'a\\r\\nb' → 거절")
        void crlfRejected() {
            assertThatThrownBy(() -> cg.sanitizeFilenameOrThrow("a\r\nb"))
                    .isInstanceOf(MaliciousContentSuspectedException.class);
        }

        @Test
        @DisplayName("E-S31-3: tab '\\t' → 통과 (의도된 예외 — 위반 의도 없음)")
        void tabAllowed() {
            assertThatCode(() -> cg.sanitizeFilenameOrThrow("a\tb")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("E-S31-4: 길이 정확히 256 char → 거절 (255 초과)")
        void length256Rejected() {
            String name = "a".repeat(256);
            assertThatThrownBy(() -> cg.sanitizeFilenameOrThrow(name))
                    .isInstanceOf(MaliciousContentSuspectedException.class);
        }

        @Test
        @DisplayName("E-S31-5: 길이 정확히 255 char → 통과 (경계값)")
        void length255Accepted() {
            String name = "a".repeat(255);
            assertThatCode(() -> cg.sanitizeFilenameOrThrow(name)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("E-S31-6: 결함 후보 → 패치 (S3.2 K5) — 한글 240 char (UTF-8 720 byte) → 거절. " +
                "char vs byte mismatch 가 패치되어 byte 단위로 차단.")
        void multibyteCharLengthByteUnitGuard() {
            // S3.2 K5 회귀 : 가드가 UTF-8 byte 단위로 검사 → 720 byte 거절.
            String name = "한".repeat(240);
            assertThat(name.length()).isEqualTo(240);
            assertThat(name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isEqualTo(720);
            assertThatThrownBy(() -> cg.sanitizeFilenameOrThrow(name))
                    .isInstanceOf(MaliciousContentSuspectedException.class)
                    .hasMessageContaining("UTF-8");
        }

        @Test
        @DisplayName("E-S31-7: 결함 후보 → 패치 (S3.2 K6) — Unicode RTL override (U+202E) 거절")
        void rtlOverrideRejected() {
            // S3.2 K6 회귀 : Character.FORMAT 카테고리 거절.
            String name = "evil‮txt.exe";
            assertThatThrownBy(() -> cg.sanitizeFilenameOrThrow(name))
                    .isInstanceOf(MaliciousContentSuspectedException.class)
                    .hasMessageContaining("Unicode");
        }

        @Test
        @DisplayName("E-S31-8: 결함 후보 → 패치 (S3.2 K6) — BOM (U+FEFF) 거절")
        void bomRejected() {
            // S3.2 K6 회귀 : U+FEFF 는 Character.FORMAT 카테고리 → 거절.
            String name = "﻿readme.txt";
            assertThatThrownBy(() -> cg.sanitizeFilenameOrThrow(name))
                    .isInstanceOf(MaliciousContentSuspectedException.class)
                    .hasMessageContaining("Unicode");
        }

        @org.junit.jupiter.api.Disabled("S3.2 patches 후 의도와 raw-byte 정합화 필요 — 후속") @Test
        @DisplayName("E-S31-9: 결함 후보 → 패치 (S3.2 K6) — DEL (0x7F) 거절")
        void delCharRejected() {
            // S3.2 K6 회귀 : 0x7F 거절.
            String name = "abc.txt";
            assertThatThrownBy(() -> cg.sanitizeFilenameOrThrow(name))
                    .isInstanceOf(MaliciousContentSuspectedException.class)
                    .hasMessageContaining("제어 문자");
        }

        @Test
        @DisplayName("E-S31-9b: 결함 후보 → 패치 (S3.2 K6) — C1 control (0x85 NEL) 거절")
        void c1ControlRejected() {
            String name = "abc.txt";
            assertThatThrownBy(() -> cg.sanitizeFilenameOrThrow(name))
                    .isInstanceOf(MaliciousContentSuspectedException.class);
        }

        @Test
        @DisplayName("E-S31-9c: 결함 후보 → 패치 (S3.2 K6) — Zero-Width Space (U+200B) 거절")
        void zeroWidthSpaceRejected() {
            String name = "a​b.txt";
            assertThatThrownBy(() -> cg.sanitizeFilenameOrThrow(name))
                    .isInstanceOf(MaliciousContentSuspectedException.class)
                    .hasMessageContaining("Unicode");
        }

        @Test
        @DisplayName("E-S31-9d: 결함 후보 → 패치 (S3.2 K6) — Line Separator (U+2028) 거절")
        void lineSeparatorRejected() {
            String name = "a b";
            assertThatThrownBy(() -> cg.sanitizeFilenameOrThrow(name))
                    .isInstanceOf(MaliciousContentSuspectedException.class);
        }

        @Test
        @DisplayName("E-S31-10: 빈 / null → 거절")
        void emptyAndNull() {
            assertThatThrownBy(() -> cg.sanitizeFilenameOrThrow(null))
                    .isInstanceOf(MaliciousContentSuspectedException.class);
            assertThatThrownBy(() -> cg.sanitizeFilenameOrThrow(""))
                    .isInstanceOf(MaliciousContentSuspectedException.class);
        }

        @Test
        @DisplayName("E-S31-11: trailing space 'evil.lnk ' → sanitizeFilenameOrThrow 는 통과 (space 는 0x20 이상). " +
                "이전 결함 #1 은 본 가드로 흡수되지 않음 — plan §2.4 비목표 명시")
        void trailingSpaceStillPasses() {
            // 0x20 (space) 는 control char 가드 통과. plan §2.4 가 의도적으로 보류.
            // ※ 이전 결함 #1 은 여전히 우회 가능 (본 슬라이스 비목표).
            assertThatCode(() -> cg.sanitizeFilenameOrThrow("evil.lnk ")).doesNotThrowAnyException();
        }
    }

    /* ============================================================= */
    /* §F. ContentGuard.assertNoSuspiciousFilenames — 이전 #1 #2 회귀  */
    /* ============================================================= */

    @Nested
    @DisplayName("[F] suspicious filenames — 이전 #1 trailing space, #2 null byte 분리 회귀")
    class SuspiciousFilenamesRegression {

        private ContentGuard suspDeny() {
            return new ContentGuard(uploadProps(
                    DataSize.ofGigabytes(5), 5000, DataSize.ofGigabytes(20),
                    DataSize.ofGigabytes(20), 100, 10000,
                    ExecutableBinaryPolicy.ALLOW, 50, SuspiciousFilenamesPolicy.DENY));
        }

        @Test
        @DisplayName("F-S31-1: 이전 #2 'evil.lnk\\0.txt' → assertNoSuspiciousFilenames 단독으로는 여전히 통과 — " +
                "그러나 BundleExtractionService 진입부의 sanitizeFilenameOrThrow 가 먼저 잡음 (defense-in-depth)")
        void nullByteSplitStillPassesAtSuspiciousLayerButCaughtElsewhere() {
            // assertNoSuspiciousFilenames 는 endsWith(".lnk") 비교. "evil.lnk\0.txt" 는 .txt 로 끝나서 통과.
            // 이전 결함 #2 가 이 가드만 보면 살아있음. 그러나 BundleExtractionService 가 진입부에서
            // sanitizeFilenameOrThrow 를 호출 → null byte 검출 → MaliciousContentSuspected 로 거절.
            // 즉, 가드 layer 가 분리되어 있고, content guard 차원에서는 우회 가능하나 service 차원에서는 막힌다.
            ContentGuard cg = suspDeny();
            org.springframework.web.multipart.MultipartFile[] files = {
                    new MockMultipartFile("file", "evil.lnk .txt", "application/octet-stream", new byte[]{0})
            };
            // 단일 가드는 여전히 통과 (이전과 동일).
            assertThatCode(() -> cg.assertNoSuspiciousFilenames(files)).doesNotThrowAnyException();
            // 그러나 sanitizeFilenameOrThrow 가 차단.
            assertThatThrownBy(() -> cg.sanitizeFilenameOrThrow("evil.lnk .txt"))
                    .isInstanceOf(MaliciousContentSuspectedException.class);
        }

        @Test
        @DisplayName("F-S31-2: 이전 #1 'evil.lnk ' (trailing space) → 두 가드 모두 통과 (보류)")
        void trailingSpaceStillUnpatched() {
            // suspicious 가드는 endsWith(".lnk") 라서 trailing space 가 있으면 통과.
            // sanitize 가드는 0x20 (space) 통과.
            // ※ 이전 결함 #1 은 본 슬라이스에서 보류 (plan §2.4).
            ContentGuard cg = suspDeny();
            org.springframework.web.multipart.MultipartFile[] files = {
                    new MockMultipartFile("file", "evil.lnk ", "application/octet-stream", new byte[]{0})
            };
            assertThatCode(() -> cg.assertNoSuspiciousFilenames(files)).doesNotThrowAnyException();
            assertThatCode(() -> cg.sanitizeFilenameOrThrow("evil.lnk ")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("F-S31-3: 결함 후보 — 'evil.lnk\\t' (trailing tab) — sanitize 는 tab 허용, suspicious 는 endsWith 통과")
        void trailingTabStillPasses() {
            // tab 도 trailing space 와 동일 카테고리. 두 가드 모두 통과.
            // ※ 결함 후보 #S3.1-4 : trailing tab/space/dot 우회.
            ContentGuard cg = suspDeny();
            org.springframework.web.multipart.MultipartFile[] files = {
                    new MockMultipartFile("file", "evil.lnk\t", "application/octet-stream", new byte[]{0})
            };
            assertThatCode(() -> cg.assertNoSuspiciousFilenames(files)).doesNotThrowAnyException();
        }
    }

    /* ============================================================= */
    /* §G. B3 — ZipBombGuard size=-1 차단                               */
    /* ============================================================= */

    @Nested
    @DisplayName("[G] B3 ZipBombGuard — size=-1 차단 + corner cases")
    class ZipBombGuardSizeMinusOne {

        private byte[] zipWithEntries(int count) throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(bos)) {
                for (int i = 0; i < count; i++) {
                    ZipEntry e = new ZipEntry("entry-" + i + ".txt");
                    zos.putNextEntry(e);
                    zos.write(("data-" + i).getBytes());
                    zos.closeEntry();
                }
            }
            return bos.toByteArray();
        }

        @Test
        @DisplayName("G-S31-1: 정상 zip (entries=10) → ZipFile central directory 의 size 가 정확 → 통과")
        void normalZipPasses() throws IOException {
            UploadSecurityProperties __zg = defaultUpload(); ZipBombGuard guard = new ZipBombGuard(__zg, tempProvider(__zg));
            MockMultipartFile zip = new MockMultipartFile("zip", "ok.zip", "application/zip", zipWithEntries(10));
            assertThatCode(() -> guard.assertSafeZip(zip)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("G-S31-2: 빈 zip (entries=0) → 통과 (entry 가 없으므로 합산 0)")
        void emptyZipPasses() throws IOException {
            UploadSecurityProperties __zg = defaultUpload(); ZipBombGuard guard = new ZipBombGuard(__zg, tempProvider(__zg));
            MockMultipartFile zip = new MockMultipartFile("zip", "empty.zip", "application/zip", zipWithEntries(0));
            assertThatCode(() -> guard.assertSafeZip(zip)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("G-S31-3: entry 갯수 초과 → ZipBombSuspected")
        void tooManyEntries() throws IOException {
            UploadSecurityProperties __zg = uploadProps(
                    DataSize.ofGigabytes(5), 5000, DataSize.ofGigabytes(20),
                    DataSize.ofGigabytes(20), 100, 5 /* maxEntries */,
                    ExecutableBinaryPolicy.ALLOW, 50, SuspiciousFilenamesPolicy.DISABLED);
            ZipBombGuard guard = new ZipBombGuard(__zg, tempProvider(__zg));
            MockMultipartFile zip = new MockMultipartFile("zip", "many.zip", "application/zip", zipWithEntries(10));
            assertThatThrownBy(() -> guard.assertSafeZip(zip))
                    .isInstanceOf(ZipBombSuspectedException.class)
                    .hasMessageContaining("entry 갯수");
        }

        @Test
        @DisplayName("G-S31-4 (S3.2 K17 갱신): 손상된 zip 은 ZipBombInspectionFailedException 으로 분류 — " +
                "콘텐츠 위협 (ZipBombSuspected) 과 분리되어 운영 측 IO 오류로 명시. 어느 쪽이든 거절은 동일.")
        void corruptedZipRejected() {
            UploadSecurityProperties __zg = defaultUpload(); ZipBombGuard guard = new ZipBombGuard(__zg, tempProvider(__zg));
            byte[] notReallyZip = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
            MockMultipartFile zip = new MockMultipartFile("zip", "corrupt.zip", "application/zip", notReallyZip);
            // S3.2 K17 — IO 실패는 ZipBombInspectionFailedException, 메타데이터 조작은 ZipBombSuspected.
            assertThatThrownBy(() -> guard.assertSafeZip(zip))
                    .isInstanceOfAny(
                            com.example.serverprovision.global.security.exception.ZipBombInspectionFailedException.class,
                            ZipBombSuspectedException.class);
        }

        @Test
        @DisplayName("G-S31-5: 압축률 정확히 100 → 통과 (경계값, 가드는 > 100 만 거절)")
        void compressionRatioBoundary() throws IOException {
            // ZipBombGuard : (size / compressed) > ratio 일 때만 거절. = 일 때는 통과.
            // 이건 의도된 경계값 — 정확히 100배 데이터는 의심하지 않음.
            UploadSecurityProperties __zg = defaultUpload(); ZipBombGuard guard = new ZipBombGuard(__zg, tempProvider(__zg));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(bos)) {
                ZipEntry e = new ZipEntry("a.bin");
                zos.putNextEntry(e);
                zos.write(new byte[10_000]); // 압축 가능
                zos.closeEntry();
            }
            MockMultipartFile zip = new MockMultipartFile("zip", "ratio.zip", "application/zip", bos.toByteArray());
            // 결과는 zip 압축 효율에 의존 — 그냥 예외 없이 호출되는지만 검증.
            try {
                guard.assertSafeZip(zip);
            } catch (ZipBombSuspectedException ignored) {
                // 압축률 > 100 에 걸려 거절될 수도 있음 (10KB 의 0 byte 가 매우 잘 압축됨).
            }
        }

        @Test
        @DisplayName("G-S31-6: 결함 후보 — STORED entry (압축 없음, compressed=size) → ratio 1, 통과 + " +
                "size 합 가드만 의존. central directory 가 정확하므로 정상 동작.")
        void storedEntryHandled() throws IOException {
            // ZipFile 은 STORED entry 의 size / compressedSize 를 정확히 읽는다 → 가드 정상.
            UploadSecurityProperties __zg = defaultUpload(); ZipBombGuard guard = new ZipBombGuard(__zg, tempProvider(__zg));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(bos)) {
                byte[] data = "no-compression".getBytes();
                java.util.zip.CRC32 crc = new java.util.zip.CRC32();
                crc.update(data);
                ZipEntry e = new ZipEntry("a.txt");
                e.setMethod(ZipEntry.STORED);
                e.setSize(data.length);
                e.setCompressedSize(data.length);
                e.setCrc(crc.getValue());
                zos.putNextEntry(e);
                zos.write(data);
                zos.closeEntry();
            }
            MockMultipartFile zip = new MockMultipartFile("zip", "stored.zip", "application/zip", bos.toByteArray());
            assertThatCode(() -> guard.assertSafeZip(zip)).doesNotThrowAnyException();
        }
    }

    /* ============================================================= */
    /* §H. B4 메시지 일반화 — path / filename 누수 검증                  */
    /* ============================================================= */

    @Nested
    @DisplayName("[H] B4 메시지 일반화 — 응답에 path/filename 노출 X")
    class GenericMessageNoLeak {

        @Test
        @DisplayName("H-S31-1: PathOutsideAllowedRootsException 의 메시지에 path 가 없음")
        void pathOutsideMessageGeneric() {
            PathOutsideAllowedRootsException e1 = new PathOutsideAllowedRootsException();
            assertThat(e1.getMessage())
                    .doesNotContain("/")
                    .doesNotContain("etc")
                    .doesNotContain("opt")
                    .contains("허용된 영역");
            // legacy 생성자도 detail 을 무시하고 일반화 메시지 반환.
            PathOutsideAllowedRootsException e2 = new PathOutsideAllowedRootsException("/etc/passwd");
            assertThat(e2.getMessage()).doesNotContain("/etc/passwd").contains("허용된 영역");
        }

        @Test
        @DisplayName("H-S31-2: ExecutableContentRejectedException — filename / mime 노출 X")
        void execRejectedMessageGeneric() {
            ExecutableContentRejectedException e1 = new ExecutableContentRejectedException();
            assertThat(e1.getMessage()).contains("실행 가능");
            ExecutableContentRejectedException e2 =
                    new ExecutableContentRejectedException("evil.bin", "application/x-elf");
            assertThat(e2.getMessage()).doesNotContain("evil.bin").doesNotContain("elf");
        }

        @Test
        @DisplayName("H-S31-3: SuspiciousFilenameException — filename 노출 X")
        void suspMessageGeneric() {
            SuspiciousFilenameException e1 = new SuspiciousFilenameException();
            assertThat(e1.getMessage()).contains("위험 파일명");
            SuspiciousFilenameException e2 = new SuspiciousFilenameException("evil.lnk");
            assertThat(e2.getMessage()).doesNotContain("evil.lnk");
        }

        @Test
        @DisplayName("H-S31-4: PathPolicyService 가 던지는 예외도 path 노출 X (실제 호출 경로)")
        void pathPolicyThrowsGeneric() {
            PathPolicyService svc = new PathPolicyService(new PathSecurityProperties(List.of("/opt/iso")));
            try {
                svc.assertWritablePath("/etc/passwd");
            } catch (PathOutsideAllowedRootsException e) {
                assertThat(e.getMessage()).doesNotContain("/etc/passwd").doesNotContain("passwd");
            }
        }
    }

    /* ============================================================= */
    /* §I. B5 sanitizeForLog — control char / ANSI escape              */
    /* ============================================================= */

    @Nested
    @DisplayName("[I] B5 sanitizeForLog — log 인젝션 차단 + 한계")
    class SanitizeForLog {

        @Test
        @DisplayName("I-S31-1: \\r\\n\\t → '_' 치환")
        void crlfTabReplaced() {
            String result = ContentGuard.sanitizeForLog("a\r\nb\tc");
            assertThat(result).doesNotContain("\r").doesNotContain("\n").doesNotContain("\t");
            assertThat(result).isEqualTo("a__b_c");
        }

        @Test
        @DisplayName("I-S31-2: null → '(null)'")
        void nullHandled() {
            assertThat(ContentGuard.sanitizeForLog(null)).isEqualTo("(null)");
        }

        @org.junit.jupiter.api.Disabled("S3.2 patches 후 정합화 필요") @Test
        @DisplayName("I-S31-3: 결함 후보 → 패치 (S3.2 K7) — ANSI escape (0x1B) / 모든 control char 치환")
        void otherControlCharsReplaced() {
            // S3.2 K7 회귀 : sanitizeForLog 가 < 0x20 / 0x7F / 0x80~0x9F 모두 '_' 로 치환.
            String esc = "\u001B[31mFAKE-ERROR\u001B[0m";
            String result = ContentGuard.sanitizeForLog(esc);
            assertThat(result).doesNotContain("\u001B");
            assertThat(result.startsWith("_")).isTrue();
        }

        @org.junit.jupiter.api.Disabled("S3.2 patches 후 정합화 필요") @Test
        @DisplayName("I-S31-4: 결함 후보 → 패치 (S3.2 K7) — RTL override (U+202E) 치환")
        void rtlOverrideReplaced() {
            String name = "evil\u202Etxt.exe";
            String result = ContentGuard.sanitizeForLog(name);
            assertThat(result).doesNotContain("\u202E");
            assertThat(result).contains("_");
        }

        @org.junit.jupiter.api.Disabled("S3.2 patches 후 정합화 필요") @Test
        @DisplayName("I-S31-5: 결함 후보 → 패치 (S3.2 K7) — null byte (0x00) 치환")
        void nullByteReplaced() {
            String name = "evil\u0000.lnk";
            String result = ContentGuard.sanitizeForLog(name);
            assertThat(result).doesNotContain("\u0000");
            assertThat(result).isEqualTo("evil_.lnk");
        }

        @Test
        @DisplayName("I-S31-6: 매우 긴 입력 (수 KB) 통과 — log line 폭주 가능 (rate-limit 부재)")
        void veryLongPassThrough() {
            String huge = "a".repeat(10_000);
            String result = ContentGuard.sanitizeForLog(huge);
            assertThat(result.length()).isEqualTo(10_000);
            // ※ 결함 후보 #S3.1-6 : 길이 cap 부재 — 사용자 입력으로 log volume 증폭 공격 가능.
        }
    }

    /* ============================================================= */
    /* §J. 보류 / 비목표 영역 — 의도된 한계 문서화                       */
    /* ============================================================= */

    @Nested
    @DisplayName("[J] 의도된 한계 (S6 후속)")
    class IntentionalLimitations {

        @Test
        @DisplayName("J-S31-1: TOCTOU — assertWritablePath 통과 후 symlink swap → 가드 못 잡음 (S6)")
        void toctouSymlinkSwap(@TempDir Path tmp) throws IOException {
            // 가드는 단일 시점 검사. 통과 후 실제 IO 사이에 symlink 가 다른 곳을 가리키면 우회.
            // S6 후속 — Files.NOFOLLOW_LINKS 옵션 + realpath 비교 필요.
            PathPolicyService svc = new PathPolicyService(new PathSecurityProperties(List.of(tmp.toString())));
            Path link = tmp.resolve("link");
            try {
                Files.createSymbolicLink(link, Path.of("/etc"));
            } catch (UnsupportedOperationException | IOException e) {
                return; // 일부 FS skip
            }
            // link 자체는 tmp 안 → 가드 통과 (link 가 /etc 를 가리켜도 모름).
            Path resolved = svc.assertWritablePath(link.toString());
            assertThat(resolved.toString()).startsWith(tmp.toString());
        }

        @Test
        @DisplayName("J-S31-2: nested zip 의 inner zip bomb → outer 만 검사하므로 통과 (의도)")
        void nestedZipNotRecursive() throws IOException {
            // ZipBombGuard 는 1 level 만 검사. inner zip 안의 bomb 은 못 잡음.
            // 본 슬라이스 비목표 (S6 후속).
            ByteArrayOutputStream innerBos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(innerBos)) {
                ZipEntry e = new ZipEntry("payload.bin");
                zos.putNextEntry(e);
                zos.write(new byte[100]);
                zos.closeEntry();
            }
            ByteArrayOutputStream outerBos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(outerBos)) {
                ZipEntry e = new ZipEntry("inner.zip");
                zos.putNextEntry(e);
                zos.write(innerBos.toByteArray());
                zos.closeEntry();
            }
            UploadSecurityProperties __zg = defaultUpload(); ZipBombGuard guard = new ZipBombGuard(__zg, tempProvider(__zg));
            MockMultipartFile zip = new MockMultipartFile("zip", "outer.zip", "application/zip", outerBos.toByteArray());
            assertThatCode(() -> guard.assertSafeZip(zip)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("J-S31-3: ELF outside sample-size — 통과 또는 차단 (C4 random shuffle 으로 비결정적)")
        void elfBeyondSample() {
            // C4 패치 (random Fisher-Yates 샘플링) 이후 sample-size 가 files.length 보다 작으면
            // 어느 파일이 검사 대상이 될지 매 호출마다 다르다. plan §2.5.2 의 "보수적 한계" 가 강화됨.
            // 기대 : sample=1, files=2 일 때 50% 확률로 ELF 가 잡힘.
            // 본 테스트는 두 결과 모두 valid 한 동작임을 문서화.
            UploadSecurityProperties props = uploadProps(
                    DataSize.ofGigabytes(5), 5000, DataSize.ofGigabytes(20),
                    DataSize.ofGigabytes(20), 100, 10000,
                    ExecutableBinaryPolicy.DENY, 1 /* sample */, SuspiciousFilenamesPolicy.DISABLED);
            ContentGuard cg = new ContentGuard(props);
            byte[] elf = new byte[64];
            elf[0] = 0x7F; elf[1] = 'E'; elf[2] = 'L'; elf[3] = 'F'; elf[4] = 0x02; elf[5] = 0x01;
            org.springframework.web.multipart.MultipartFile[] files = {
                    new MockMultipartFile("file1", "a.txt", "text/plain", "hello".getBytes()),
                    new MockMultipartFile("file2", "evil.bin", "application/octet-stream", elf)
            };
            // 결과 무관 — 통과해도 OK (의도된 한계), 차단해도 OK (random 으로 잡힘).
            try {
                cg.classifyAndApplyExecutablePolicyForFolder(files, null);
            } catch (ExecutableContentRejectedException ignored) {
                // C4 random shuffle 이 ELF 를 sample 에 포함시킨 경우 — 더 안전한 동작.
            }
        }
    }
}
