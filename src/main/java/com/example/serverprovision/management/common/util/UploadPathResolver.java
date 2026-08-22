package com.example.serverprovision.management.common.util;

import java.util.List;
import java.util.function.Function;

/**
 * 사용자 입력 자원 경로를 실제 저장 경로로 해석한다. (R12-1 에서 os.util.IsoPathResolver 를 공용화 이동 — ISO · BIOS 공유)
 * 경로가 {@code /} 로 끝나면 디렉토리로 간주하고 그 뒤에 업로드 파일명을 append 한다.
 *
 * <p>호출측이 "파일명 누락" 상황을 반드시 의식하게 하기 위해 예외 팩토리를 인자로 받는다 —
 * unchecked 예외로만 던지면 호출측에서 catch 를 잊어 500 으로 새어 나가는 사고가 났던 적이 있다
 * (IsoPathResolver.MissingFilenameException → 기본 핸들러 → 500).
 * 이제는 호출측이 자신의 도메인 예외를 명시적으로 지정해야 컴파일이 통과한다.</p>
 */
public final class UploadPathResolver {

	private UploadPathResolver() {
	}

	/**
	 * @param rawPath           사용자가 입력한 자원 경로 (예: "/opt/iso/rocky/9-7/" 또는 "/opt/iso/rocky/9-7/dvd.iso")
	 * @param filename          업로드된 파일의 원본 파일명 (예: "Rocky-9.7-x86_64-dvd.iso"). null 허용.
	 * @param onMissingFilename 디렉토리 경로인데 filename 이 없을 때 던질 예외 팩토리.
	 *                          인자는 문제가 된 {@code rawPath}. 호출측 도메인 예외(409) 로 변환하는 것을 권장.
	 * @return 디렉토리 경로이면 {@code rawPath + filename}, 아니면 {@code rawPath} 원본.
	 */
	public static String resolve(
			String rawPath, String filename,
			Function<String, ? extends RuntimeException> onMissingFilename
	) {
		if (rawPath == null || rawPath.isBlank()) return rawPath;
		if (!rawPath.endsWith("/")) return rawPath;
		if (filename == null || filename.isBlank()) {
			throw onMissingFilename.apply(rawPath);
		}
		return rawPath + filename;
	}

	/**
	 * R12-1 — <b>업로드 파일이 함께 오는 경우</b>의 경로 해석. {@link #resolve} 의 "{@code /} 로 끝나면 디렉토리"
	 * 규칙에 더해 {@link #looksLikeDirectory} 판정으로 디렉토리 의도를 흡수한다.
	 *
	 * <p>끝 슬래시는 사용자가 자주 빠뜨리는 표기이고, 그때 {@code /firmware/v310} 같은 입력이
	 * "v310 이라는 이름으로 저장하라"로 해석되면 확장자 정책에 걸려 <b>첨부 파일이 정상인데도 거절</b>된다.
	 * 실제로 R12-1 CP5 직후 사용자가 이 함정에 부딪혔다.</p>
	 *
	 * <p>업로드가 없는 등록(claim)에는 쓰지 않는다 — 그 경우 경로는 그 자체로 대상 파일을 가리킨다.</p>
	 *
	 * @param allowedExtensions 해당 자원 · 제조사의 허용 확장자(소문자 · 점 없음). 비어 있으면 제한이 없는
	 *                          것으로 보고 점 유무만으로 판정한다. 공용 유틸이 도메인 정책을 알지 않도록
	 *                          호출측이 주입한다.
	 */
	public static String resolveForUpload(
			String rawPath, String filename, List<String> allowedExtensions,
			Function<String, ? extends RuntimeException> onMissingFilename
	) {
		if (rawPath == null || rawPath.isBlank()) return rawPath;
		String normalized = looksLikeDirectory(rawPath, allowedExtensions) && !rawPath.endsWith("/")
				? rawPath + "/"
				: rawPath;
		return resolve(normalized, filename, onMissingFilename);
	}

	/**
	 * 경로가 디렉토리를 가리키는 것으로 보이는지 판정한다. 화면(bios-new.js · bmc-new.js)이 같은 규칙을
	 * 복제하므로 판정을 바꾸면 양쪽을 함께 고쳐야 한다.
	 *
	 * <p>R12-2 (D8) — 판정 기준을 <b>허용 확장자 목록</b>으로 옮겼다. 종전의 "점이 없으면 디렉토리" 규칙은
	 * 버전 번호를 디렉토리 이름으로 쓰는 운영 관례({@code …/2.03})를 걸러내지 못해, 정상 파일을 첨부하고도
	 * 거절되는 경우가 남아 있었다. 마지막 세그먼트의 확장자가 그 자원의 허용 목록에 없으면 <b>파일명일 수
	 * 없으므로</b> 디렉토리로 본다 — {@code …/2.03} 은 확장자 {@code 03} 이 목록 밖이라 디렉토리로 구제되고,
	 * {@code …/custom.RBU} 는 목록 안이라 저장할 파일 경로로 남는다.</p>
	 *
	 * <p>허용 목록이 없는 제조사는 판정 근거가 없으므로 종전의 점 유무 규칙을 그대로 쓴다.</p>
	 */
	public static boolean looksLikeDirectory(String rawPath, List<String> allowedExtensions) {
		if (rawPath == null || rawPath.isBlank()) return false;
		if (rawPath.endsWith("/")) return true;
		int slash = rawPath.lastIndexOf('/');
		String lastSegment = slash < 0 ? rawPath : rawPath.substring(slash + 1);
		int dot = lastSegment.lastIndexOf('.');
		if (dot < 0) return true;
		if (allowedExtensions == null || allowedExtensions.isEmpty()) return false;
		return !allowedExtensions.contains(lastSegment.substring(dot + 1).toLowerCase());
	}
}
