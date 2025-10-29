package com.budgetbuddy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ReportViewController {

    @GetMapping("/reports")
    public String showReportsPage() {
        return "reports/reports_view"; // located in templates/reports/reports_view.html
    }
}
