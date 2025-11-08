package com.budgetbuddy.repository;

import com.budgetbuddy.model.SpendingPattern;
import com.budgetbuddy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpendingPatternRepository extends JpaRepository<SpendingPattern, Long> {
    List<SpendingPattern> findByUserAndIsActiveTrue(User user);
    List<SpendingPattern> findByUser(User user);
    List<SpendingPattern> findByUserAndPatternType(User user, SpendingPattern.PatternType patternType);
}

