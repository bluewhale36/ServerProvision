package com.example.serverprovision.management.common.nudge.dto;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.enums.OSName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 중복 확인 창 후보 한 건의 응답 모양.
 * 후보 줄 보조 글자의 부분({@code detail} · {@code trashedAtText})은 이름과 버전이 같은 휴지통 후보를 휴지통 이동 시각으로 구별하게 한다(HF30-5).
 */
class NudgeConflictEntryTest {

	private static final ObjectMapper JSON = new ObjectMapper();
	private static final Instant TRASHED_AT = Instant.parse("2026-11-17T05:05:09Z");

	/** 실행 환경의 시간대에 맞춘 기대 시각. 휴지통 목록과 같은 형식 · 서버 시간대다. */
	private static String expectedTime(Instant instant) {
		return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(instant);
	}

	private static BoardBIOS trashedBios(Instant trashedAt) {
		return BoardBIOS.builder().id(7L).name("MZ33 BIOS").version("R01")
				.manifestHash("h7").isDeleted(true).isDeprecated(false).trashedAt(trashedAt).build();
	}

	@Test
	@DisplayName("of : 자원명 입력이 맞춰야 하는 displayName 을 서버 대조와 같은 Markable 값으로 싣는다(HF30-3)")
	void of_carriesDisplayName() {
		BoardBIOS bios = trashedBios(TRASHED_AT);

		NudgeConflictEntry entry = NudgeConflictEntry.of(bios, bios.getName(), bios.getVersion());

		assertThat(entry.id()).isEqualTo(7L);
		assertThat(entry.state()).isEqualTo(LifecycleStage.SOFT_DELETED);
		assertThat(entry.displayName()).isEqualTo(bios.displayName());
	}

	@Test
	@DisplayName("of : 보조 글자 부분은 버전과 엔티티의 휴지통 이동 시각이다")
	void of_detailIsVersion_trashedAtFromEntity() {
		BoardBIOS bios = trashedBios(TRASHED_AT);

		NudgeConflictEntry entry = NudgeConflictEntry.of(bios, bios.getName(), bios.getVersion());

		assertThat(entry.trashedAt()).isEqualTo(TRASHED_AT);
		assertThat(entry.detail()).isEqualTo("버전 R01");
		assertThat(entry.trashedAtText()).isEqualTo(expectedTime(TRASHED_AT));
	}

	@Test
	@DisplayName("ofPath : ISO 는 버전 대신 등록 경로를 보인다")
	void ofPath_detailIsPath() {
		OSMetadata os = OSMetadata.builder().osName(OSName.ROCKY_LINUX).osVersion("9.4").build();
		ISO iso = ISO.builder().id(55L).osMetadata(os).isoPath("/opt/iso/rocky/9/dvd.iso").manifestHash("h55")
				.isDeleted(true).isDeprecated(false).trashedAt(TRASHED_AT).build();

		NudgeConflictEntry entry = NudgeConflictEntry.ofPath(iso, "9.4", iso.getIsoPath());

		assertThat(entry.detail()).isEqualTo("/opt/iso/rocky/9/dvd.iso");
		assertThat(entry.trashedAtText()).isEqualTo(expectedTime(TRASHED_AT));
	}

	@Test
	@DisplayName("이동 시각이 없는 행(정합 깨짐) : 시각 글자가 비고 화면이 '-' 를 보인다")
	void withoutTrashedAt_textIsNull() {
		BoardBIOS bios = trashedBios(null);

		NudgeConflictEntry entry = NudgeConflictEntry.of(bios, bios.getName(), bios.getVersion());

		assertThat(entry.trashedAtText()).isNull();
	}

	@Test
	@DisplayName("직렬화 : 보조 글자 부분과 datetime 용 원값이 실리고, JS 판정용 state 원문은 그대로 남는다")
	void serializesNoteParts() {
		NudgeConflictEntry entry = NudgeConflictEntry.of(trashedBios(TRASHED_AT), "MZ33 BIOS", "R01");

		JsonNode node = JSON.valueToTree(entry);

		assertThat(node.get("state").asString()).isEqualTo("SOFT_DELETED");
		assertThat(node.get("detail").asString()).isEqualTo("버전 R01");
		assertThat(node.get("trashedAtText").asString()).isEqualTo(expectedTime(TRASHED_AT));
		assertThat(Instant.parse(node.get("trashedAt").asString())).isEqualTo(TRASHED_AT);
		assertThat(node.has("registeredAt")).isFalse();
	}
}
