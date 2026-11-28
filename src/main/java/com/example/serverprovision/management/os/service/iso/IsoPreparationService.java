package com.example.serverprovision.management.os.service.iso;

import com.example.serverprovision.management.os.exception.ExtractionFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ISO 경로를 탐색 가능한 상태로 준비해 주는 서비스.
 * <ul>
 *   <li>HTTP(S) URL → 통과</li>
 *   <li>디렉토리 → 통과 (이미 마운트된 상태로 간주)</li>
 *   <li>.iso 파일 → {@code mount -o loop,ro} 시도 → 실패 시 {@code 7z} / {@code bsdtar} 로 폴백</li>
 * </ul>
 * 반환되는 {@link PreparedIsoPath} 는 try-with-resources 종료 시점에 마운트 해제 + 임시 디렉토리 정리를 보장한다.
 */
@Slf4j
@Service
public class IsoPreparationService {

	/**
	 * 탐색 가능한 경로 + 정리 콜백 쌍.
	 * {@code close()} 는 idempotent 해야 하므로 통과 케이스는 {@link #passthrough(String)} 로 no-op 콜백을 걸어둔다.
	 */
	public record PreparedIsoPath(
			String effectivePath,
			Runnable cleanup
	) implements AutoCloseable {

		@Override
		public void close() {
			cleanup.run();
		}

		public static PreparedIsoPath passthrough(String path) {
			return new PreparedIsoPath(
					path, () -> {
			}
			);
		}
	}

	public PreparedIsoPath prepare(String isoPath) {
		if (isoPath == null || isoPath.isBlank()) {
			throw new ExtractionFailedException("ISO 경로가 지정되지 않았습니다.");
		}

		// HTTP(S) URL 은 사전 접속 검증 없이 통과한다 (실패는 전략 내부에서 명시적 메시지로 변환됨).
		if (isoPath.startsWith("http://") || isoPath.startsWith("https://")) {
			return PreparedIsoPath.passthrough(isoPath);
		}

		Path path;
		try {
			path = Paths.get(isoPath);
		} catch (Exception e) {
			throw new ExtractionFailedException("ISO 경로 형식이 올바르지 않습니다: " + isoPath, e);
		}

		if (!Files.exists(path)) {
			throw new ExtractionFailedException("ISO 경로가 없습니다: " + isoPath + ". 경로를 다시 확인해야 합니다.");
		}

		if (Files.isDirectory(path)) {
			log.debug("[IsoPreparationService] 마운트된 디렉토리로 인식. path={}", isoPath);
			return PreparedIsoPath.passthrough(isoPath);
		}

		if (Files.isRegularFile(path)) {
			if (!isoPath.toLowerCase().endsWith(".iso")) {
				throw new ExtractionFailedException("지정한 경로가 ISO 파일이 아닙니다: " + isoPath + ". ISO 파일이나 마운트된 디렉토리를 지정해야 합니다.");
			}
			log.info("[IsoPreparationService] .iso 파일 — 마운트/압축 해제를 시도합니다. path={}", isoPath);
			return prepareFromIsoFile(path);
		}

		// 심볼릭 링크 미해결 / FIFO / 소켓 등 처리 불가 유형
		throw new ExtractionFailedException("인식할 수 없는 경로입니다: " + isoPath + ". ISO 파일이나 디렉토리만 지정할 수 있습니다.");
	}

