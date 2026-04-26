package com.example.serverprovision.management.common.filesystem.service;

import com.example.serverprovision.management.common.filesystem.dto.DirectoryBrowseRequest;
import com.example.serverprovision.management.common.filesystem.dto.DirectoryListingResponse;
import com.example.serverprovision.management.common.filesystem.exception.BrowseTargetNotDirectoryException;
import com.example.serverprovision.management.common.filesystem.exception.BrowseTargetNotFoundException;
import com.example.serverprovision.management.common.filesystem.exception.DirectoryBrowseIoException;
import com.example.serverprovision.management.common.filesystem.exception.InvalidBrowsePathException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * BIOS / BMC / ISO 폼의 서버 경로 탐색 공통 로직.
 */
@Service
public class DirectoryBrowseService {

    public DirectoryListingResponse browse(DirectoryBrowseRequest request) {
        String raw = (request.path() == null || request.path().isBlank()) ? "/" : request.path();
        Path target;
        try {
            target = Path.of(raw).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new InvalidBrowsePathException(raw);
        }
        if (!Files.exists(target)) {
            throw new BrowseTargetNotFoundException(target.toString());
        }
        if (!Files.isDirectory(target)) {
            throw new BrowseTargetNotDirectoryException(target.toString());
        }

        List<DirectoryListingResponse.Entry> entries = new ArrayList<>();
        try (Stream<Path> children = Files.list(target)) {
            children.sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        if (Files.isDirectory(p)) {
                            entries.add(DirectoryListingResponse.Entry.directory(name));
                        } else if (request.includeFiles()) {
                            long size = -1L;
                            try { size = Files.size(p); } catch (IOException ignore) { }
                            entries.add(DirectoryListingResponse.Entry.file(name, size));
                        }
                    });
        } catch (IOException e) {
            throw new DirectoryBrowseIoException("디렉토리 열람 중 오류 : " + e.getMessage(), e);
        }

        Path parent = target.getParent();
        return new DirectoryListingResponse(target.toString(), parent == null ? null : parent.toString(), entries);
    }
}
