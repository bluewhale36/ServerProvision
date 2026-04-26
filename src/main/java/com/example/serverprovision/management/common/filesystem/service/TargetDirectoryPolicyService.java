package com.example.serverprovision.management.common.filesystem.service;

import java.nio.file.Path;

/**
 * BIOS/BMC 번들 업로드 전 target directory 정책 계약.
 * 본체 구현과 feature wiring 은 MA4-1-3 CP4 에서 마무리한다.
 */
public interface TargetDirectoryPolicyService {

    void validateForIntent(Path targetDirectory, boolean allowCreateDirectory);

    void prepareForUpload(Path targetDirectory, boolean allowCreateDirectory);
}
