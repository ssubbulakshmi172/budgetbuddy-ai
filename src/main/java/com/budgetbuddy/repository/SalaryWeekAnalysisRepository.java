package com.budgetbuddy.repository;

import com.budgetbuddy.model.SalaryWeekAnalysis;
import com.budgetbuddy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.YearMonth;
import java.util.List;

@Repository
public interface SalaryWeekAnalysisRepository extends JpaRepository<SalaryWeekAnalysis, Long> {
    List<SalaryWeekAnalysis> findByUserOrderByMonthDesc(User user);
    List<SalaryWeekAnalysis> findByUserAndIsAnomalyTrue(User user);
    List<SalaryWeekAnalysis> findByUserAndMonth(User user, YearMonth month);
}

