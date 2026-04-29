package com.example.serverprovision.management.common.filesystem.exception;

import com.example.serverprovision.global.exception.NotFoundException;

public class BrowseTargetNotFoundException extends NotFoundException {
    public BrowseTargetNotFoundException(String path) {
        super("경로를 찾을 수 없습니다 : " + path);
    }
}
