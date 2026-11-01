package com.example.submanager;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class RenewalController {

    private final RenewalService service;

    public RenewalController(RenewalService service) {
        this.service = service;
    }

    @GetMapping("/alerts")
    public List<RenewalService.Alert> alerts() {
        return service.alerts();
    }

    @GetMapping("/calendar")
    public Map<String, List<RenewalService.CalEntry>> calendar(@RequestParam int year,
                                                               @RequestParam int month) {
        return service.calendar(year, month);
    }
}