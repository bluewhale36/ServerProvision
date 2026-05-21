package com.example.serverprovision.management.common.filesystem.service;

import com.example.serverprovision.global.security.PathPolicyService;
import com.example.serverprovision.global.security.config.FileSystemSecurityProperties;
import com.example.serverprovision.management.common.filesystem.dto.DirectoryBrowseRequest;
import com.example.serverprovision.management.common.filesystem.dto.DirectoryListingResponse;
import com.example.serverprovision.management.common.filesystem.exception.BrowseTargetNotDirectoryException;
import com.example.serverprovision.management.common.filesystem.exception.BrowseTargetNotFoundException;
import com.example.serverprovision.management.common.filesystem.exception.DirectoryBrowseIoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * BIOS / BMC / ISO / Subprogram 폼의 서버 경로 탐색 공통 로직.
 * <p>S3 — 입력 경로는 {@link PathPolicyService} 통과 후에만 사용.
 * 응답 entry 갯수가 {@link FileSystemSecurityProperties#maxEntries()} 초과면 처음 N 개만 반환 + {@code truncated=true}.</p>
 */
@Service
@RequiredArgsConstructor
public class DirectoryBrowseService {

	private final PathPolicyService pathPolicyService;
	private final FileSystemSecurityProperties fileSystemSecurityProperties;

	public DirectoryListingResponse browse(DirectoryBrowseRequest request) {
		String raw = request.path();
		// S5-1 — 신규 등록 폼 첫 진입 시 빈 path 도 자동 치환 (이전엔 InvalidBrowsePathException 거절).
		// 명시 path 는 기존대로 assertReadablePath 통과해 정규화 + allowed-roots 검증.
		Path target = (raw == null || raw.isBlank())
				? pathPolicyService.firstAllowedRoot()
				: pathPolicyService.assertReadablePath(raw);
		if (!Files.exists(target)) {
			throw new BrowseTargetNotFoundException(target.toString());
		}
		if (!Files.isDirectory(target)) {
			throw new BrowseTargetNotDirectoryException(target.toString());
		}

		int maxEntries = fileSystemSecurityProperties.maxEntries();
		List<DirectoryListingResponse.Entry> entries = new ArrayList<>();
		boolean truncated = false;

		try (Stream<Path> children = Files.list(target)) {
			List<Path> sorted = children
					.sorted(Comparator.comparing(p -> p.getFileName().toString()))
					.toList();
			for (Path p : sorted) {
				if (entries.size() >= maxEntries) {
					truncated = true;
					break;
				}
				String name = p.getFileName().toString();
				if (Files.isDirectory(p)) {
					entries.add(DirectoryListingResponse.Entry.directory(name));
				} else if (request.includeFiles()) {
					long size = -1L;
					try {
						size = Files.size(p);
					} catch (IOException ignore) {
					}
					entries.add(DirectoryListingResponse.Entry.file(name, size));
				}
			}
		} catch (IOException e) {
			throw new DirectoryBrowseIoException("디렉토리 열람 중 오류 : " + e.getMessage(), e);
		}

		// 부모 경로 — allowed-roots 안에 있으면 노출 / 밖이면 null (클라이언트가 '상위' 버튼 비활성화).
		// 사용자가 root 경계를 넘어가려 시도해 에러 메시지를 받기 전에 UI 단계에서 차단하기 위함.
		Path parent = target.getParent();
		String parentPath = null;
		if (parent != null) {
			try {
				pathPolicyService.assertReadablePath(parent.toString());
				parentPath = parent.toString();
			} catch (RuntimeException ignored) {
				// 부모가 allowed-roots 밖 → null
			}
		}
		return new DirectoryListingResponse(
				target.toString(),
				parentPath,
				entries,
				truncated
		);
	}
}
