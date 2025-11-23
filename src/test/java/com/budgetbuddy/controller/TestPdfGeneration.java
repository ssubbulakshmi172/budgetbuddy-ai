package com.budgetbuddy.controller;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Standalone test runner for PDF generation
 * Run this as a Java application to test PDF generation locally
 */
public class TestPdfGeneration {
    
    public static void main(String[] args) {
        System.out.println("=== Testing PDF Generation Locally ===\n");
        
        try {
            SubmissionDocumentController controller = new SubmissionDocumentController();
            
            System.out.println("1. Calling downloadSubmissionDocument()...");
            var response = controller.downloadSubmissionDocument();
            
            System.out.println("2. Checking response...");
            if (response.getStatusCode().value() == 200) {
                System.out.println("   ✓ Response status: 200 OK");
            } else {
                System.out.println("   ✗ Response status: " + response.getStatusCode());
                return;
            }
            
            System.out.println("3. Extracting PDF bytes...");
            var body = response.getBody();
            if (body == null) {
                System.out.println("   ✗ Response body is null");
                return;
            }
            
            byte[] pdfBytes = body.getInputStream().readAllBytes();
            System.out.println("   ✓ PDF size: " + pdfBytes.length + " bytes");
            
            System.out.println("4. Validating PDF format...");
            String pdfHeader = new String(pdfBytes, 0, Math.min(4, pdfBytes.length));
            if (!pdfHeader.equals("%PDF")) {
                System.out.println("   ✗ Invalid PDF header: " + pdfHeader);
                return;
            }
            System.out.println("   ✓ Valid PDF header");
            
            System.out.println("5. Parsing PDF document...");
            try (PDDocument document = PDDocument.load(pdfBytes)) {
                int pageCount = document.getNumberOfPages();
                System.out.println("   ✓ PDF has " + pageCount + " page(s)");
                
                System.out.println("6. Checking for embedded images...");
                boolean hasImages = false;
                for (int i = 0; i < pageCount; i++) {
                    PDPage page = document.getPage(i);
                    if (page.getResources() != null && page.getResources().getXObjectNames() != null) {
                        for (var name : page.getResources().getXObjectNames()) {
                            var xObject = page.getResources().getXObject(name);
                            if (xObject instanceof PDImageXObject) {
                                hasImages = true;
                                PDImageXObject image = (PDImageXObject) xObject;
                                System.out.println("   ✓ Found image on page " + (i + 1));
                                System.out.println("     - Width: " + image.getWidth() + "px");
                                System.out.println("     - Height: " + image.getHeight() + "px");
                                System.out.println("     - Color space: " + 
                                    (image.getColorSpace() != null ? image.getColorSpace().getName() : "unknown"));
                                break;
                            }
                        }
                    }
                    if (hasImages) break;
                }
                
                if (!hasImages) {
                    System.out.println("   ⚠ Warning: No images found in PDF");
                }
            }
            
            System.out.println("7. Saving PDF for inspection...");
            String userHome = System.getProperty("user.home");
            Path desktopPath = Paths.get(userHome, "Desktop", "BudgetBuddy_Test_Submission_Document.pdf");
            
            try {
                Files.write(desktopPath, pdfBytes);
                System.out.println("   ✓ PDF saved to: " + desktopPath);
                System.out.println("   → Open this file to verify the image is in color");
            } catch (Exception e) {
                // Try current directory
                Path fallbackPath = Paths.get("test-submission-document.pdf");
                Files.write(fallbackPath, pdfBytes);
                System.out.println("   ✓ PDF saved to: " + fallbackPath.toAbsolutePath());
            }
            
            System.out.println("\n=== Test Completed Successfully ===");
            System.out.println("Please open the generated PDF file to verify:");
            System.out.println("  1. The PDF opens correctly");
            System.out.println("  2. The architecture diagram is visible");
            System.out.println("  3. The image is in COLOR (not black and white)");
            
        } catch (Exception e) {
            System.err.println("\n✗ Test Failed!");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

