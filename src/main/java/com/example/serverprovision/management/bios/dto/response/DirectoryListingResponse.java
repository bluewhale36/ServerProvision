package com.example.serverprovision.management.bios.dto.response;

import java.util.List;

/**
 * 서버 파일시스템 경로 탐색 응답. 번들 업로드 대상 디렉토리를 마우스로 선택하게 돕는 보조 API 전용.
 * <ul>
 *   <li>{@code path} — 지금 열람 중인 절대경로 (정규화된 형태)</li>
 *   <li>{@code parent} — 상위 경로 문자열. 루트({@code /}) 이면 null</li>
 *   <li>{@code entries} — 이 경로의 항목들. {@link Entry#type} 으로 디렉토리/파일 구분</li>
 * </ul>
 * <p>BIOS / ISO 가 공통 사용. 향후 BMC/Driver 도 동일 형식 재사용 가능.</p>
 */
public record DirectoryListingResponse(
        String path,
        String parent,
        List<Entry> entries
) {
    /**
     * 디렉토리 항목 1 개.
     * @param name 짧은 이름 (마지막 path 컴포넌트)
     * @param type {@code "DIR"} 또는 {@code "FILE"}
     * @param size 파일 크기 (bytes). 디렉토리는 null
     */
    public record Entry(String name, String type, Long size) {

        /** 디렉토리 항목 헬퍼. */
        public static Entry directory(String name) {
            return new Entry(name, "DIR", null);
        }

        /** 파일 항목 헬퍼. */
        public static Entry file(String name, long size) {
            return new Entry(name, "FILE", size);
        }
    }
}
