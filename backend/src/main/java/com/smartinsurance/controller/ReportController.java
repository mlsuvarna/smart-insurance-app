package com.smartinsurance.controller;

import com.smartinsurance.service.ReportService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartinsurance.service.PdfService;

@RestController
@RequestMapping("/api/v1/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {
    private final ReportService reports;
    private final PdfService pdf;
    public ReportController(ReportService reports, PdfService pdf) { this.reports = reports; this.pdf = pdf; }
    @GetMapping("/summary")
    public Map<String, Object> summary(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam LocalDate from,
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam LocalDate to,
                                       @RequestParam(required = false) Long productId,
                                       @RequestParam(required = false) String claimType,
                                       @RequestParam(required = false) String status) {
        return reports.summary(from, to, productId, claimType, status);
    }
    @GetMapping("/top-customers")
    public List<Map<String, Object>> topCustomers(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam LocalDate from,
                                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam LocalDate to,
                                                   @RequestParam(required = false) Long productId) {
        return reports.topPremiumCustomers(from, to, productId);
    }
    @GetMapping(value = "/top-customers.csv", produces = "text/csv")
    public ResponseEntity<byte[]> topCustomersCsv(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam LocalDate from,
                                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam LocalDate to,
                                                  @RequestParam(required = false) Long productId) {
        return csvResponse("customer_id,full_name,premium_total\n",
                reports.topPremiumCustomers(from, to, productId), List.of("customer_id", "full_name", "premium_total"),
                "top-customers.csv");
    }
    @GetMapping("/monthly-revenue")
    public List<Map<String, Object>> monthly(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam LocalDate from,
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam LocalDate to,
                                             @RequestParam(required = false) Long productId) {
        return reports.monthlyRevenue(from, to, productId);
    }
    @GetMapping(value = "/monthly-revenue.csv", produces = "text/csv")
    public ResponseEntity<byte[]> monthlyCsv(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam LocalDate from,
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam LocalDate to,
                                             @RequestParam(required = false) Long productId) {
        return csvResponse("revenue_month,premium_revenue\n",
                reports.monthlyRevenue(from, to, productId), List.of("revenue_month", "premium_revenue"),
                "monthly-revenue.csv");
    }
    @GetMapping("/products")
    public List<Map<String, Object>> products(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam LocalDate from,
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam LocalDate to,
                                              @RequestParam(required = false) Long productId,
                                              @RequestParam(required = false) String claimType,
                                              @RequestParam(required = false) String status) {
        return reports.productStatistics(from, to, productId, claimType, status);
    }
    @GetMapping(value = "/products.csv", produces = "text/csv")
    public ResponseEntity<byte[]> productsCsv(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam LocalDate from,
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam LocalDate to,
                                              @RequestParam(required = false) Long productId,
                                              @RequestParam(required = false) String claimType,
                                              @RequestParam(required = false) String status) {
        return csvResponse("product_id,name,claim_count,premium_collected,settlements,approximate_margin\n",
                reports.productStatistics(from, to, productId, claimType, status),
                List.of("product_id", "name", "claim_count", "premium_collected", "settlements", "approximate_margin"),
                "product-statistics.csv");
    }
    @GetMapping(value = "/summary.csv", produces = "text/csv")
    public ResponseEntity<byte[]> csv(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam LocalDate from,
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam LocalDate to,
                                      @RequestParam(required = false) Long productId,
                                      @RequestParam(required = false) String claimType,
                                      @RequestParam(required = false) String status) {
        Map<String, Object> report = reports.summary(from, to, productId, claimType, status);
        String content = "metric,value\n" + report.entrySet().stream()
                .map(e -> csvEscape(e.getKey()) + "," + csvEscape(String.valueOf(e.getValue())))
                .collect(java.util.stream.Collectors.joining("\n"));
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=claim-report.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(content.getBytes(StandardCharsets.UTF_8));
    }
    @GetMapping(value = "/summary.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam LocalDate from,
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam LocalDate to,
                                      @RequestParam(required = false) Long productId,
                                      @RequestParam(required = false) String claimType,
                                      @RequestParam(required = false) String status) {
        String title = "Claims report " + from + " to " + to;
        List<String> lines = reports.summary(from, to, productId, claimType, status).entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue()).toList();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=claims-report.pdf")
                .contentType(MediaType.APPLICATION_PDF).body(pdf.create(title, lines));
    }
    private ResponseEntity<byte[]> csvResponse(String header, List<Map<String, Object>> rows, List<String> columns,
                                               String filename) {
        String content = header + rows.stream().map(row -> columns.stream()
                        .map(column -> csvEscape(String.valueOf(row.getOrDefault(column, ""))))
                        .collect(java.util.stream.Collectors.joining(",")))
                .collect(java.util.stream.Collectors.joining("\n"));
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(content.getBytes(StandardCharsets.UTF_8));
    }
    private String csvEscape(String value) {
        boolean negativeNumber = value.matches("-\\d+(\\.\\d+)?");
        String safe = !negativeNumber && value.matches("^[=+@\\t\\r-].*") ? "'" + value : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
