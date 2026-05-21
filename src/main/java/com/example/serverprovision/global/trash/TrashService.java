package com.example.serverprovision.global.trash;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.exception.TrashMoveFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * MK3 — 5 자원 도메인이 공통으로 사용하는 trash mv / restore 인프라.
 *
 * <p>책임 :
 * <ul>
 *   <li>{@link #moveToTrash} — 자원 파일을 trash 디렉토리로 mv. timestamp + UUID8 suffix 로 충돌 차단.</li>
 *   <li>{@link #moveBack} — trash 자원을 원래 경로로 mv (restore 시).</li>
 *   <li>saga 보상 — IOException 발생 시 reverse mv. retry n회 (DCN-NEW7=b) 후 critical {@link TrashMoveFailedException}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrashService {

	private final TrashPolicy trashPolicy;

	/**
	 * timestamp suffix 형식 (DCN3=c) — ms 정밀도 + UUID8 fallback.
	 */
	private static final DateTimeFormatter TS_FORMAT =
			DateTimeFormatter.ofPattern("yyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

	private static final int MAX_MOVE_RETRY = 3;

	/**
	 * 자원 파일을 trash 디렉토리로 이동한다.
	 *
	 * @param resourcePath 원래 자원 경로 (mv 대상)
	 * @param resourceType 자원 도메인 타입
	 * @param resourceId   자원 PK
	 * @return 이동 후 trash 내 절대 경로 (DB.trashed_path 에 저장)
	 */
	public Path moveToTrash(Path resourcePath, ResourceType resourceType, Long resourceId) {
		Path trashDir = trashPolicy.resolveTrashDirectory(resourceType, resourceId);
		try {
			Files.createDirectories(trashDir);
		} catch (IOException e) {
			throw new TrashMoveFailedException("trash 디렉토리 생성 실패 : " + trashDir, e);
		}

		Path trashedPath = trashDir.resolve(generateTrashedFileName(resourcePath));
		moveWithRetry(resourcePath, trashedPath);
		log.info("[trash] moveToTrash type={} id={} from={} to={}", resourceType, resourceId, resourcePath, trashedPath);
		return trashedPath;
	}

	/**
	 * trash 자원을 원래 경로로 이동한다 (restore).
	 *
	 * @param trashedPath  현재 trash 내 자원 경로
	 * @param originalPath 복원 대상 절대 경로
	 */
	public void moveBack(Path trashedPath, Path originalPath) {
		moveWithRetry(trashedPath, originalPath);
		log.info("[trash] moveBack from={} to={}", trashedPath, originalPath);
	}

	/**
	 * 원본 파일명 + ms timestamp + UUID8 suffix 합성. 같은 ms 안에 두 번 호출되어도 UUID 로 고유 보장.
	 */
	private String generateTrashedFileName(Path resourcePath) {
		String original = resourcePath.getFileName().toString();
		String base;
		String ext;
		int dot = original.lastIndexOf('.');
		if (dot > 0) {
			base = original.substring(0, dot);
			ext = original.substring(dot); // includes leading dot
		} else {
			base = original;
			ext = "";
		}
		String ts = TS_FORMAT.format(Instant.now());
		String uuid8 = UUID.randomUUID().toString().substring(0, 8);
		return base + "_" + ts + "_" + uuid8 + ext;
	}

	/**
	 * Files.move 를 ATOMIC_MOVE 우선 시도, 실패 시 일반 move 로 fallback. 최대 {@link #MAX_MOVE_RETRY} 회 재시도 후
	 * critical {@link TrashMoveFailedException}.
	 */
	private void moveWithRetry(Path source, Path target) {
		IOException lastError = null;
		for (int attempt = 1; attempt <= MAX_MOVE_RETRY; attempt++) {
			try {
				try {
					Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
				} catch (IOException atomicFail) {
					Files.move(source, target);
				}
				return;
			} catch (IOException e) {
				lastError = e;
				log.warn("[trash] move attempt {} 실패. source={} target={} msg={}", attempt, source, target, e.getMessage());
			}
		}
		throw new TrashMoveFailedException(
				"trash 이동 " + MAX_MOVE_RETRY + "회 retry 실패 — 운영자 즉시 점검 필요 : " + source + " → " + target,
				lastError
		);
	}
}
