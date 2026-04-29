package com.example.serverprovision.management.bmc.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * BMC 의 현재 상태와 요청이 맞지 않을 때 던진다.
 */
public class IllegalBmcStateException extends ConflictException {

    public IllegalBmcStateException(String message) {
        super(message);
    }
}
