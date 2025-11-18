package com.budgetbuddy.repository;

import com.budgetbuddy.model.SavingsProjection;
import com.budgetbuddy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavingsProjectionRepository extends JpaRepository<SavingsProjection, Long> {
    Optional<SavingsProjection> findFirstByUserOrderByProjectionDateDesc(User user);
    List<SavingsProjection> findByUserAndYearOrderByProjectionDateDesc(User user, Integer year);
}

