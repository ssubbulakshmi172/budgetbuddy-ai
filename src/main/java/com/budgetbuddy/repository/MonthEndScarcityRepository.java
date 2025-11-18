package com.budgetbuddy.repository;

import com.budgetbuddy.model.MonthEndScarcity;
import com.budgetbuddy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.YearMonth;
import java.util.List;

@Repository
public interface MonthEndScarcityRepository extends JpaRepository<MonthEndScarcity, Long> {
    List<MonthEndScarcity> findByUserOrderByMonthDesc(User user);
    List<MonthEndScarcity> findByUserAndBehaviorType(User user, MonthEndScarcity.BehaviorType behaviorType);
    List<MonthEndScarcity> findByUserAndMonth(User user, YearMonth month);
}

