package com.example.submanager;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsBySubscriptionIdAndPaidOn(Long subscriptionId, LocalDate paidOn);

    List<Payment> findAllByOrderByPaidOnDesc();
}