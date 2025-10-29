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

    public User getUserByName(String userName) {
        return userRepository.findByName(userName)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userName));
    }
}
