package com.budgetbuddy.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Simple test to generate PDF locally and save it for viewing
 * Run this as: ./gradlew test --tests GeneratePdfLocally
 */
public class GeneratePdfLocally {
    
    public static void main(String[] args) {
        System.out.println("=== Generating PDF Locally ===\n");
        
        try {
            SubmissionDocumentController controller = new SubmissionDocumentController();
            
            System.out.println("1. Generating PDF...");
            var response = controller.downloadSubmissionDocument();
            
            if (response.getStatusCode().value() != 200) {
                System.err.println("✗ Failed to generate PDF. Status: " + response.getStatusCode());
                return;
            }
            
            System.out.println("2. Extracting PDF bytes...");
            var body = response.getBody();
            if (body == null) {
                System.err.println("✗ Response body is null");
                return;
            }
            
            byte[] pdfBytes = body.getInputStream().readAllBytes();
            System.out.println("   ✓ PDF size: " + pdfBytes.length + " bytes");
            
            System.out.println("3. Saving PDF to Desktop...");
            String userHome = System.getProperty("user.home");
            Path desktopPath = Paths.get(userHome, "Desktop", "BudgetBuddy_Submission_Document.pdf");
            
            Files.write(desktopPath, pdfBytes);
            System.out.println("   ✓ PDF saved to: " + desktopPath);
            System.out.println("\n=== PDF Generated Successfully ===");
            System.out.println("Open the file to verify:");
            System.out.println("  → " + desktopPath);
            System.out.println("\nCheck that:");
            System.out.println("  1. The PDF opens correctly");
            System.out.println("  2. The architecture diagram is visible");
            System.out.println("  3. The image is in COLOR (not black and white)");
            System.out.println("  4. The image appears in the 'System Architecture' section");
            
        } catch (Exception e) {
            System.err.println("\n✗ Error generating PDF!");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

