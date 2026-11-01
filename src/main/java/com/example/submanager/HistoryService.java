package com.example.submanager;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HistoryService {

    public record PaymentRow(Long id, String serviceName, String category, double amount, String paidOn) {}
    public record Point(String month, double total) {}
    public record ServiceTotal(String name, int charges, double total) {}
    public record Report(int year, double total, int charges, Map<String, Double> byCategory,
                         List<Double> byMonth, List<ServiceTotal> services) {}
    public record Cancelled(Long id, String serviceName, String category, String cancelledDate,
                            String reason, double totalPaid) {}

    private final SubscriptionRepository subs;
    private final PaymentRepository payments;

    public HistoryService(SubscriptionRepository subs, PaymentRepository payments) {
        this.subs = subs;
        this.payments = payments;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private int stepMonths(Subscription s) {
        return switch (s.getBillingCycle()) {
            case MONTHLY -> 1;
            case QUARTERLY -> 3;
            case ANNUAL -> 12;
        };
    }

    // Creates any missing charges for one subscription, from its start date up to today.
    public void syncOne(Subscription s) {
        if (s.getId() == null || s.getStartDate() == null || s.getAmount() == null) return;

        LocalDate today = LocalDate.now();
        int step = stepMonths(s);
        double share = round(s.getAmount().doubleValue() / Math.max(1, s.getCoPayers()));

        for (int k = 0; k < 600; k++) {
            LocalDate d = s.getStartDate().plusMonths((long) k * step);
            if (d.isAfter(today)) break;
            // charges before the free trial ends are free
            if (s.getTrialEndDate() != null && d.isBefore(s.getTrialEndDate())) continue;
            if (payments.existsBySubscriptionIdAndPaidOn(s.getId(), d)) continue;

            Payment p = new Payment();
            p.setSubscriptionId(s.getId());
            p.setServiceName(s.getServiceName());
            p.setCategory(String.valueOf(s.getCategory()));
            p.setAmount(share);
            p.setPaidOn(d);
            payments.save(p);
        }
    }

    // Only active subscriptions keep adding new charges.
    public void sync() {
        for (Subscription s : subs.findAll()) {
            if (s.getStatus() == Subscription.Status.ACTIVE) syncOne(s);
        }
    }

    // Feature 20
    public List<PaymentRow> paymentLog() {
        sync();
        return payments.findAllByOrderByPaidOnDesc().stream()
                .map(p -> new PaymentRow(p.getId(), p.getServiceName(), p.getCategory(),
                        p.getAmount(), p.getPaidOn().toString()))
                .toList();
    }

    // Feature 21: total spent in each of the last 12 months
    public List<Point> trend() {
        sync();
        YearMonth now = YearMonth.now();
        Map<YearMonth, Double> sums = new LinkedHashMap<>();
        for (int i = 11; i >= 0; i--) sums.put(now.minusMonths(i), 0.0);

        for (Payment p : payments.findAll()) {
            YearMonth ym = YearMonth.from(p.getPaidOn());
            if (sums.containsKey(ym)) sums.put(ym, sums.get(ym) + p.getAmount());
        }
        List<Point> out = new ArrayList<>();
        sums.forEach((ym, total) -> out.add(new Point(ym.toString(), round(total))));
        return out;
    }

    // Feature 23: yearly report
    public Report report(int year) {
        sync();
        List<Payment> list = payments.findAll().stream()
                .filter(p -> p.getPaidOn().getYear() == year)
                .toList();

        double total = list.stream().mapToDouble(Payment::getAmount).sum();

        Map<String, Double> byCategory = new TreeMap<>();
        double[] months = new double[12];
        Map<String, double[]> perService = new HashMap<>();   // [count, total]

        for (Payment p : list) {
            byCategory.merge(p.getCategory(), p.getAmount(), Double::sum);
            months[p.getPaidOn().getMonthValue() - 1] += p.getAmount();
            double[] agg = perService.computeIfAbsent(p.getServiceName(), k -> new double[2]);
            agg[0] += 1;
            agg[1] += p.getAmount();
        }
        byCategory.replaceAll((k, v) -> round(v));

        List<Double> monthList = new ArrayList<>();
        for (double m : months) monthList.add(round(m));

        List<ServiceTotal> services = perService.entrySet().stream()
                .map(e -> new ServiceTotal(e.getKey(), (int) e.getValue()[0], round(e.getValue()[1])))
                .sorted(Comparator.comparingDouble(ServiceTotal::total).reversed())
                .toList();

        return new Report(year, round(total), list.size(), byCategory, monthList, services);
    }

    // Feature 22
    public List<Cancelled> cancellations() {
        Map<Long, Double> paidBySub = payments.findAll().stream()
                .collect(Collectors.groupingBy(Payment::getSubscriptionId,
                        Collectors.summingDouble(Payment::getAmount)));

        return subs.findAll().stream()
                .filter(s -> s.getStatus() == Subscription.Status.CANCELLED)
                .sorted(Comparator.comparing(Subscription::getCancelledDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(s -> new Cancelled(s.getId(), s.getServiceName(), String.valueOf(s.getCategory()),
                        s.getCancelledDate() == null ? "-" : s.getCancelledDate().toString(),
                        s.getCancelReason() == null ? "-" : s.getCancelReason(),
                        round(paidBySub.getOrDefault(s.getId(), 0.0))))
                .toList();
    }
}