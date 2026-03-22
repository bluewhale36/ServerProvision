package com.example.serverprovision.domain.os.service;

import com.example.serverprovision.domain.os.dto.OSMetadataCreateDTO;
import com.example.serverprovision.domain.os.dto.OSMetadataDTO;
import com.example.serverprovision.domain.os.dto.OSMetadataUpdateDTO;
import com.example.serverprovision.domain.os.entity.OSMetadata;
import com.example.serverprovision.domain.os.repository.OSMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OSMetadataService {

    private final OSMetadataRepository osMetadataRepository;

    public List<OSMetadataDTO> getAllOSMetadata() {
        return osMetadataRepository.findAll().stream().map(OSMetadataDTO::from).toList();
    }

    public List<OSMetadataDTO> getAllActiveOSMetadata() {
        return osMetadataRepository.findAllByEnabledIsTrue().stream().map(OSMetadataDTO::from).toList();
    }

    public OSMetadataDTO getOSMetadataById(Long id) {
        OSMetadata osMetadata = osMetadataRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 OS 정보입니다. ID: " + id));
        return OSMetadataDTO.from(osMetadata);
    }

    @Transactional
    public void saveOSMetadata(OSMetadataCreateDTO createDTO) {
        osMetadataRepository.save(OSMetadata.createFrom(createDTO));
    }

    @Transactional
    public void saveOSMetadata(OSMetadataUpdateDTO updateDTO) {
        osMetadataRepository.save(OSMetadata.updateFrom(updateDTO));
    }

    @Transactional
    public void toggleActive(Long id) {
        OSMetadata osMetadata = osMetadataRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 OS 정보입니다. ID: " + id));
        osMetadata.toggleEnabled();
    }
}
