package com.example.serverprovision.domain.board.service;

import com.example.serverprovision.domain.board.dto.BoardModelDTO;
import com.example.serverprovision.domain.board.repository.BoardModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoardModelService {

    private final BoardModelRepository boardModelRepository;

    public List<BoardModelDTO> getAllBoardModel() {
        return boardModelRepository.findAll()
                .stream()
                .map(BoardModelDTO::from)
                .toList();
    }

    public List<BoardModelDTO> getAllActiveBoardModel() {
        return boardModelRepository.findAllByEnabledIsTrue()
                .stream()
                .map(BoardModelDTO::from)
                .toList();
    }
}
