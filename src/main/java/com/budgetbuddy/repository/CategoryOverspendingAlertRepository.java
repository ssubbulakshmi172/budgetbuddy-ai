package com.budgetbuddy.repository;

import com.budgetbuddy.model.CategoryOverspendingAlert;
import com.budgetbuddy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.YearMonth;
import java.util.List;

@Repository
public interface CategoryOverspendingAlertRepository extends JpaRepository<CategoryOverspendingAlert, Long> {
    List<CategoryOverspendingAlert> findByUserAndIsActiveTrue(User user);
    List<CategoryOverspendingAlert> findByUserAndMonth(User user, YearMonth month);
    List<CategoryOverspendingAlert> findByUserAndAlertLevel(User user, CategoryOverspendingAlert.AlertLevel alertLevel);
}

