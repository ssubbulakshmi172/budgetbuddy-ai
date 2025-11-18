package com.budgetbuddy.repository;

import com.budgetbuddy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    // JpaRepository already provides findById, so no need to redeclare it
}
