package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DocumentDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DocumentDefinitionRepository extends JpaRepository<DocumentDefinition, Long> , JpaSpecificationExecutor<DocumentDefinition> {

    boolean existsByCode(String code);
}
