package com.budgetbuddy.controller;

import com.budgetbuddy.model.User;
import com.budgetbuddy.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    // Display all users
    @GetMapping
    public String listUsers(Model model) {
        model.addAttribute("users", userService.getAllUsers());
        return "user-list";
    }

    // Show the form to create a new user
    @GetMapping("/new")
    public String createUserForm(Model model) {
        model.addAttribute("user", new User());
        return "user-form";
    }

    // Save a new user
    @PostMapping
    public String saveUser(@ModelAttribute("user") User user) {
        userService.saveUser(user);
        return "redirect:/users";
    }

    // Edit an existing user
    @GetMapping("/edit/{id}")
    public String editUserForm(@PathVariable Long id, Model model) {
        model.addAttribute("user", userService.getUserById(id));
        return "user-form";
    }

    // Delete a user
    @GetMapping("/delete/{id}")
    public String deleteUser(@PathVariable Long id) {
        userService.deleteUserById(id);
        return "redirect:/users";
    }
}

