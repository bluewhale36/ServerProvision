package com.example.serverprovision.domain.board.service;

import com.example.serverprovision.domain.board.dto.BoardBIOSDTO;
import com.example.serverprovision.domain.board.dto.BoardBMCDTO;
import com.example.serverprovision.domain.board.dto.BoardModelDTO;
import com.example.serverprovision.domain.board.entity.BoardBIOS;
import com.example.serverprovision.domain.board.entity.BoardBMC;
import com.example.serverprovision.domain.board.entity.BoardModel;
import com.example.serverprovision.domain.board.model.BoardOptionSelectView;
import com.example.serverprovision.domain.board.repository.BoardBIOSRepository;
import com.example.serverprovision.domain.board.repository.BoardBMCRepository;
import com.example.serverprovision.domain.board.repository.BoardModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoardModelService {

    private final BoardModelRepository boardModelRepository;
    private final BoardBIOSRepository boardBIOSRepository;
    private final BoardBMCRepository boardBMCRepository;

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

    public List<BoardOptionSelectView> getViewModelList() {
        List<BoardModelDTO> boardList = getAllBoardModel();
        List<BoardBIOSDTO> biosList = boardBIOSRepository.findAll().stream().map(BoardBIOSDTO::from).toList();
        List<BoardBMCDTO> bmcList = boardBMCRepository.findAll().stream().map(BoardBMCDTO::from).toList();

        List<BoardOptionSelectView> list = boardList.stream()
                .map(
                        board -> BoardOptionSelectView.of(board, biosList, bmcList)
                )
                .toList();
        list.forEach(System.out::println);

        return list;
    }
}
