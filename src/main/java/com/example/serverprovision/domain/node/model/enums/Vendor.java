package com.example.serverprovision.domain.node.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum Vendor {
    GIGABYTE("Gigabyte"),
    ASUS("Asus");

    private final String vendorNameInternal;

    public static Vendor getVendorByString(String vendorName) {
        for (Vendor vendor : Vendor.values()) {
            if (vendor.getVendorNameInternal().equalsIgnoreCase(vendorName) || vendor.name().equalsIgnoreCase(vendorName)) {
                return vendor;
            }
        }
        return null;
    }
}
