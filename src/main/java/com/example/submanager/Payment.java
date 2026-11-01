package com.example.submanager;


import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Data
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long subscriptionId;
    private String serviceName;
    private String category;
    private double amount;        // your share of the charge
    private LocalDate paidOn;
}