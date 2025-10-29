package com.budgetbuddy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication

public class BudgetbuddyApplication {

	public static void main(String[] args) {
		SpringApplication.run(BudgetbuddyApplication.class, args);
	}

}
