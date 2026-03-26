package com.salon.payment.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "payments")
public class Payment {
    @Id
    private String id;
    private String bookingId;
    private Double amount;
    private String currency = "USD";
    private String customerEmail;
    private String customerName;
    private PaymentStatus status;
    private String lemonSqueezyOrderId;
    private String lemonSqueezyCheckoutId;
    private String lemonSqueezyVariantId;
    private String lemonSqueezyProductId;
    private String lemonSqueezyCheckoutRequestId;
    private String checkoutUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime paidAt;
    private String failureReason;
    private String transactionId;


}

