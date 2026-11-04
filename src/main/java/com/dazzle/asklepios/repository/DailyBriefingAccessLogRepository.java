package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DailyBriefingAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyBriefingAccessLogRepository extends JpaRepository<DailyBriefingAccessLog, Long> {
}
