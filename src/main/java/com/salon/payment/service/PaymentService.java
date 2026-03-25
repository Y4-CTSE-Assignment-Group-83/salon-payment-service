package com.salon.payment.service;

import com.salon.payment.dto.CreatePaymentRequest;
import com.salon.payment.dto.PaymentResponse;
import com.salon.payment.dto.BookingUpdateRequest;
import com.salon.payment.model.Payment;
import com.salon.payment.model.PaymentStatus;
import com.salon.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final LemonSqueezyService lemonSqueezyService;
    private final WebClient.Builder webClientBuilder;

    @Value("${booking.service.url}")
    private String bookingServiceUrl;

    public PaymentResponse createPayment(CreatePaymentRequest request) {
        log.info("Creating payment for booking: {}", request.getBookingId());

        var existingPayment = paymentRepository.findByBookingId(request.getBookingId());
        if (existingPayment.isPresent()) {
            Payment payment = existingPayment.get();
            PaymentResponse response = mapToResponse(payment);
            response.setMessage("Payment already exists for this booking");
            return response;
        }

        Payment payment = new Payment();
        payment.setBookingId(request.getBookingId());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setCustomerEmail(request.getCustomerEmail());
        payment.setCustomerName(request.getCustomerName());
        payment.setLemonSqueezyVariantId(request.getVariantId());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCreatedAt(LocalDateTime.now());
        payment.setTransactionId("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment record created with ID: {}", savedPayment.getId());

        try {
            String successUrl = "http://localhost:3000/payment-success?bookingId=" + request.getBookingId();

            LemonSqueezyService.CheckoutResponse checkoutResponse =
                    lemonSqueezyService.createCheckout(savedPayment, successUrl).block();

            savedPayment.setCheckoutUrl(checkoutResponse.getUrl());
            savedPayment.setLemonSqueezyCheckoutRequestId(checkoutResponse.getCheckoutRequestId());
            savedPayment.setStatus(PaymentStatus.PROCESSING);
            savedPayment.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(savedPayment);

            log.info("Checkout URL generated: {}, Checkout Request ID: {}",
                    checkoutResponse.getUrl(), checkoutResponse.getCheckoutRequestId());

            return mapToResponse(savedPayment);

        } catch (Exception error) {
            savedPayment.setStatus(PaymentStatus.FAILED);
            savedPayment.setFailureReason("Failed to create checkout: " + error.getMessage());
            savedPayment.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(savedPayment);

            log.error("Failed to create checkout", error);
            return mapToResponse(savedPayment);
        }
    }

    public PaymentResponse getPaymentByBookingId(String bookingId) {
        Payment payment = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new RuntimeException("Payment not found for booking: " + bookingId));
        return mapToResponse(payment);
    }

    public PaymentResponse getPaymentById(String id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found with ID: " + id));
        return mapToResponse(payment);
    }

    public PaymentResponse updatePaymentStatus(String orderId, String status, String checkoutId, String paymentId) {
        log.info("Updating payment status - orderId: {}, status: {}, checkoutId: {}, paymentId: {}",
                orderId, status, checkoutId, paymentId);

        Payment payment = null;

        // First try to find by payment ID if provided (from custom data)
        if (paymentId != null && !paymentId.isEmpty()) {
            log.info("Trying to find payment by payment ID: {}", paymentId);
            payment = paymentRepository.findById(paymentId).orElse(null);
            if (payment != null) {
                log.info("Found payment by payment ID: {}", paymentId);
            }
        }

        // If not found by payment ID, try by order ID
        if (payment == null && orderId != null && !orderId.isEmpty()) {
            log.info("Trying to find payment by order ID: {}", orderId);
            payment = paymentRepository.findByLemonSqueezyOrderId(orderId).orElse(null);
            if (payment != null) {
                log.info("Found payment by order ID: {}", orderId);
            }
        }

        // If not found by order ID, try by checkout request ID
        if (payment == null && checkoutId != null && !checkoutId.isEmpty()) {
            log.info("Trying to find payment by checkout request ID: {}", checkoutId);
            payment = paymentRepository.findByLemonSqueezyCheckoutRequestId(checkoutId).orElse(null);
            if (payment != null) {
                log.info("Found payment by checkout request ID: {}", checkoutId);
            }
        }

        // If still not found, throw exception
        if (payment == null) {
            String errorMsg = String.format("Payment not found with any criteria - orderId: %s, checkoutId: %s, paymentId: %s",
                    orderId, checkoutId, paymentId);
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }

        PaymentStatus newStatus = switch (status) {
            case "paid" -> PaymentStatus.COMPLETED;
            case "failed" -> PaymentStatus.FAILED;
            case "refunded" -> PaymentStatus.REFUNDED;
            case "pending" -> PaymentStatus.PROCESSING;
            default -> {
                log.warn("Unknown status: {}, keeping current status: {}", status, payment.getStatus());
                yield payment.getStatus();
            }
        };

        log.info("Updating payment {} from {} to {}", payment.getId(), payment.getStatus(), newStatus);

        payment.setStatus(newStatus);
        if (orderId != null) {
            payment.setLemonSqueezyOrderId(orderId);
        }
        if (checkoutId != null) {
            payment.setLemonSqueezyCheckoutId(checkoutId);
        }

        if (newStatus == PaymentStatus.COMPLETED) {
            payment.setPaidAt(LocalDateTime.now());
        }

        payment.setUpdatedAt(LocalDateTime.now());
        Payment updatedPayment = paymentRepository.save(payment);

        // Notify booking service
        notifyBookingService(updatedPayment);

        return mapToResponse(updatedPayment);
    }

    private void notifyBookingService(Payment payment) {
        try {
            BookingUpdateRequest updateRequest = new BookingUpdateRequest();
            updateRequest.setBookingId(payment.getBookingId());
            updateRequest.setPaymentId(payment.getId());
            updateRequest.setPaymentStatus(payment.getStatus().toString());
            updateRequest.setTransactionId(payment.getTransactionId());
            updateRequest.setLemonSqueezyOrderId(payment.getLemonSqueezyOrderId());

            WebClient webClient = webClientBuilder.build();

            String url = bookingServiceUrl + "/api/bookings/" + payment.getBookingId() + "/payment-status";

            log.info("Notifying booking service at: {} with data: {}", url, updateRequest);

            webClient.put()
                    .uri(url)
                    .bodyValue(updateRequest)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnSuccess(response -> log.info("Booking service notified successfully: {}", response))
                    .doOnError(error -> log.error("Failed to notify booking service: {}", error.getMessage()))
                    .subscribe();

        } catch (Exception e) {
            log.error("Error notifying booking service: {}", e.getMessage());
        }
    }

    private PaymentResponse mapToResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(payment.getId());
        response.setBookingId(payment.getBookingId());
        response.setAmount(payment.getAmount());
        response.setStatus(payment.getStatus());
        response.setTransactionId(payment.getTransactionId());
        response.setCreatedAt(payment.getCreatedAt());
        response.setCheckoutUrl(payment.getCheckoutUrl());
        response.setLemonSqueezyOrderId(payment.getLemonSqueezyOrderId());

        String message;
        switch (payment.getStatus()) {
            case COMPLETED:
                message = "Payment completed successfully";
                break;
            case PROCESSING:
                message = "Redirect to checkout to complete payment";
                break;
            case PENDING:
                message = "Payment is pending";
                break;
            case FAILED:
                message = "Payment failed: " + (payment.getFailureReason() != null ? payment.getFailureReason() : "Unknown error");
                break;
            case REFUNDED:
                message = "Payment has been refunded";
                break;
            default:
                message = "Payment status: " + payment.getStatus();
        }
        response.setMessage(message);

        return response;
    }

    public List<PaymentResponse> getAllPayments() {
        return paymentRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public void deletePayment(String id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found with ID: " + id));

        paymentRepository.delete(payment);
        log.info("Payment deleted successfully with ID: {}", id);
    }
}