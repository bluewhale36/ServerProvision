package com.example.serverprovision.management.os.util;

import java.util.function.Function;

/**
 * 사용자 입력 ISO 경로를 실제 저장 경로로 해석한다.
 * 경로가 {@code /} 로 끝나면 디렉토리로 간주하고 그 뒤에 업로드 파일명을 append 한다.
 *
 * <p>호출측이 "파일명 누락" 상황을 반드시 의식하게 하기 위해 예외 팩토리를 인자로 받는다 —
 * unchecked 예외로만 던지면 호출측에서 catch 를 잊어 500 으로 새어 나가는 사고가 났던 적이 있다
 * (IsoPathResolver.MissingFilenameException → 기본 핸들러 → 500).
 * 이제는 호출측이 자신의 도메인 예외를 명시적으로 지정해야 컴파일이 통과한다.</p>
 */
public final class IsoPathResolver {

    private IsoPathResolver() {}

    /**
     * @param rawPath           사용자가 입력한 ISO 경로 (예: "/opt/iso/rocky/9-7/" 또는 "/opt/iso/rocky/9-7/dvd.iso")
     * @param filename          업로드된 파일의 원본 파일명 (예: "Rocky-9.7-x86_64-dvd.iso"). null 허용.
     * @param onMissingFilename 디렉토리 경로인데 filename 이 없을 때 던질 예외 팩토리.
     *                          인자는 문제가 된 {@code rawPath}. 호출측 도메인 예외(409) 로 변환하는 것을 권장.
     * @return 디렉토리 경로이면 {@code rawPath + filename}, 아니면 {@code rawPath} 원본.
     */
    public static String resolve(String rawPath, String filename,
                                 Function<String, ? extends RuntimeException> onMissingFilename) {
        if (rawPath == null || rawPath.isBlank()) return rawPath;
        if (!rawPath.endsWith("/")) return rawPath;
        if (filename == null || filename.isBlank()) {
            throw onMissingFilename.apply(rawPath);
        }
        return rawPath + filename;
    }
}
