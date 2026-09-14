package com.example.serverprovision.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>S10 — 폼 제출 규약 강제. 이 테스트가 존재하는 이유를 지우지 말 것.</b>
 *
 * <p>U3-2-b 에서 세팅 정의서 상세에 lifecycle 폼을 추가하면서 제출 방식 마커를 빠뜨렸고, 서버가 거절한
 * 응답의 JSON 원문이 사용자 화면에 그대로 노출됐다. 규약은 이미 문서에 있었고 지켜지지 않았다. 그래서
 * S10 은 두 가지를 했다 — 기본값을 뒤집어 <b>가로채는 것이 기본</b>이 되게 했고, 그 기본값에서 빠져나가는
 * 폼이 근거를 남기도록 이 테스트로 고정했다.</p>
 *
 * <p>여기서 지키는 것은 "누가 문서를 읽었는가" 와 무관한 성질뿐이다. 사람의 주의력을 요구하는 규약은
 * 다시 잊힌다는 것이 이 슬라이스의 출발점이다.</p>
 */
class FormSubmissionConventionTest {

	private static final Path TEMPLATES = Path.of("src/main/resources/templates");
	private static final Path JAVA = Path.of("src/main/java/com/example/serverprovision");

	private static final Pattern FORM_TAG = Pattern.compile("<form\\b[^>]*>", Pattern.DOTALL);
	private static final Pattern NATIVE_MARKER = Pattern.compile("data-native-submit\\s*=\\s*\"([^\"]*)\"");

	/**
	 * S18 — Spring Security 의 필터가 서블릿 앞에서 처리하는 폼. 재렌더 핸들러가 없는데도 네이티브여야 한다(전역 fetch 의
	 * multipart 본문을 필터가 읽지 못한다). 등식에서 이 목록만 뺀다 — 상수 +1 로 두면 다른 폼이 마커를 빠뜨려도 총합이
	 * 우연히 맞을 수 있다. 목록에 없는 네이티브 폼은 종전대로 등식에 걸린다.
	 */
	private static final Map<String, String> FILTER_HANDLED_FORMS = Map.of(
			"security/login.html", "UsernamePasswordAuthenticationFilter 가 로그인 POST 를 처리한다",
			"fragments/layout.html", "LogoutFilter 가 로그아웃 POST 를 처리한다");

	/**
	 * {@code @PostMapping} 부터 파라미터 목록 여는 괄호까지. 사이에 붙는 다른 어노테이션
	 * ({@code @ResponseBody} 등)을 함께 담아야 XHR 핸들러를 가려낼 수 있다.
	 */
	private static final Pattern POST_HANDLER = Pattern.compile(
			"@PostMapping\\b[^\\n]*\\n(?:\\s*@[^\\n]*\\n)*\\s*public\\s+[^\\s(]+(?:<[^>]*>)?\\s+(\\w+)\\s*\\(");

	/* ═══════════ 폐기된 마커가 되살아나지 않는다 ═══════════ */

	/**
	 * {@code data-async-submit} 은 "이 폼만 fetch 로 보낸다" 는 opt-in 이었다. 붙이는 것을 잊으면
	 * 곧바로 raw JSON 노출이라 사고의 원인 그 자체였다. 되살아나면 기본값이 다시 뒤집힌 것이다.
	 */
	@Test
	@DisplayName("폐기된 data-async-submit 마커가 템플릿에 없다")
	void obsoleteAsyncMarkerIsGone() {
		List<String> offenders = templateFiles()
				.filter(p -> read(p).contains("data-async-submit"))
				.map(TEMPLATES::relativize)
				.map(Path::toString)
				.sorted()
				.toList();

		assertThat(offenders)
				.as("data-async-submit 은 S10 에서 폐기됐다. 가로채기가 기본이므로 마커를 붙일 필요가 없고, "
						+ "이 마커가 다시 보인다면 opt-in 구조로 되돌아간 것이다")
				.isEmpty();
	}

	/* ═══════════ 기본값에서 빠져나가는 폼은 근거를 남긴다 ═══════════ */

	@Test
	@DisplayName("data-native-submit 은 사유를 반드시 적는다")
	void nativeSubmitOptOutCarriesReason() {
		List<String> offenders = new ArrayList<>();
		templateFiles().forEach(p -> {
			Matcher form = FORM_TAG.matcher(read(p));
			while (form.find()) {
				String tag = form.group();
				if (!tag.contains("data-native-submit")) continue;
				Matcher reason = NATIVE_MARKER.matcher(tag);
				if (!reason.find() || reason.group(1).isBlank()) {
					offenders.add(TEMPLATES.relativize(p).toString());
				}
			}
		});

		assertThat(offenders)
				.as("네이티브 제출로 빠져나가는 폼은 왜 그런지 적어야 한다. 사유가 없으면 다음 사람이 "
						+ "실수로 붙인 것인지 의도인지 구분할 수 없다")
				.isEmpty();
	}

