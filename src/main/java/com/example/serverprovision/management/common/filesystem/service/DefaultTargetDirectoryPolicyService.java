package com.example.serverprovision.management.common.filesystem.service;

import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.common.filesystem.exception.*;
import com.example.serverprovision.management.common.filesystem.policy.BundleFilePolicy;
import com.example.serverprovision.management.os.exception.DirectoryMissingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * BIOS/BMC 번들 업로드 전 target directory 정책의 공통 기본 구현.
 */
@Slf4j
@Service
public class DefaultTargetDirectoryPolicyService implements TargetDirectoryPolicyService {

	@Override
	public void validateForIntent(Path targetDirectory, boolean allowCreateDirectory) {
		if (Files.exists(targetDirectory)) {
			if (!Files.isDirectory(targetDirectory)) {
				throw new TargetDirectoryNotEmptyException(targetDirectory + " (디렉토리가 아닌 파일 점유)");
			}
			try (Stream<Path> children = Files.list(targetDirectory)) {
				if (children.anyMatch(path -> !BundleFilePolicy.isIgnorable(path))) {
					if (Files.exists(targetDirectory.resolve(ProvisionMarkerService.MARKER_FILENAME))) {
						throw new MarkerConflictException(targetDirectory.toString());
					}
					throw new TargetDirectoryNotEmptyException(targetDirectory.toString());
				}
			} catch (IOException e) {
				log.warn("[validateForIntent] targetDirectory 점검 중 IO 예외 (통과) : {}", targetDirectory, e);
			}
			return;
		}

		Path parent = targetDirectory.getParent();
		if (parent != null && !Files.exists(parent) && !allowCreateDirectory) {
			throw new DirectoryMissingException(parent.toString());
		}
	}

	@Override
	public void prepareForUpload(Path targetDirectory, boolean allowCreateDirectory) {
		ensureParentDirectory(targetDirectory, allowCreateDirectory);
		verifyTargetNotOccupied(targetDirectory);
	}

	private void ensureParentDirectory(Path targetPath, boolean allowCreateDirectory) {
		Path parent = targetPath.getParent();
		if (parent == null || Files.exists(parent)) return;
		if (!allowCreateDirectory) {
			throw new DirectoryMissingException(parent.toString());
		}
		try {
			Files.createDirectories(parent);
		} catch (IOException e) {
			throw new BundleExtractionException("상위 디렉토리 생성 실패 : " + parent, e);
		}
	}

	@Override
	public void prepareForExistingDirectoryRegistration(Path targetDirectory) {
		if (!Files.exists(targetDirectory) || !Files.isDirectory(targetDirectory)) {
			throw new MissingTargetDirectoryException(String.valueOf(targetDirectory));
		}
		if (Files.exists(targetDirectory.resolve(ProvisionMarkerService.MARKER_FILENAME))) {
			throw new MarkerConflictException(targetDirectory.toString());
		}
		try (Stream<Path> children = Files.list(targetDirectory)) {
			boolean hasContent = children.anyMatch(path -> !BundleFilePolicy.isIgnorable(path));
			if (!hasContent) {
				throw new EmptyTargetDirectoryException(targetDirectory.toString());
			}
		} catch (IOException e) {
			throw new BundleExtractionException("기존 디렉토리 검증 실패 : " + targetDirectory, e);
		}
	}

	private void verifyTargetNotOccupied(Path targetDirectory) {
		if (!Files.exists(targetDirectory)) return;
		if (!Files.isDirectory(targetDirectory)) {
			throw new TargetDirectoryNotEmptyException(targetDirectory + " (디렉토리가 아닌 파일 점유)");
		}
		try (Stream<Path> children = Files.list(targetDirectory)) {
			List<Path> occupied = children.filter(path -> !BundleFilePolicy.isIgnorable(path)).toList();
			if (occupied.isEmpty()) return;
			if (Files.exists(targetDirectory.resolve(ProvisionMarkerService.MARKER_FILENAME))) {
				throw new MarkerConflictException(targetDirectory.toString());
			}
			throw new TargetDirectoryNotEmptyException(targetDirectory.toString());
		} catch (IOException e) {
			throw new BundleExtractionException("대상 디렉토리 점검 실패 : " + targetDirectory, e);
		}
	}
}
