package com.budgetbuddy.repository;

import com.budgetbuddy.model.SpendingPrediction;
import com.budgetbuddy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SpendingPredictionRepository extends JpaRepository<SpendingPrediction, Long> {
    List<SpendingPrediction> findByUser(User user);
    List<SpendingPrediction> findByUserAndIsOverspendingRiskTrue(User user);
    List<SpendingPrediction> findByUserAndForecastStartDateGreaterThanEqual(User user, LocalDate date);
    List<SpendingPrediction> findByUserAndForecastStartDateBetween(User user, LocalDate start, LocalDate end);
}

