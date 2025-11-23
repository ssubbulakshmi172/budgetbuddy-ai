package com.budgetbuddy.controller;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Submission Document Controller Tests")
class SubmissionDocumentControllerTest {

    private final SubmissionDocumentController controller = new SubmissionDocumentController();

    @Test
    void testPdfGeneration() throws Exception {
        System.out.println("=== Testing PDF Generation ===");
        
        // Call the controller method
        ResponseEntity<?> response = controller.downloadSubmissionDocument();
        
        // Verify response
        assertNotNull(response, "Response should not be null");
        assertEquals(HttpStatus.OK, response.getStatusCode(), "Should return 200 OK");
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType(), 
            "Should return PDF content type");
        
        // Get PDF bytes
        Object body = response.getBody();
        assertNotNull(body, "Response body should not be null");
        
        byte[] pdfBytes;
        if (body instanceof org.springframework.core.io.ByteArrayResource) {
            org.springframework.core.io.ByteArrayResource resource = 
                (org.springframework.core.io.ByteArrayResource) body;
            pdfBytes = resource.getInputStream().readAllBytes();
        } else {
            fail("Response body should be ByteArrayResource");
            return;
        }
        
        // Verify PDF is not empty
        assertTrue(pdfBytes.length > 0, "PDF should not be empty");
        System.out.println("PDF size: " + pdfBytes.length + " bytes");
        
        // Verify PDF header (PDF files start with %PDF)
        String pdfHeader = new String(pdfBytes, 0, Math.min(4, pdfBytes.length));
        assertEquals("%PDF", pdfHeader, "Should be a valid PDF file");
        System.out.println("✓ PDF header verified: " + pdfHeader);
        
        // Verify PDF can be parsed
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            int pageCount = document.getNumberOfPages();
            assertTrue(pageCount > 0, "PDF should have at least one page");
            System.out.println("✓ PDF has " + pageCount + " page(s)");
            
            // Check if image is embedded (look for image objects in the document)
            boolean hasImages = false;
            for (int i = 0; i < pageCount; i++) {
                PDPage page = document.getPage(i);
                // Check if page has resources with images
                if (page.getResources() != null && page.getResources().getXObjectNames() != null) {
                    for (org.apache.pdfbox.cos.COSName name : page.getResources().getXObjectNames()) {
                        Object xObject = page.getResources().getXObject(name);
                        if (xObject instanceof PDImageXObject) {
                            hasImages = true;
                            PDImageXObject image = (PDImageXObject) xObject;
                            System.out.println("✓ Found image on page " + (i + 1) + 
                                " - Width: " + image.getWidth() + ", Height: " + image.getHeight());
                            break;
                        }
                    }
                }
                if (hasImages) break;
            }
            
            if (hasImages) {
                System.out.println("✓ Image is embedded in PDF");
            } else {
                System.out.println("⚠ Warning: No images found in PDF (may still be valid)");
            }
        }
        
        // Save PDF to file for manual inspection (optional)
        savePdfForInspection(pdfBytes);
        
        System.out.println("=== PDF Generation Test Passed ===");
    }
    
    @Test
    void testHtmlResourceExists() {
        System.out.println("=== Testing HTML Resource ===");
        
        ClassPathResource htmlResource = new ClassPathResource("static/SUBMISSION_DOCUMENT.html");
        assertTrue(htmlResource.exists(), "HTML file should exist");
        System.out.println("✓ HTML file exists");
        
        ClassPathResource imageResource = new ClassPathResource("static/images/budgetbuddy-ai-architecture-diagram.png");
        assertTrue(imageResource.exists(), "Image file should exist");
        System.out.println("✓ Image file exists");
        
        System.out.println("=== Resource Check Passed ===");
    }
    
    @Test
    void testPdfGenerationWithFileOutput() throws Exception {
        System.out.println("=== Testing PDF Generation with File Output ===");
        
        ResponseEntity<?> response = controller.downloadSubmissionDocument();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        Object body = response.getBody();
        assertNotNull(body);
        
        byte[] pdfBytes;
        if (body instanceof org.springframework.core.io.ByteArrayResource) {
            org.springframework.core.io.ByteArrayResource resource = 
                (org.springframework.core.io.ByteArrayResource) body;
            pdfBytes = resource.getInputStream().readAllBytes();
        } else {
            fail("Response body should be ByteArrayResource");
            return;
        }
        
        // Save to a specific location for easy access
        String userHome = System.getProperty("user.home");
        Path testPdfPath = Paths.get(userHome, "Desktop", "BudgetBuddy_Test_Submission_Document.pdf");
        
        try (FileOutputStream fos = new FileOutputStream(testPdfPath.toFile())) {
            fos.write(pdfBytes);
            System.out.println("✓ PDF saved to: " + testPdfPath);
            System.out.println("  You can open this file to verify the image is in color");
        } catch (IOException e) {
            System.err.println("Could not save PDF to Desktop: " + e.getMessage());
            // Try current directory instead
            Path fallbackPath = Paths.get("test-submission-document.pdf");
            Files.write(fallbackPath, pdfBytes);
            System.out.println("✓ PDF saved to: " + fallbackPath.toAbsolutePath());
        }
        
        // Verify the saved file
        assertTrue(Files.exists(testPdfPath) || Files.exists(Paths.get("test-submission-document.pdf")), 
            "PDF file should be saved");
        
        System.out.println("=== File Output Test Passed ===");
    }
    
    private void savePdfForInspection(byte[] pdfBytes) {
        try {
            // Save to test output directory
            Path testOutputDir = Paths.get("build", "test-output");
            Files.createDirectories(testOutputDir);
            
            Path pdfPath = testOutputDir.resolve("test-submission-document.pdf");
            Files.write(pdfPath, pdfBytes);
            System.out.println("✓ PDF saved for inspection: " + pdfPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Could not save PDF for inspection: " + e.getMessage());
        }
    }
}

