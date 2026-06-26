package com.example.serverprovision.provisioning.exception;

import com.example.serverprovision.global.exception.NotFoundException;

import java.util.UUID;

/**
 * 지정 ID 의 게스트 서버가 존재하지 않을 때 던진다. (→ 404)
 */
public class GuestServerNotFoundException extends NotFoundException {

    public GuestServerNotFoundException(UUID id) {
        super("게스트 서버를 찾을 수 없습니다. id=" + id);
    }
}
