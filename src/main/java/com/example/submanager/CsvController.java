package com.example.submanager;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api")
public class CsvController {

    private final CsvService service;

    public CsvController(CsvService service) {
        this.service = service;
    }

    private ResponseEntity<byte[]> file(String name, String content) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(content.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/export/subscriptions.csv")
    public ResponseEntity<byte[]> exportSubscriptions() {
        return file("subscriptions.csv", service.exportSubscriptions());
    }

    @GetMapping("/export/payments.csv")
    public ResponseEntity<byte[]> exportPayments() {
        return file("payment-history.csv", service.exportPayments());
    }

    @GetMapping("/import/template.csv")
    public ResponseEntity<byte[]> template() {
        return file("subscription-template.csv", service.template());
    }

    @PostMapping(value = "/import", consumes = "text/plain")
    public CsvService.ImportResult importCsv(@RequestBody String text) {
        return service.importCsv(text);
    }
}