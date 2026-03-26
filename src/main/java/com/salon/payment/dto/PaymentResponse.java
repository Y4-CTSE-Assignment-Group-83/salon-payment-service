package com.salon.payment.dto;

import com.salon.payment.model.PaymentStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PaymentResponse {
    private String paymentId;
    private String bookingId;
    private Double amount;
    private PaymentStatus status;
    private String message;
    private String checkoutUrl;
    private String transactionId;
    private LocalDateTime createdAt;
    private String lemonSqueezyOrderId;
    private String customerName;
    private String customerEmail;
    private String currency;
}