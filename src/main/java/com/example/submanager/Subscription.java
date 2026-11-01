package com.example.submanager;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Data
public class Subscription {

    public enum Category {
        ENTERTAINMENT, SOFTWARE, HEALTH_FITNESS, FOOD_DELIVERY,
        NEWS_LEARNING, GAMING, SHOPPING, OTHER
    }

    public enum BillingCycle { MONTHLY, QUARTERLY, ANNUAL }

    public enum Status { ACTIVE, PAUSED, CANCELLED }

    public enum UsageFrequency { DAILY, WEEKLY, MONTHLY, RARELY }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String serviceName;

    @Enumerated(EnumType.STRING)
    private Category category = Category.OTHER;

    @NotNull
    @Positive
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private BillingCycle billingCycle = BillingCycle.MONTHLY;

    private LocalDate startDate;
    private LocalDate nextRenewalDate;
    private LocalDate trialEndDate;

    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;

    private LocalDate cancelledDate;   // set when status becomes CANCELLED
    private String cancelReason;       // why it was cancelled

    @Min(1)
    private int coPayers = 1;

    @Min(1)
    @Max(5)
    private Integer satisfaction = 3;

    @Enumerated(EnumType.STRING)
    private UsageFrequency usageFrequency = UsageFrequency.WEEKLY;
}