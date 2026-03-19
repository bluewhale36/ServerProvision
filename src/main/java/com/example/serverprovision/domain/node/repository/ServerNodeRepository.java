package com.example.serverprovision.domain.node.repository;

import com.example.serverprovision.domain.node.entity.ServerNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServerNodeRepository extends JpaRepository<ServerNode, String> {
}
