package com.swami.skillquiz.repo;

import com.swami.skillquiz.model.AiUsage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiUsageRepository extends JpaRepository<AiUsage, String> {
}
