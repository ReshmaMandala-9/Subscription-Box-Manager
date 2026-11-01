package com.example.submanager;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class RenewalService {

    public record Alert(Long id, String serviceName, String type, int daysLeft,
                        String date, double charge, String message) {}

    public record CalEntry(Long id, String serviceName, double charge, String type) {}

    private final SubscriptionRepository repo;
    private final List<Integer> reminderDays;

    public RenewalService(SubscriptionRepository repo,
                          @Value("${app.renewal.reminder-days:7,3}") String reminderDaysText) {
        this.repo = repo;
        this.reminderDays = Arrays.stream(reminderDaysText.split(","))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .map(Integer::parseInt)
                .toList();
    }

    private int stepMonths(Subscription s) {
        return switch (s.getBillingCycle()) {
            case MONTHLY -> 1;
            case QUARTERLY -> 3;
            case ANNUAL -> 12;
        };
    }

    private double charge(Subscription s) {
        double v = s.getAmount().doubleValue() / Math.max(1, s.getCoPayers());
        return Math.round(v * 100.0) / 100.0;
    }

    // All renewal dates of a subscription between from and to (inclusive)
    List<LocalDate> occurrences(Subscription s, LocalDate from, LocalDate to) {
        List<LocalDate> out = new ArrayList<>();
        LocalDate base = s.getNextRenewalDate();
        if (base == null || to.isBefore(from)) return out;
        int step = stepMonths(s);
        long k = Math.max(0, Math.floorDiv(ChronoUnit.MONTHS.between(base, from), (long) step) - 1);
        while (true) {
            LocalDate d = base.plusMonths(k * step);
            if (d.isAfter(to)) break;
            if (!d.isBefore(from)) out.add(d);
            k++;
        }
        return out;
    }

    private LocalDate nextRenewal(Subscription s, LocalDate today) {
        if (s.getNextRenewalDate() == null) return null;
        return occurrences(s, today, today.plusMonths(stepMonths(s) + 1L))
                .stream().findFirst().orElse(null);
    }

    private String when(int days) {
        if (days == 0) return "today";
        if (days == 1) return "tomorrow";
        return "in " + days + " days";
    }

    private String money(double v) {
        return String.format(Locale.US, "%.2f", v);
    }

    // Features 17, 18, 19: renewal, annual and trial alerts
    public List<Alert> alerts() {
        LocalDate today = LocalDate.now();
        int maxDays = reminderDays.stream().max(Integer::compare).orElse(7);
        List<Alert> out = new ArrayList<>();

        for (Subscription s : repo.findAll()) {
            if (s.getStatus() != Subscription.Status.ACTIVE) continue;
            double charge = charge(s);

            LocalDate trial = s.getTrialEndDate();
            boolean trialUpcoming = trial != null && !trial.isBefore(today);
            if (trialUpcoming) {
                int trialDays = (int) ChronoUnit.DAYS.between(today, trial);
                if (trialDays <= maxDays) {
                    out.add(new Alert(s.getId(), s.getServiceName(), "TRIAL_END", trialDays,
                            trial.toString(), charge,
                            "Free trial for " + s.getServiceName() + " ends " + when(trialDays)
                                    + ". It will convert to a paid plan (" + money(charge) + ")."));
                }
            }

            LocalDate next = nextRenewal(s, today);
            if (next == null) continue;
            if (trialUpcoming && trial.equals(next)) continue;   // avoid duplicate alert

            int days = (int) ChronoUnit.DAYS.between(today, next);
            boolean annual = s.getBillingCycle() == Subscription.BillingCycle.ANNUAL;

            if (annual && days <= 30) {
                out.add(new Alert(s.getId(), s.getServiceName(), "ANNUAL", days, next.toString(), charge,
                        "Annual plan " + s.getServiceName() + " renews " + when(days)
                                + " (" + money(charge) + "). Decide whether to keep it before the charge."));
            } else if (days <= maxDays) {
                out.add(new Alert(s.getId(), s.getServiceName(), "RENEWAL", days, next.toString(), charge,
                        s.getServiceName() + " renews " + when(days) + " (" + money(charge) + ")."));
            }
        }
        out.sort(Comparator.comparingInt(Alert::daysLeft));
        return out;
    }

    // Alerts that should be emailed today (exact reminder days, or 30 days for annual plans)
    public List<Alert> dueForEmail() {
        return alerts().stream()
                .filter(a -> reminderDays.contains(a.daysLeft())
                        || (a.type().equals("ANNUAL") && a.daysLeft() == 30))
                .toList();
    }

    // Feature 16: calendar data, keyed by date (yyyy-MM-dd)
    public Map<String, List<CalEntry>> calendar(int year, int month) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        Map<String, List<CalEntry>> out = new TreeMap<>();

        for (Subscription s : repo.findAll()) {
            if (s.getStatus() != Subscription.Status.ACTIVE) continue;
            double charge = charge(s);

            for (LocalDate d : occurrences(s, from, to)) {
                out.computeIfAbsent(d.toString(), k -> new ArrayList<>())
                        .add(new CalEntry(s.getId(), s.getServiceName(), charge, "RENEWAL"));
            }
            LocalDate t = s.getTrialEndDate();
            if (t != null && !t.isBefore(from) && !t.isAfter(to)) {
                out.computeIfAbsent(t.toString(), k -> new ArrayList<>())
                        .add(new CalEntry(s.getId(), s.getServiceName(), charge, "TRIAL_END"));
            }
        }
        return out;
    }
}