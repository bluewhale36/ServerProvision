package com.example.serverprovision.domain.board.repository;

import com.example.serverprovision.domain.board.entity.BoardBMC;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BoardBMCRepository extends JpaRepository<BoardBMC, Long> {
}
