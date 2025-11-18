package com.budgetbuddy.repository;

import com.budgetbuddy.model.CategoryKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryKeywordRepository extends JpaRepository<CategoryKeyword, Long> {

    List<CategoryKeyword> findByCategoryName(String categoryName);

    boolean existsByKeyword(String keyword);

    Optional<CategoryKeyword> findByKeyword(String keyword);

    @Query("SELECT DISTINCT ck.categoryName FROM CategoryKeyword ck WHERE ck.categoryName IS NOT NULL")
    List<String> findDistinctCategoryNames();
    
    List<CategoryKeyword> findByCategoriesFor(String categoriesFor);
}