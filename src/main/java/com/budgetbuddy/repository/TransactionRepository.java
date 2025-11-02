package com.budgetbuddy.repository;

import com.budgetbuddy.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {




    // Query to find transactions by month, year, and user
    @Query("SELECT t FROM Transaction t WHERE MONTH(t.date) = :month AND YEAR(t.date) = :year AND t.user.id = :userId")
    List<Transaction> findTransactionsByMonthYearAndUser(@Param("month") int month,
                                                         @Param("year") int year,
                                                         @Param("userId") Long userId);

    // Query to delete transactions by month, year, and user
    @Modifying
    @Transactional
    @Query("DELETE FROM Transaction t WHERE MONTH(t.date) = :month AND YEAR(t.date) = :year AND t.user.id = :userId")
    int deleteTransactionsByMonthYearAndUser(@Param("month") int month,
                                             @Param("year") int year,
                                             @Param("userId") Long userId);

    // Find duplicate transactions
    @Query("SELECT t FROM Transaction t WHERE EXISTS (SELECT 1 FROM Transaction t2 WHERE t.id != t2.id AND t.date = t2.date AND t.narration = t2.narration AND t.user.id = t2.user.id)")
    List<Transaction> findDuplicateTransactions();


    List<Transaction> findByCategoryNameIsNull();
    
    // Sort methods for date
    List<Transaction> findAllByOrderByDateAsc();
    List<Transaction> findAllByOrderByDateDesc();

    @Query("SELECT t FROM Transaction t WHERE " +
            "(:month = -1 OR FUNCTION('MONTH', t.date) = :month) " +  // Month filter
            "AND (:year = -1 OR FUNCTION('YEAR', t.date) = :year) " +  // Year filter
            "AND (:userId IS NULL OR t.user.id = :userId) " +  // Optional userId filter
            "AND ((:categoryKeyword IS NULL OR t.categoryName = :categoryKeyword) " +
            "OR (:categoryKeyword = 'EMPTY' AND (t.categoryName IS NULL OR t.categoryName = ''))) " +
            "AND (:amountValue IS NULL OR :amountOperator IS NULL OR " +  // Amount filter
            "(:amountOperator = 'gt' AND t.amount > :amountValue) " +
            "OR (:amountOperator = 'lt' AND t.amount < :amountValue) " +
            "OR (:amountOperator = 'gte' AND t.amount >= :amountValue) " +
            "OR (:amountOperator = 'lte' AND t.amount <= :amountValue) " +
            "OR (:amountOperator = 'eq' AND t.amount = :amountValue)) " +
            "AND (:narration IS NULL OR LOWER(t.narration) LIKE LOWER(CONCAT('%', :narration, '%')))")  // Narration filter (case-insensitive)
    List<Transaction> filterTransactions(@Param("month") int month,
                                         @Param("year") Integer year,
                                         @Param("userId") Long userId,
                                         @Param("categoryKeyword") String categoryKeyword,
                                         @Param("amountValue") Double amountValue,
                                         @Param("amountOperator") String amountOperator,
                                         @Param("narration") String narration);



}
