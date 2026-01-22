package com.dazzle.asklepios.repository;
import com.dazzle.asklepios.domain.ExternalTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
public interface ExternalTestRepository extends JpaRepository<ExternalTest, Long> {
    Optional<ExternalTest> findByTestId(Long testId);
    boolean existsByTestId(Long testId);
    void deleteByTestId(Long testId);
    @Query("select e.testId from ExternalTest e where e.testId in ?1")
    List<Long> findExistingTestIds(Collection<Long> ids);

}
