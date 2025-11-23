package com.budgetbuddy.controller;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Controller
public class SubmissionDocumentController {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionDocumentController.class);

    @GetMapping("/submission-document.pdf")
    public ResponseEntity<Resource> downloadSubmissionDocument() {
        try {
            // Step 1: Read HTML content
            ClassPathResource htmlResource = new ClassPathResource("static/SUBMISSION_DOCUMENT.html");
            if (!htmlResource.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            InputStream htmlInputStream = htmlResource.getInputStream();
            String htmlContent = new String(htmlInputStream.readAllBytes(), StandardCharsets.UTF_8);
            htmlInputStream.close();
            
            // Step 2: Replace image with placeholder div (we'll add image directly with PDFBox)
            // Match both img tag and the placeholder div pattern
            htmlContent = htmlContent.replaceAll(
                "(<img[^>]*src=[\"']images/budgetbuddy-ai-architecture-diagram\\.png[\"'][^>]*/>)|(<div[^>]*id=[\"']architecture-diagram-placeholder[\"'][^>]*>.*?</div>)",
                "<div id=\"architecture-diagram-placeholder\" style=\"height: 500px; border: 2px solid #66B2FF; border-radius: 4px; background: white; padding: 15px; margin: 0 auto; max-width: 100%; page-break-inside: avoid;\"></div>"
            );
            
            // Step 3: Generate PDF from HTML using OpenHTMLToPDF
            ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            
            // Set base URI for resolving resources
            String baseUri;
            try {
                java.io.File htmlFile = htmlResource.getFile();
                baseUri = htmlFile.getParentFile().toURI().toString();
            } catch (Exception e) {
                baseUri = "classpath:/static/";
            }
            
            builder.withHtmlContent(htmlContent, baseUri);
            builder.toStream(pdfOutputStream);
            builder.run();
            
            byte[] pdfBytes = pdfOutputStream.toByteArray();
            
            // Step 4: Load the generated PDF and add image directly using PDFBox (preserves colors)
            PDDocument document = PDDocument.load(pdfBytes);
            
            // Load the image file
            ClassPathResource imageResource = new ClassPathResource("static/images/budgetbuddy-ai-architecture-diagram.png");
            if (!imageResource.exists()) {
                document.close();
                return ResponseEntity.notFound().build();
            }
            
            // Create PDImageXObject from file (preserves color)
            PDImageXObject pdImage = PDImageXObject.createFromFile(
                imageResource.getFile().getAbsolutePath(), document);
            
            // Find the page where we want to insert the image
            // The placeholder div has height 500px, so it should be on page 2 or 3
            // We'll search for the page that likely contains "System Architecture" section
            int targetPageIndex = 1; // Default to page 2 (index 1)
            
            // Ensure we don't go out of bounds
            if (targetPageIndex >= document.getNumberOfPages()) {
                targetPageIndex = document.getNumberOfPages() - 1;
            }
            
            PDPage targetPage = document.getPage(targetPageIndex);
            PDRectangle pageSize = targetPage.getMediaBox();
            
            // Calculate image dimensions to fit page width with proper margins
            float margin = 72f; // 1 inch margin
            float headerSpace = 120f; // Space for "2. System Architecture" and "Architecture Diagram" headings
            float captionSpace = 80f; // Space for "Figure 1" caption
            float maxWidth = pageSize.getWidth() - (2 * margin);
            float maxHeight = pageSize.getHeight() - headerSpace - captionSpace - margin;
            
            float imageAspectRatio = (float) pdImage.getHeight() / (float) pdImage.getWidth();
            
            // Calculate dimensions that fit within both width and height constraints
            float scaledWidth = maxWidth;
            float scaledHeight = scaledWidth * imageAspectRatio;
            
            // If height exceeds max, scale down based on height
            if (scaledHeight > maxHeight) {
                scaledHeight = maxHeight;
                scaledWidth = scaledHeight / imageAspectRatio;
            }
            
            // Center image horizontally
            float x = (pageSize.getWidth() - scaledWidth) / 2;
            // Position image below the "Architecture Diagram" heading
            // PDF coordinates: y=0 is bottom, y=height is top
            // We want to place it starting from top, going down
            float y = pageSize.getHeight() - headerSpace - scaledHeight;
            
            // Ensure image doesn't go below caption space
            if (y < captionSpace + margin) {
                // If image is too tall, scale it down further
                float availableHeight = pageSize.getHeight() - headerSpace - captionSpace - margin;
                scaledHeight = availableHeight;
                scaledWidth = scaledHeight / imageAspectRatio;
                x = (pageSize.getWidth() - scaledWidth) / 2;
                y = pageSize.getHeight() - headerSpace - scaledHeight;
            }
            
            // If image still doesn't fit on this page, move to next page
            if (y < margin + 50 || scaledHeight > pageSize.getHeight() - margin * 2) {
                if (targetPageIndex < document.getNumberOfPages() - 1) {
                    targetPage = document.getPage(targetPageIndex + 1);
                    pageSize = targetPage.getMediaBox();
                    // On new page, start from top with margin
                    float newMaxHeight = pageSize.getHeight() - (2 * margin) - captionSpace;
                    if (scaledHeight > newMaxHeight) {
                        scaledHeight = newMaxHeight;
                        scaledWidth = scaledHeight / imageAspectRatio;
                    }
                    x = (pageSize.getWidth() - scaledWidth) / 2;
                    y = pageSize.getHeight() - margin - scaledHeight - captionSpace;
                    targetPageIndex = targetPageIndex + 1;
                }
            }
            
            // Add image to PDF page
            PDPageContentStream contentStream = new PDPageContentStream(
                document, targetPage, PDPageContentStream.AppendMode.APPEND, true, true);
            
            // Draw a white background rectangle for the image container
            contentStream.setNonStrokingColor(255, 255, 255);
            contentStream.addRect(x - 5, y - 5, scaledWidth + 10, scaledHeight + 10);
            contentStream.fill();
            
            // Draw the image
            contentStream.drawImage(pdImage, x, y, scaledWidth, scaledHeight);
            contentStream.close();
            
            // Step 5: Save the final PDF
            ByteArrayOutputStream finalOutputStream = new ByteArrayOutputStream();
            document.save(finalOutputStream);
            document.close();
            
            byte[] finalPdfBytes = finalOutputStream.toByteArray();
            ByteArrayResource pdfResource = new ByteArrayResource(finalPdfBytes);
            
            logger.info("PDF generated successfully with color image embedded");
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"BudgetBuddy_AI_Submission_Document.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfResource);
                
        } catch (Exception e) {
            logger.error("Error generating PDF", e);
            
            // Fallback: return HTML if PDF conversion fails
            try {
                Resource resource = new ClassPathResource("static/SUBMISSION_DOCUMENT.html");
                if (resource.exists()) {
                    return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"SUBMISSION_DOCUMENT.html\"")
                        .contentType(MediaType.TEXT_HTML)
                        .body(resource);
                }
            } catch (Exception fallbackEx) {
                logger.error("Fallback also failed", fallbackEx);
            }
            
            return ResponseEntity.notFound().build();
        }
    }
}
