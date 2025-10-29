package com.budgetbuddy.repository;

import com.budgetbuddy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // Additional query methods (if needed)

    Optional<User> findByName(String name);
    Optional<User> findById(Long id);

}
