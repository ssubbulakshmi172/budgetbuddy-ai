package com.budgetbuddy.repository;

import com.budgetbuddy.model.FinancialNudge;
import com.budgetbuddy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FinancialNudgeRepository extends JpaRepository<FinancialNudge, Long> {
    List<FinancialNudge> findByUser(User user);
    List<FinancialNudge> findByUserAndIsDismissedFalse(User user);
    List<FinancialNudge> findByUserAndIsDismissedFalseAndIsReadFalse(User user);
    List<FinancialNudge> findByUserAndExpiresAtGreaterThanEqual(User user, LocalDate date);
    List<FinancialNudge> findByUserAndIsDismissedFalseAndExpiresAtGreaterThanEqual(User user, LocalDate date);
}