	/* ═══════════ 서버 쪽 재렌더 핸들러와 템플릿 표기가 어긋나지 않는다 ═══════════ */

	/**
	 * HF9 이전에는 "flash 를 살리기 위해" 네이티브로 빠진 폼 4 개를 이름으로 등재해 아래 수 대조에서 덜어냈다.
	 * HF9 가 그 사정을 서버({@code XhrRedirectFilter} — XHR 리다이렉트를 200 + X-Redirect-Location 으로)와
	 * {@code form-submit.js}(브라우저 이동)에서 없앴으므로 목록도 없다 — 네이티브 제출의 정당한 이유는 재렌더 하나뿐이다.
	 * 아래 {@link #nativeSubmitNeverForFlash} 가 그 우회의 재유입을 막는다.
	 */
	/**
	 * 핵심 가드다. 실패 시 뷰를 다시 렌더하는 핸들러는 {@code BindingResult} 를 받는다 — 그것이 재렌더의
	 * 유일한 목적이기 때문이다. 그런 핸들러를 부르는 폼을 fetch 로 가로채면 서버가 돌려준 재렌더 HTML 을
	 * 버리게 되어 필드별 오류 표시가 사라진다.
	 *
	 * <p>그래서 <b>재렌더 핸들러 수와 {@code data-native-submit} 폼 수가 같아야 한다</b>. 새 입력 폼을
	 * 추가하면서 표기를 잊으면 이 수가 어긋나 CP4 에서 빨간불이 뜬다 — U3-2-b 의 실패 방식과 정확히
	 * 반대 방향이다.</p>
	 *
	 * <p>U3-5-c 부터 HF9 전까지는 flash 보존을 이유로 네이티브가 된 폼을 이름으로 빼는 예외 목록이 있었다. HF9 가
	 * 그 이유를 없앴으므로 예외 없이 수가 같아야 한다.</p>
	 */
	@Test
	@DisplayName("재렌더(BindingResult) 핸들러 수 = data-native-submit 폼 수 − 필터 처리 폼(허용 목록)")
	void reRenderHandlersMatchNativeSubmitForms() {
		List<String> reRenderHandlers = reRenderHandlers();
		List<String> nativeForms = nativeSubmitForms().stream()
				.filter(form -> !FILTER_HANDLED_FORMS.containsKey(form))
				.toList();
		assertThat(nativeForms)
				.as("""
						검증 실패 시 뷰를 다시 렌더하는 핸들러(BindingResult 수령)와, 네이티브 제출을 유지하도록 \
						표기된 폼의 수가 어긋난다.
						  재렌더 핸들러 %d개: %s
						  표기된 폼   %d개: %s
						새 입력 폼에 data-native-submit 표기를 빠뜨렸는지 확인하라. flash 를 살리려고 네이티브로 빼는 것은 \
						HF9 이후 이유가 되지 않는다(XhrRedirectFilter + form-submit.js 가 PRG 의 flash 를 보존한다)."""
						.formatted(reRenderHandlers.size(), reRenderHandlers, nativeForms.size(), nativeForms))
				.hasSameSizeAs(reRenderHandlers);
	}

	/**
	 * HF9 — flash 를 살리려고 네이티브 제출로 빼던 우회(U3-5-c · E4-1-a-4)가 되살아나지 않는다. 그 사정은
	 * 서버와 가로채기 경로에서 없앴으므로, 사유에 flash 를 든 표기가 보이면 옛 우회를 다시 붙인 것이다.
	 */
	@Test
	@DisplayName("data-native-submit 의 사유에 flash 가 없다 — HF9 이후 flash 보존은 네이티브 제출의 이유가 아니다")
	void nativeSubmitNeverForFlash() {
		List<String> offenders = new ArrayList<>();
		templateFiles().forEach(p -> {
			Matcher form = FORM_TAG.matcher(read(p));
			while (form.find()) {
				Matcher reason = NATIVE_MARKER.matcher(form.group());
				if (reason.find() && reason.group(1).toLowerCase().contains("flash")) {
					offenders.add(TEMPLATES.relativize(p).toString());
				}
			}
		});
		assertThat(offenders)
				.as("flash 보존을 이유로 네이티브 제출로 빠진 폼이 있다. HF9 이후 가로채기 경로가 PRG 의 flash 를 "
						+ "보존하므로(XhrRedirectFilter · form-submit.js) 이 우회는 이유가 없다 — 표기를 떼라")
				.isEmpty();
	}

	/* ═══════════ 인터셉터가 조용히 빠지지 않는다 ═══════════ */

