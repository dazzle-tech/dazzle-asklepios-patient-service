package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DepartmentsRepository extends JpaRepository<Department, Long> {


}
