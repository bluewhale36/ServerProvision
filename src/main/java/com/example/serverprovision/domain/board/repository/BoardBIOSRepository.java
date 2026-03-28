package com.example.serverprovision.domain.board.repository;

import com.example.serverprovision.domain.board.entity.BoardBIOS;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BoardBIOSRepository extends JpaRepository<BoardBIOS, Long> {
}
