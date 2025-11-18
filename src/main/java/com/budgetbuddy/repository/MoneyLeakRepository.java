package com.budgetbuddy.repository;

import com.budgetbuddy.model.MoneyLeak;
import com.budgetbuddy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MoneyLeakRepository extends JpaRepository<MoneyLeak, Long> {
    List<MoneyLeak> findByUserAndIsActiveTrueOrderByAnnualAmountDesc(User user);
    List<MoneyLeak> findByUserAndLeakType(User user, MoneyLeak.LeakType leakType);
    @Query("SELECT m FROM MoneyLeak m WHERE m.user = :user AND m.rank IS NOT NULL ORDER BY m.rank ASC")
    List<MoneyLeak> findByUserAndRankIsNotNullOrderByRankAsc(User user);
}

