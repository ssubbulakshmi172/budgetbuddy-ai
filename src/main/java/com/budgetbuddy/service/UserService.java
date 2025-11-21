package com.budgetbuddy.service;

import com.budgetbuddy.model.User;
import com.budgetbuddy.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User saveUser(User user) {
        return userRepository.save(user);
    }

    public User getUserById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    public void deleteUserById(Long id) {
        userRepository.deleteById(id);
    }

    /**
     * Get current user (defaults to user ID 1 for single-user setup)
     * TODO: Implement proper authentication/authorization when multi-user support is added
     */
    public User getCurrentUser() {
        return userRepository.findById(1L).orElse(null);
    }
}
