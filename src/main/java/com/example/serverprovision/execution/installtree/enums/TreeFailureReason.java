package com.example.serverprovision.execution.installtree.enums;

/**
 * 설치 작업 트리를 확보하지 못한 사유. 문구는 서버 상세의 대기 사유(b-5)와 설치 원장 사유에 그대로 쓰인다.
 *
 * <p>{@link #immediate()} 가 참인 사유는 추출을 시도하기 전에 판정되며 ISO 상태를 바꾸지 않는다.
 * 거짓인 사유는 추출 단계의 실패로, 그 ISO 를 {@code FAILED} 로 만든다.</p>
 */
public enum TreeFailureReason {

    NOT_CONFIGURED(true, "설치 트리 디렉터리가 설정되지 않았습니다. 관리자에게 확인해야 합니다."),
    TOOL_MISSING(true, "설치 원본을 풀 도구(bsdtar)가 서버에 없습니다. 관리자에게 확인해야 합니다."),
    HTTP_ISO_UNSUPPORTED(true, "웹 주소로 등록된 ISO 는 설치 원본으로 쓸 수 없습니다."),
    ISO_UNAVAILABLE(true, "설치에 쓸 ISO 파일을 찾을 수 없습니다. 운영 관리에서 ISO 상태를 확인해야 합니다."),
    NOT_ENOUGH_SPACE(false, "설치 원본을 준비하지 못했습니다. 작업 디렉터리의 디스크 공간이 부족합니다."),
    NOT_RHEL_TREE(false, "설치 원본을 준비하지 못했습니다. RHEL 계열 설치 ISO 인지 확인해야 합니다."),
    EXTRACTION_FAILED(false, "설치 원본을 준비하지 못했습니다. 원인을 확인한 뒤 재시도해야 합니다.");

    private final boolean immediate;
    private final String message;

    TreeFailureReason(boolean immediate, String message) {
        this.immediate = immediate;
        this.message = message;
    }

    public boolean immediate() {
        return immediate;
    }

    public String message() {
        return message;
    }
}
