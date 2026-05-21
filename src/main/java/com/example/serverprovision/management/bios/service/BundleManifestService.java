package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.management.common.filesystem.exception.BundleExtractionException;
import com.example.serverprovision.management.common.filesystem.policy.BundleFilePolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * 번들 트리 스캔 · manifestHash 계산 · 파일 수 · 총 바이트 집계.
 * <p>알고리즘 : marker 파일({@code .provision.json}) 을 제외한 모든 일반 파일에 대해 정규화된 상대경로(슬래시 통일) 와
 * 각 파일의 SHA-256 hex 를 계산해 {@code "<relPath>||<sha256>"} 문자열을 만든 뒤 lex 정렬하고
 * 줄바꿈으로 연결해 다시 SHA-256 해시한다. 같은 내용 · 같은 트리면 항상 같은 해시가 나온다.</p>
 */
@Slf4j
@Service
public class BundleManifestService {

	public ManifestSummary compute(Path treeRoot) {
		if (!Files.isDirectory(treeRoot)) {
			throw new BundleExtractionException("트리 루트가 디렉토리가 아닙니다 : " + treeRoot);
		}
		List<Entry> entries = new ArrayList<>();
		long totalBytes = 0L;

		try (Stream<Path> walker = Files.walk(treeRoot)) {
			for (Path p : (Iterable<Path>) walker::iterator) {
				if (!Files.isRegularFile(p)) continue;
				if (p.getFileName().toString().equals(com.example.serverprovision.global.marker.service.ProvisionMarkerService.MARKER_FILENAME))
					continue;
				if (BundleFilePolicy.isIgnorable(p)) continue;
				String rel = toRelative(treeRoot, p);
				String sha = sha256Hex(p);
				long size = Files.size(p);
				entries.add(new Entry(rel, sha));
				totalBytes += size;
			}
		} catch (IOException e) {
			throw new BundleExtractionException("트리 스캔 실패 : " + treeRoot, e);
		}

		entries.sort(Comparator.comparing(Entry::relPath));

		try {
			MessageDigest outer = MessageDigest.getInstance("SHA-256");
			for (Entry e : entries) {
				String line = e.relPath() + "||" + e.sha256() + "\n";
				outer.update(line.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			}
			String manifestHash = HexFormat.of().formatHex(outer.digest());
			return new ManifestSummary(manifestHash, entries.size(), totalBytes);
		} catch (NoSuchAlgorithmException e) {
			throw new BundleExtractionException("SHA-256 계산 실패", e);
		}
	}

	private static String toRelative(Path root, Path file) {
		return root.relativize(file).toString().replace('\\', '/');
	}

	private static String sha256Hex(Path file) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			try (InputStream in = Files.newInputStream(file);
			     DigestInputStream dis = new DigestInputStream(in, md)) {
				byte[] buf = new byte[8192];
				while (dis.read(buf) >= 0) { /* drain */ }
			}
			return HexFormat.of().formatHex(md.digest());
		} catch (IOException | NoSuchAlgorithmException e) {
			throw new BundleExtractionException("파일 SHA-256 계산 실패 : " + file, e);
		}
	}

	private record Entry(
			String relPath,
			String sha256
	) {

	}


	public record ManifestSummary(
			String manifestHash,
			int fileCount,
			long totalBytes
	) {

	}
}
