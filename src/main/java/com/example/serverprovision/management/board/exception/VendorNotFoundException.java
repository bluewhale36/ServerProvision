package com.example.serverprovision.management.board.exception;

import com.example.serverprovision.global.exception.NotFoundException;

public class VendorNotFoundException extends NotFoundException {
    public VendorNotFoundException(String expectedVendorString) {
        super("제조사를 찾을 수 없습니다. expectedVendorString=" + expectedVendorString);
    }
}
