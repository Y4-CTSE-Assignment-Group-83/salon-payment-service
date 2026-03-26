package com.salon.payment.dto;

import com.salon.payment.model.PaymentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdatePaymentRequest {

    private String customerName;

    private String customerEmail;

    private Double amount;

    private String currency;

    private PaymentStatus status;
}
