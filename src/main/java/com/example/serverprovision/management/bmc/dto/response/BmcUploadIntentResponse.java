package com.example.serverprovision.management.bmc.dto.response;

import java.util.List;

public record BmcUploadIntentResponse(
        String uploadToken,
        List<String> warnings
) {
}
