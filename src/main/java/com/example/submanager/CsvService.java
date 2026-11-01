package com.example.submanager;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class CsvService {

    public record ImportResult(int imported, int skipped, List<String> errors) {}

    public static final String HEADER =
            "serviceName,category,amount,billingCycle,startDate,nextRenewalDate,"
            + "trialEndDate,status,coPayers,satisfaction,usageFrequency";
    private static final int MAX_ROWS = 1000;

    private final SubscriptionRepository repo;
    private final HistoryService history;

    public CsvService(SubscriptionRepository repo, HistoryService history) {
        this.repo = repo;
        this.history = history;
    }

    /* ---------------- Export ---------------- */

    private String csv(String v, boolean guardFormula) {
        if (v == null) return "";
        // stops spreadsheet apps from running text such as =SUM(...) as a formula
        if (guardFormula && !v.isEmpty() && "=+-@\t".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private String str(Object o) {
        return o == null ? "" : o.toString();
    }

    public String exportSubscriptions() {
        StringBuilder sb = new StringBuilder("\uFEFF").append(HEADER).append("\r\n");
        for (Subscription s : repo.findAll()) {
            sb.append(String.join(",",
                    csv(s.getServiceName(), true),
                    str(s.getCategory()),
                    s.getAmount() == null ? "" : s.getAmount().toPlainString(),
                    str(s.getBillingCycle()),
                    str(s.getStartDate()),
                    str(s.getNextRenewalDate()),
                    str(s.getTrialEndDate()),
                    str(s.getStatus()),
                    str(s.getCoPayers()),
                    str(s.getSatisfaction()),
                    str(s.getUsageFrequency())
            )).append("\r\n");
        }
        return sb.toString();
    }

    public String exportPayments() {
        StringBuilder sb = new StringBuilder("\uFEFF").append("date,service,category,amount\r\n");
        for (HistoryService.PaymentRow p : history.paymentLog()) {
            sb.append(p.paidOn()).append(',')
              .append(csv(p.serviceName(), true)).append(',')
              .append(csv(p.category(), false)).append(',')
              .append(String.format(Locale.US, "%.2f", p.amount()))
              .append("\r\n");
        }
        return sb.toString();
    }

    public String template() {
        return HEADER + "\r\n"
                + "Netflix,ENTERTAINMENT,499,MONTHLY,2026-06-15,2026-11-15,,ACTIVE,1,4,WEEKLY\r\n"
                + "Spotify,ENTERTAINMENT,119,MONTHLY,2026-08-10,2026-11-10,,ACTIVE,1,5,DAILY\r\n"
                + "Gym,HEALTH_FITNESS,5000,ANNUAL,2026-03-01,2027-03-01,,ACTIVE,1,2,RARELY\r\n";
    }

    /* ---------------- Import ---------------- */

    static List<List<String>> parse(String text) {
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < len && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                    else quoted = false;
                } else cell.append(c);
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < len && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString());
                cell.setLength(0);
                if (!(row.size() == 1 && row.get(0).isBlank())) rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            if (!(row.size() == 1 && row.get(0).isBlank())) rows.add(row);
        }
        return rows;
    }

    private String get(List<String> row, Map<String, Integer> idx, String col) {
        Integer i = idx.get(col.toLowerCase());
        if (i == null || i >= row.size()) return "";
        return row.get(i).trim();
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String col, String v, E def, boolean strict) {
        if (v.isBlank()) return def;
        String n = v.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        try {
            return Enum.valueOf(type, n);
        } catch (IllegalArgumentException e) {
            if (strict) throw new IllegalArgumentException(col + " has an invalid value '" + v + "'");
            return def;
        }
    }

    private LocalDate parseDate(String col, String v) {
        if (v.isBlank()) return null;
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(col + " must look like 2026-11-15 (yyyy-MM-dd)");
        }
    }

    private int parseInt(String col, String v, int def) {
        if (v.isBlank()) return def;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(col + " must be a whole number");
        }
    }

    public ImportResult importCsv(String text) {
        List<String> errors = new ArrayList<>();
        List<List<String>> rows = parse(text == null ? "" : text);
        if (rows.size() < 2) {
            errors.add("The file has no data rows. Download the template and fill it in.");
            return new ImportResult(0, 0, errors);
        }

        Map<String, Integer> idx = new HashMap<>();
        List<String> head = rows.get(0);
        for (int i = 0; i < head.size(); i++) idx.put(head.get(i).trim().toLowerCase(), i);
        if (!idx.containsKey("servicename") || !idx.containsKey("amount")) {
            errors.add("The first row must contain the columns serviceName and amount.");
            return new ImportResult(0, 0, errors);
        }

        List<Subscription> existing = new ArrayList<>(repo.findAll());
        int imported = 0, skipped = 0;

        for (int r = 1; r < rows.size(); r++) {
            if (r > MAX_ROWS) {
                errors.add("Only the first " + MAX_ROWS + " rows were read.");
                break;
            }
            List<String> row = rows.get(r);
            try {
                String name = get(row, idx, "serviceName");
                if (name.isBlank()) throw new IllegalArgumentException("serviceName is empty");

                BigDecimal amount;
                try {
                    amount = new BigDecimal(get(row, idx, "amount"));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("amount must be a number");
                }
                if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be greater than 0");

                Subscription s = new Subscription();
                s.setServiceName(name);
                s.setAmount(amount);
                s.setCategory(parseEnum(Subscription.Category.class, "category",
                        get(row, idx, "category"), Subscription.Category.OTHER, false));
                s.setBillingCycle(parseEnum(Subscription.BillingCycle.class, "billingCycle",
                        get(row, idx, "billingCycle"), Subscription.BillingCycle.MONTHLY, true));
                s.setStatus(parseEnum(Subscription.Status.class, "status",
                        get(row, idx, "status"), Subscription.Status.ACTIVE, true));
                s.setUsageFrequency(parseEnum(Subscription.UsageFrequency.class, "usageFrequency",
                        get(row, idx, "usageFrequency"), Subscription.UsageFrequency.WEEKLY, true));
                s.setStartDate(parseDate("startDate", get(row, idx, "startDate")));
                s.setNextRenewalDate(parseDate("nextRenewalDate", get(row, idx, "nextRenewalDate")));
                s.setTrialEndDate(parseDate("trialEndDate", get(row, idx, "trialEndDate")));

                int co = parseInt("coPayers", get(row, idx, "coPayers"), 1);
                if (co < 1) throw new IllegalArgumentException("coPayers must be at least 1");
                s.setCoPayers(co);

                int sat = parseInt("satisfaction", get(row, idx, "satisfaction"), 3);
                if (sat < 1 || sat > 5) throw new IllegalArgumentException("satisfaction must be 1 to 5");
                s.setSatisfaction(sat);

                boolean duplicate = existing.stream().anyMatch(e ->
                        e.getServiceName().equalsIgnoreCase(name)
                                && e.getAmount().compareTo(amount) == 0
                                && e.getBillingCycle() == s.getBillingCycle());
                if (duplicate) {
                    skipped++;
                    errors.add("Row " + (r + 1) + ": " + name + " already exists, skipped.");
                    continue;
                }

                if (s.getStatus() == Subscription.Status.CANCELLED) {
                    s.setCancelledDate(LocalDate.now());
                    s.setCancelReason("Imported as cancelled");
                }
                Subscription saved = repo.save(s);
                if (saved.getStatus() == Subscription.Status.CANCELLED) history.syncOne(saved);
                existing.add(saved);
                imported++;
            } catch (RuntimeException e) {
                skipped++;
                errors.add("Row " + (r + 1) + ": " + e.getMessage());
            }
        }
        return new ImportResult(imported, skipped, errors);
    }
}