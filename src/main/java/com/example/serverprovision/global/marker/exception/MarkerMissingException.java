package com.example.serverprovision.global.marker.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * DB 레코드는 존재하나 자원 위치의 {@code .provision.json} 마커 파일이 유실된 상태.
 * 관리자에게 "marker 재발급" 또는 자원 재등록을 안내. → 409
 */
public class MarkerMissingException extends ConflictException {

    public MarkerMissingException(String path) {
        super("marker 파일 (.provision.json) 이 없습니다. 재발급이 필요합니다 : " + path);
    }
}
