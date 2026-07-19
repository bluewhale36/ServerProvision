package com.example.serverprovision.global.trash;

import com.example.serverprovision.global.marker.ResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * HF4-3 (F-7) — trash 이동 리네임 규칙 단위 테스트.
 *
 * <p>디렉토리 이름의 점을 확장자로 오분리하던 결함("v1.10" → "v1_….10")의 회귀 방지가 목적.
 * {@code TrashPolicy} 만 mock 하고 mv 는 {@code @TempDir} 실제 파일시스템으로 태워
 * {@code generateTrashedFileName} + {@code moveWithRetry} 경로를 함께 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class TrashServiceTest {

	@Mock TrashPolicy trashPolicy;

	TrashService service;

	@BeforeEach
	void setUp() {
		service = new TrashService(trashPolicy);
	}

	@Test
	@DisplayName("moveToTrash : 파일은 확장자를 보존하며 timestamp+UUID8 접미사가 붙는다 (기존 동작 유지)")
	void file_keepsExtension(@TempDir Path dir) throws Exception {
		Path src = dir.resolve("a.iso");
		Files.writeString(src, "iso-bytes");
		given(trashPolicy.resolveTrashDirectory(ResourceType.OS_ISO, 1L)).willReturn(dir.resolve("trash"));

		Path moved = service.moveToTrash(src, ResourceType.OS_ISO, 1L);

		assertThat(Files.isRegularFile(moved)).isTrue();
		assertThat(Files.exists(src)).isFalse();
		assertThat(moved.getFileName().toString())
				.matches("a_\\d{6}-\\d{6}-\\d{3}_[0-9a-f]{8}\\.iso");
	}

	@Test
	@DisplayName("moveToTrash : 디렉토리는 확장자 분리 없이 전체 이름 뒤에 접미사 — 'v1.10' 점 미분리 (HF4-3 F-7)")
	void directory_noExtensionSplit(@TempDir Path dir) throws Exception {
		Path src = Files.createDirectory(dir.resolve("v1.10"));
		Files.writeString(src.resolve("firmware.bin"), "x");
		given(trashPolicy.resolveTrashDirectory(ResourceType.SUBPROGRAM, 7L)).willReturn(dir.resolve("trash"));

		Path moved = service.moveToTrash(src, ResourceType.SUBPROGRAM, 7L);

		// ".10" 이 접미사 뒤로 떨어져 나가지 않고 전체 이름이 그대로 보존되어야 한다.
		assertThat(moved.getFileName().toString())
				.matches("v1\\.10_\\d{6}-\\d{6}-\\d{3}_[0-9a-f]{8}");
		assertThat(Files.isDirectory(moved)).isTrue();
		assertThat(Files.exists(moved.resolve("firmware.bin"))).isTrue();
		assertThat(Files.exists(src)).isFalse();
	}

	@Test
	@DisplayName("moveToTrash : 점 없는 파일 이름은 전체 이름 뒤에 접미사 (기존 동작 유지)")
	void file_withoutDot(@TempDir Path dir) throws Exception {
		Path src = dir.resolve("bundle");
		Files.writeString(src, "x");
		given(trashPolicy.resolveTrashDirectory(ResourceType.OS_ISO, 2L)).willReturn(dir.resolve("trash"));

		Path moved = service.moveToTrash(src, ResourceType.OS_ISO, 2L);

		assertThat(moved.getFileName().toString())
				.matches("bundle_\\d{6}-\\d{6}-\\d{3}_[0-9a-f]{8}");
	}
}
