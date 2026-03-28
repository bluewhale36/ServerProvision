package com.example.serverprovision.domain.os.model.setting;

import com.example.serverprovision.domain.os.model.enums.OSName;

import java.util.List;

public class RockyLinuxSetting extends OSSetting {


    protected RockyLinuxSetting() {
        super(OSName.ROCKY_LINUX, List.of());
    }

    protected RockyLinuxSetting(List<String> compatibleOSVersion) {
        super(OSName.ROCKY_LINUX, compatibleOSVersion);
    }

}
