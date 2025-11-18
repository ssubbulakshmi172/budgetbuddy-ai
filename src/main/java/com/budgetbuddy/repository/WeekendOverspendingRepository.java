package com.budgetbuddy.repository;

import com.budgetbuddy.model.WeekendOverspending;
import com.budgetbuddy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.YearMonth;
import java.util.List;

@Repository
public interface WeekendOverspendingRepository extends JpaRepository<WeekendOverspending, Long> {
    List<WeekendOverspending> findByUserAndIsActiveTrue(User user);
    List<WeekendOverspending> findByUserAndMonth(User user, YearMonth month);
    List<WeekendOverspending> findByUserAndCategory(User user, String category);
}

