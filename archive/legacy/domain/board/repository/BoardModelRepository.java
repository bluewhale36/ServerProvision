package com.example.serverprovision.domain.board.repository;

import com.example.serverprovision.domain.board.entity.BoardModel;
import com.example.serverprovision.domain.node.model.enums.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BoardModelRepository extends JpaRepository<BoardModel, Long> {

    Optional<BoardModel> findByVendorAndModelName(Vendor vendor, String modelName);

    List<BoardModel> findAllByEnabledIsTrue();
}
