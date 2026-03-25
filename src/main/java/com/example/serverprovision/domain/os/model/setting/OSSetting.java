package com.example.serverprovision.domain.os.model.setting;

import com.example.serverprovision.domain.os.model.enums.OSName;
import com.example.serverprovision.domain.os.model.OSTemplate;

import java.util.List;

public abstract class OSSetting extends OSTemplate {

    protected OSSetting(OSName compatibleOS, List<String> compatibleOSVersion) {
        super(compatibleOS, compatibleOSVersion);
    }
}
