package com.example.serverprovision.global.lifecycle.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * MK2 — Deprecate / Undeprecate 전이 위반.
 *
 * <ul>
 *   <li>이미 Deprecated 인 자원에 deprecate() 호출</li>
 *   <li>Active 자원에 undeprecate() 호출</li>
 *   <li>SoftDeleted 자원에 deprecate / undeprecate 호출</li>
 * </ul>
 */
public class IllegalDeprecationStateException extends ConflictException {
    public IllegalDeprecationStateException(String message) {
        super(message);
    }
}
