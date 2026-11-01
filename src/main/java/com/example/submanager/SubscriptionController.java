package com.example.submanager;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api")
public class SubscriptionController {

    private final SubscriptionRepository repo;
    private final SubscriptionService service;
    private final HistoryService history;

    public SubscriptionController(SubscriptionRepository repo, SubscriptionService service,
                                  HistoryService history) {
        this.repo = repo;
        this.service = service;
        this.history = history;
    }

    @GetMapping("/subscriptions")
    public List<SubscriptionService.SubView> list() {
        return service.all();
    }

    @PostMapping("/subscriptions")
    public Subscription add(@Valid @RequestBody Subscription s) {
        s.setId(null);
        return repo.save(s);
    }

    @PutMapping("/subscriptions/{id}")
    public ResponseEntity<Subscription> update(@PathVariable Long id,
                                               @Valid @RequestBody Subscription s) {
        if (!repo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        s.setId(id);
        return ResponseEntity.ok(repo.save(s));
    }

    @PatchMapping("/subscriptions/{id}/status")
    public ResponseEntity<Subscription> status(@PathVariable Long id,
                                               @RequestParam Subscription.Status value,
                                               @RequestParam(required = false) String reason) {
        return repo.findById(id)
                .map(s -> {
                    if (value == Subscription.Status.CANCELLED) {
                        if (s.getStatus() != Subscription.Status.CANCELLED) {
                            history.syncOne(s);   // record all charges up to today first
                            s.setCancelledDate(LocalDate.now());
                            s.setCancelReason(reason == null || reason.isBlank()
                                    ? "No reason given" : reason.trim());
                        }
                    } else {
                        if (value == Subscription.Status.PAUSED) history.syncOne(s);
                        s.setCancelledDate(null);
                        s.setCancelReason(null);
                    }
                    s.setStatus(value);
                    return ResponseEntity.ok(repo.save(s));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/subscriptions/{id}")
    public void delete(@PathVariable Long id) {
        repo.deleteById(id);
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return service.dashboard();
    }
}