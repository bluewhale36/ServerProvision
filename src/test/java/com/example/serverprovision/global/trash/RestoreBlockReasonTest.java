package com.example.serverprovision.global.trash;

import com.example.serverprovision.global.trash.exception.GhostRowRestoreNotAllowedException;
import com.example.serverprovision.management.common.exception.RestoreTrashLostException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MK4-5-1 — 복원 차단 사유의 사용자 문구 SSOT 회귀 고정.
 *
 * <p>이 슬라이스가 고친 결함의 성질이 <b>문구</b>였다. 안내가 이 행에 없는 버튼을 가리켰고, 실제와
 * 다른 드리프트 종류를 지목했으며, 화면 어디에도 없는 말({@code reconciliation} · {@code drift apply} ·
 * {@code row} · {@code ghost})을 썼다. 문구는 코드가 돌아도 조용히 다시 새므로, 새지 않는다는 것을
 * 테스트가 붙들고 있어야 한다({@code DriftKindTest} 가 같은 이유로 존재한다).</p>
 */
class RestoreBlockReasonTest {

	/**
	 * 화면에 없는 말. 사용자에게 보이는 문구에 이 낱말이 들어가면 그 안내는 가리킬 대상이 없다.
	 * 메뉴 이름은 '자원 무결성 점검' 이고, 버튼 이름은 '해결' 이며, 사용자가 보는 것은 자원 표시명이다.
	 */
	private static final List<String> WORDS_NOT_ON_SCREEN =
			List.of("reconciliation", "drift", "ghost", "row", "apply", "GHOST_DB_ROW");

	@Test
	@DisplayName("전 사유의 라벨 · 안내 문구가 비어 있지 않다")
	void allReasonsHaveUserFacingTexts() {
		for (RestoreBlockReason reason : RestoreBlockReason.values()) {
			assertThat(reason.getLabel()).as("%s.label", reason).isNotBlank();
			assertThat(reason.getGuidance()).as("%s.guidance", reason).isNotBlank();
			assertThat(reason.getBadgeColor()).as("%s.badgeColor", reason).isNotBlank();
		}
	}

	@Test
	@DisplayName("안내 문구가 왜 막혔는지에서 끝나지 않고 다음에 무엇을 할지까지 말한다")
	void guidanceTellsWhatToDoNext() {
		for (RestoreBlockReason reason : RestoreBlockReason.values()) {
			// 거절만 하고 갈 곳을 말하지 않으면 막다른 길이 된다. 권유형 종결이 그 신호다.
			assertThat(reason.getGuidance())
					.as("%s — 다음 행동을 말하지 않는다", reason)
					.containsAnyOf("주세요", "있습니다");
		}
	}

	@Test
	@DisplayName("사용자 문구에 화면에 없는 말이 섞이지 않는다")
	void userFacingTextsAvoidWordsNotOnScreen() {
		for (RestoreBlockReason reason : RestoreBlockReason.values()) {
			assertNoWordsOffScreen(reason + ".label", reason.getLabel());
			assertNoWordsOffScreen(reason + ".guidance", reason.getGuidance());
			if (reason.getNextScreen() != null) {
				assertNoWordsOffScreen(reason + ".nextScreen", reason.getNextScreen());
			}
		}
	}

	@Test
	@DisplayName("다른 화면으로 보내는 사유는 그 화면의 실제 메뉴 이름을 쓴다")
	void nextScreenUsesRealMenuName() {
		assertThat(RestoreBlockReason.TRASH_LOST.getNextScreen()).isEqualTo("자원 무결성 점검");
		// 이 행 안에서 끝나는 사유는 다른 화면을 가리키지 않는다 — 가리킬 곳이 없는데 링크를
		// 띄우면 사용자가 없는 화면을 찾게 된다.
		assertThat(RestoreBlockReason.GHOST.hasNextScreen()).isFalse();
		assertThat(RestoreBlockReason.PARENT_DELETED.hasNextScreen()).isFalse();
	}

	@Test
	@DisplayName("부모 삭제만 행 배지를 띄우지 않는다 — 부모 줄에 이미 같은 문구가 있다")
	void onlyParentDeletedSkipsRowBadge() {
		assertThat(RestoreBlockReason.PARENT_DELETED.isRowBadge()).isFalse();
		assertThat(RestoreBlockReason.GHOST.isRowBadge()).isTrue();
		assertThat(RestoreBlockReason.TRASH_LOST.isRowBadge()).isTrue();
	}

	// ── 거절 안내 두 건 ──────────────────────────────────────────────

	@Test
	@DisplayName("유령 기록 거절 안내가 이 행에 실재하는 [정리] 를 가리킨다")
	void ghostRejectionPointsToExistingButton() {
		String message = new GhostRowRestoreNotAllowedException("Rocky Linux 9.6 dvd.iso").getMessage();

		assertThat(message).contains("Rocky Linux 9.6 dvd.iso");
		assertThat(message).contains("[정리]");
		assertNoWordsOffScreen("GhostRowRestoreNotAllowedException", message);
	}

	@Test
	@DisplayName("휴지통 소실 거절 안내가 실제 종류와 실제 메뉴를 가리킨다")
	void trashLostRejectionPointsToRealKindAndMenu() {
		String message = new RestoreTrashLostException("/opt/.soft-deleted/OS_ISO/9/dvd.iso").getMessage();

		// 종전에는 이 행에 없는 [정리] 를 가리켰다.
		assertThat(message).doesNotContain("[정리]");
		// 종전에는 종류를 GHOST_DB_ROW 로 지목했다. 실제로 이 상태를 보고하는 종류는 이것이다.
		assertThat(message).contains("휴지통 자원 소실");
		assertThat(message).contains("자원 무결성 점검");
		assertThat(message).contains("/opt/.soft-deleted/OS_ISO/9/dvd.iso");
		assertNoWordsOffScreen("RestoreTrashLostException", message);
	}

	private static void assertNoWordsOffScreen(String what, String text) {
		String lowered = text.toLowerCase(Locale.ROOT);
		for (String word : WORDS_NOT_ON_SCREEN) {
			assertThat(lowered)
					.as("%s — 화면에 없는 말 '%s' 가 사용자 문구에 남아 있다", what, word)
					.doesNotContain(word.toLowerCase(Locale.ROOT));
		}
	}
}
