package com.example.submanager;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SubscriptionService {

    public record SubView(Subscription sub, double monthlyCost, double costPerDay,
                          int roiScore, String verdict) {}

    private final SubscriptionRepository repo;

    public SubscriptionService(SubscriptionRepository repo) {
        this.repo = repo;
    }

    // Your share of the cost, normalised to one month
    double monthlyCost(Subscription s) {
        double share = s.getAmount().doubleValue() / Math.max(1, s.getCoPayers());
        return switch (s.getBillingCycle()) {
            case MONTHLY -> share;
            case QUARTERLY -> share / 3;
            case ANNUAL -> share / 12;
        };
    }

    // ROI: value (satisfaction x usage) compared with cost
    int roi(Subscription s, double monthly) {
        int freq = switch (s.getUsageFrequency()) {
            case DAILY -> 4;
            case WEEKLY -> 3;
            case MONTHLY -> 2;
            case RARELY -> 1;
        };
        int value = s.getSatisfaction() * freq; // max 20
        double score = (value / 20.0) * 100 - Math.min(30, monthly / 2);
        return (int) Math.max(0, Math.round(score));
    }

    SubView toView(Subscription s) {
        double m = monthlyCost(s);
        int roi = roi(s, m);
        return new SubView(s, round(m), round(m * 12 / 365), roi,
                roi >= 40 ? "Worth It" : "Consider Cancelling");
    }

    public List<SubView> all() {
        return repo.findAll().stream().map(this::toView).toList();
    }

    public Map<String, Object> dashboard() {
        List<SubView> active = all().stream()
                .filter(v -> v.sub().getStatus() == Subscription.Status.ACTIVE)
                .toList();

        double monthly = active.stream().mapToDouble(SubView::monthlyCost).sum();

        Map<String, Double> byCategory = active.stream().collect(Collectors.groupingBy(
                v -> v.sub().getCategory().name(),
                Collectors.summingDouble(SubView::monthlyCost)));

        List<SubView> top = active.stream()
                .sorted(Comparator.comparingDouble(SubView::monthlyCost).reversed())
                .limit(5)
                .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("monthlyTotal", round(monthly));
        out.put("annualProjection", round(monthly * 12));
        out.put("byCategory", byCategory);
        out.put("topSpending", top);
        return out;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}