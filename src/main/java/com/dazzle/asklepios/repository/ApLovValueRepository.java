package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ApLovValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApLovValueRepository extends JpaRepository<ApLovValue, String> {
    List<ApLovValue> findByKeyIn(List<String> keys);
    List<ApLovValue> findByLovKey(String lovKey);

    List<ApLovValue> findByLovCode(String lovCode);

    List<ApLovValue> findByLovKeyAndIsValidTrue(String lovKey);

    List<ApLovValue> findByLovCodeAndIsValidTrue(String lovCode);

    List<ApLovValue> findByParentValueId(String parentValueId);

    List<ApLovValue> findByForInternalUser(Boolean forInternalUser);
}