	@Test
	@DisplayName("전역 레이아웃이 form-submit.js 를 적재한다")
	void layoutLoadsGlobalInterceptor() {
		String layout = read(TEMPLATES.resolve("fragments/layout.html"));

		assertThat(layout)
				.as("전역 인터셉터가 레이아웃에서 빠지면 모든 액션 폼이 조용히 네이티브 제출로 돌아간다. "
						+ "화면마다 확인해야 알 수 있는 회귀라 여기서 막는다")
				.contains("/global/form-submit.js");
	}

	/* ═══════════ Accept 정규화의 안전 전제 ═══════════ */

	/**
	 * {@link DocumentNavigationAcceptFilter} 는 문서 이동 요청의 Accept 를 {@code text/html} 로 좁힌다.
	 * 문서 이동으로 도달하면서 HTML 이 아닌 응답을 내는 엔드포인트가 생기면 그 요청은 406 이 된다.
	 * 현재 그런 경로는 없다(다운로드 엔드포인트 0건). 앞으로 생기면 여기서 먼저 걸려, 필터에 예외 경로를
	 * 두든 다른 방법을 쓰든 <b>의식적으로</b> 결정하게 한다.
	 */
	@Test
	@DisplayName("문서 이동으로 도달하는 SSR 컨트롤러에 비 HTML 응답 생산자가 없다")
	void noBinaryProducersOnDocumentNavigablePaths() {
		List<String> offenders = javaFiles()
				.filter(p -> p.getFileName().toString().endsWith("Controller.java"))
				.filter(p -> {
					String src = read(p);
					if (src.contains("@RestController")) return false;   // XHR 전용 — 문서 이동 대상 아님
					return src.contains("APPLICATION_OCTET_STREAM")
							|| src.contains("ResponseEntity<Resource>")
							|| src.contains("InputStreamResource")
							|| src.contains("StreamingResponseBody")
							|| src.contains("Content-Disposition");
				})
				.map(p -> JAVA.relativize(p).toString())
				.sorted()
				.toList();

		assertThat(offenders)
				.as("파일 다운로드 같은 비 HTML 응답을 SSR 컨트롤러에 추가하면, 문서 이동 Accept 정규화가 "
						+ "그 경로를 406 으로 만든다. DocumentNavigationAcceptFilter 의 적용 범위를 함께 결정하라")
				.isEmpty();
	}

	/* ─────────────────────────── 스캔 헬퍼 ─────────────────────────── */

	/** 비 XHR POST 핸들러 중 {@code BindingResult} 를 받는 것 = 실패 시 뷰를 다시 렌더하는 핸들러. */
	private static List<String> reRenderHandlers() {
		List<String> found = new ArrayList<>();
		javaFiles()
				.filter(p -> p.getFileName().toString().endsWith("Controller.java"))
				.forEach(p -> {
					String src = read(p);
					boolean rest = src.substring(0, Math.max(0, src.indexOf("public class")))
							.contains("@RestController");
					Matcher m = POST_HANDLER.matcher(src);
					while (m.find()) {
						if (rest || src.substring(m.start(), m.end()).contains("@ResponseBody")) continue;
						if (parameterList(src, m.end()).contains("BindingResult")) {
							found.add(p.getFileName() + "#" + m.group(1));
						}
					}
				});
		return found.stream().sorted().toList();
	}

	private static List<String> nativeSubmitForms() {
		List<String> found = new ArrayList<>();
		templateFiles().forEach(p -> {
			Matcher form = FORM_TAG.matcher(read(p));
			while (form.find()) {
				if (form.group().contains("data-native-submit")) {
					found.add(TEMPLATES.relativize(p).toString());
				}
			}
		});
		return found.stream().sorted().toList();
	}

	/** 메서드 파라미터 목록만 잘라낸다. 괄호 깊이를 세어 어노테이션 안의 괄호에 속지 않는다. */
	private static String parameterList(String src, int afterOpenParen) {
		int depth = 1;
		int i = afterOpenParen;
		while (i < src.length() && depth > 0) {
			char c = src.charAt(i++);
			if (c == '(') depth++;
			else if (c == ')') depth--;
		}
		return src.substring(afterOpenParen, Math.max(afterOpenParen, i - 1));
	}

	private static Stream<Path> templateFiles() {
		return walk(TEMPLATES, ".html");
	}

	private static Stream<Path> javaFiles() {
		return walk(JAVA, ".java");
	}

	private static Stream<Path> walk(Path root, String suffix) {
		assertThat(Files.isDirectory(root)).as("스캔 대상 디렉토리가 없다: " + root.toAbsolutePath()).isTrue();
		try (Stream<Path> s = Files.walk(root)) {
			return s.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().endsWith(suffix))
					.toList().stream();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String read(Path p) {
		try {
			return Files.readString(p);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