	/**
	 * E4-1-b-1 — ISO 파일을 지정한 빈 디렉터리에 푼다(RHEL 설치 작업 트리). mount 는 root 가 필요해 쓰지 않는다.
	 * bsdtar 를 먼저 쓴다: libarchive 는 Rock Ridge 의 긴 파일 이름을 읽고, 운영 런북도 bsdtar 만 설치한다.
	 * 정리는 호출자 몫이다.
	 *
	 * @throws ExtractionFailedException 두 도구가 모두 없거나 실패한 경우
	 */
	public void extractInto(Path isoFile, Path targetDir) {
		try {
			boolean extracted = runExtractBsdtar(isoFile, targetDir) || runExtract7z(isoFile, targetDir);
			if (!extracted) {
				throw new ExtractionFailedException("ISO 압축을 풀지 못했습니다: " + isoFile + ". bsdtar 또는 7z 설치 상태를 확인해야 합니다.");
			}
		} catch (IOException e) {
			throw new ExtractionFailedException("ISO 압축을 풀지 못했습니다: " + isoFile, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ExtractionFailedException("ISO 압축 풀기가 중단되었습니다: " + isoFile, e);
		}
	}

	/**
	 * ISO 에서 지정한 파일만 꺼낸다. ISO 에 없는 이름은 건너뛴다. 없는 파일은 호출자의 판별이 "그 계열 ISO 가 아님" 으로 닫는다.
	 * 지정한 파일이 모두 꺼내졌는데 도구가 실패했으면 손상으로 보고 예외를 던진다. bsdtar 는 없는 이름이 섞이면 종료 코드 1 을 낸다.
	 *
	 * @throws ExtractionFailedException 도구가 없거나, 파일은 다 나왔는데 도구가 실패한 경우
	 */
	public void extractMembers(Path isoFile, Path targetDir, List<String> members) {
		boolean toolSucceeded;
		try {
			toolSucceeded = runMembers(isoFile, targetDir, members);
		} catch (IOException e) {
			throw new ExtractionFailedException("ISO 에서 파일을 꺼내지 못했습니다: " + isoFile, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ExtractionFailedException("ISO 에서 파일 꺼내기가 중단되었습니다: " + isoFile, e);
		}
		boolean allPresent = members.stream().allMatch(member -> Files.isRegularFile(targetDir.resolve(member)));
		if (!toolSucceeded && allPresent) {
			throw new ExtractionFailedException("ISO 에서 파일을 꺼내지 못했습니다: " + isoFile);
		}
	}

	/** {@link #extractInto} 가 쓸 도구(bsdtar 또는 7z)가 하나라도 있는가. */
	public boolean extractionToolAvailable() {
		return isCommandAvailable("bsdtar") || isCommandAvailable("7z");
	}

	private PreparedIsoPath prepareFromIsoFile(Path isoFile) {
		try {
			return mountIso(isoFile);
		} catch (Exception e) {
			log.warn("[IsoPreparationService] loop 마운트 실패, 압축 해제로 폴백합니다. iso={}, 원인={}", isoFile, e.getMessage());
		}
		return extractIso(isoFile);
	}

	private PreparedIsoPath mountIso(Path isoFile) throws IOException, InterruptedException {
		Path mountDir = Files.createTempDirectory("iso_mount_");
		log.info("[IsoPreparationService] loop 마운트 시도. iso={}, mountDir={}", isoFile, mountDir);

		Process proc = new ProcessBuilder("mount", "-o", "loop,ro", isoFile.toString(), mountDir.toString())
				.redirectErrorStream(true)
				.start();
		int exitCode = proc.waitFor();

		if (exitCode != 0) {
			String stderr = new String(proc.getInputStream().readAllBytes()).trim();
			Files.deleteIfExists(mountDir);
			throw new IOException("mount 실패 (exit=" + exitCode + "): " + stderr);
		}

		log.info("[IsoPreparationService] loop 마운트 성공. mountDir={}", mountDir);
		return new PreparedIsoPath(mountDir.toString(), () -> unmountAndClean(mountDir));
	}

	private PreparedIsoPath extractIso(Path isoFile) {
		try {
			Path extractDir = Files.createTempDirectory("iso_extract_");
			log.info("[IsoPreparationService] ISO 압축 해제 시도. iso={}, extractDir={}", isoFile, extractDir);

			boolean extracted = runExtract7z(isoFile, extractDir)
					|| runExtractBsdtar(isoFile, extractDir);

			if (!extracted) {
				deleteDirectory(extractDir);
				throw new ExtractionFailedException(
						"ISO 파일을 열 수 없습니다: " + isoFile + ". 마운트 권한과 7z 또는 bsdtar 설치 상태를 확인해야 합니다."
				);
			}

			log.info("[IsoPreparationService] ISO 압축 해제 성공. extractDir={}", extractDir);
			return new PreparedIsoPath(extractDir.toString(), () -> deleteDirectory(extractDir));

		} catch (IOException | InterruptedException e) {
			throw new ExtractionFailedException(
					"ISO 압축을 풀지 못했습니다: " + isoFile, e);
		}
	}

	private boolean runExtract7z(Path isoFile, Path extractDir) throws IOException, InterruptedException {
		if (!isCommandAvailable("7z")) return false;
		Process proc = new ProcessBuilder("7z", "x", isoFile.toString(), "-o" + extractDir, "-y")
				.redirectErrorStream(true)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.start();
		return proc.waitFor() == 0;
	}

	private boolean runExtractBsdtar(Path isoFile, Path extractDir) throws IOException, InterruptedException {
		if (!isCommandAvailable("bsdtar")) return false;
		Process proc = new ProcessBuilder("bsdtar", "-xf", isoFile.toString(), "-C", extractDir.toString())
				.redirectErrorStream(true)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.start();
		return proc.waitFor() == 0;
	}

	/** bsdtar 를 먼저, 없으면 7z 로 지정 파일만 꺼낸다. 둘 다 없으면 예외. 반환값은 도구의 성공 여부다. */
	private boolean runMembers(Path isoFile, Path targetDir, List<String> members) throws IOException, InterruptedException {
		List<String> command = new ArrayList<>();
		if (isCommandAvailable("bsdtar")) {
			command.addAll(List.of("bsdtar", "-xf", isoFile.toString(), "-C", targetDir.toString()));
			command.addAll(members);
		} else if (isCommandAvailable("7z")) {
			command.addAll(List.of("7z", "x", isoFile.toString(), "-o" + targetDir, "-y"));
			command.addAll(members);
		} else {
			throw new ExtractionFailedException("ISO 에서 파일을 꺼낼 도구가 없습니다: " + isoFile + ". bsdtar 또는 7z 설치 상태를 확인해야 합니다.");
		}
		Process proc = new ProcessBuilder(command)
				.redirectErrorStream(true)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.start();
		return proc.waitFor() == 0;
	}

	private boolean isCommandAvailable(String command) {
		try {
			return new ProcessBuilder("which", command)
					.redirectErrorStream(true)
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.start()
					.waitFor() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	private void unmountAndClean(Path mountDir) {
		try {
			log.info("[IsoPreparationService] loop 마운트 해제. mountDir={}", mountDir);
			new ProcessBuilder("umount", mountDir.toString())
					.redirectErrorStream(true)
					.start()
					.waitFor();
		} catch (Exception e) {
			log.warn("[IsoPreparationService] 마운트 해제 실패. mountDir={}, 원인={}", mountDir, e.getMessage());
		} finally {
			deleteDirectory(mountDir);
		}
	}

	private void deleteDirectory(Path dir) {
		if (dir == null || !Files.exists(dir)) return;
		try (var paths = Files.walk(dir)) {
			paths.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.delete(p);
				} catch (IOException e) {
					log.warn("[IsoPreparationService] 파일 삭제 실패. path={}", p);
				}
			});
		} catch (IOException e) {
			log.warn("[IsoPreparationService] 임시 디렉토리 삭제 실패. dir={}, 원인={}", dir, e.getMessage());
		}
	}
}
