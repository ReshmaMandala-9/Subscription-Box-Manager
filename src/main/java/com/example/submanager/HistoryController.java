package com.example.submanager;

import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class HistoryController {

    private final HistoryService service;

    public HistoryController(HistoryService service) {
        this.service = service;
    }

    @GetMapping("/payments")
    public List<HistoryService.PaymentRow> payments() {
        return service.paymentLog();
    }

    @GetMapping("/trend")
    public List<HistoryService.Point> trend() {
        return service.trend();
    }

    @GetMapping("/report")
    public HistoryService.Report report(@RequestParam(required = false) Integer year) {
        return service.report(year == null ? LocalDate.now().getYear() : year);
    }

    @GetMapping("/cancellations")
    public List<HistoryService.Cancelled> cancellations() {
        return service.cancellations();
    }
}