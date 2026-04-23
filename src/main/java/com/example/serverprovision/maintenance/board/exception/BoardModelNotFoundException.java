package com.example.serverprovision.maintenance.board.exception;

import com.example.serverprovision.global.exception.NotFoundException;

/**
 * 지정 ID 의 메인보드 모델이 존재하지 않을 때 던진다.
 * soft 삭제된 레코드에 대해 삭제/토글/수정을 시도하는 경우에도 NotFound 로 취급한다.
 */
public class BoardModelNotFoundException extends NotFoundException {

    public BoardModelNotFoundException(Long id) {
        super("메인보드 모델을 찾을 수 없습니다. id=" + id);
    }
}
