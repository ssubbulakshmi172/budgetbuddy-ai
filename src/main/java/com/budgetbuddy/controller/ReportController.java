package com.budgetbuddy.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @Value("${reports.folder.path}")
    private String reportsFolderPath;

    // ✅ 1️⃣ List all reports
    @GetMapping
    public List<Map<String, String>> listReports() throws IOException {
        Path dir = Paths.get(reportsFolderPath);

        if (!Files.exists(dir)) {
            return List.of(Map.of("error", "Reports folder not found"));
        }

        return Files.list(dir)
                .filter(Files::isRegularFile)
                .map(file -> Map.of(
                        "name", file.getFileName().toString(),
                        "url", "/api/reports/view/" + file.getFileName()
                ))
                .sorted(Comparator.comparing(r -> r.get("name")))
                .collect(Collectors.toList());
    }

    // ✅ 2️⃣ Serve a specific file (PNG / JSON / etc.)
    @GetMapping("/view/{filename:.+}")
    public ResponseEntity<Resource> viewReport(@PathVariable String filename) throws IOException {
        Path file = Paths.get(reportsFolderPath).resolve(filename).normalize();

        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(file.toUri());
        String contentType = Files.probeContentType(file);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFileName().toString() + "\"")
                .body(resource);
    }
}
