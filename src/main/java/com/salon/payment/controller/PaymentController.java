package com.salon.payment.controller;

import com.salon.payment.dto.CreatePaymentRequest;
import com.salon.payment.dto.PaymentResponse;
import com.salon.payment.dto.UpdatePaymentRequest;
import com.salon.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = {
        "http://localhost:3000",
        "http://ctse-alb-320060941.eu-north-1.elb.amazonaws.com"
})
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create")
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        log.info("Received payment creation request for booking: {}", request.getBookingId());
        PaymentResponse response = paymentService.createPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<PaymentResponse> getPaymentByBookingId(@PathVariable String bookingId) {
        log.info("Fetching payment for booking: {}", bookingId);
        PaymentResponse response = paymentService.getPaymentByBookingId(bookingId);
        return ResponseEntity.ok(response);
    }
    @GetMapping("/all")
    public ResponseEntity<List<PaymentResponse>> getAllPayments() {
        log.info("Fetching all payments");
        List<PaymentResponse> responses = paymentService.getAllPayments();
        return ResponseEntity.ok(responses);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deletePayment(@PathVariable String id) {
        log.info("Deleting payment with ID: {}", id);
        paymentService.deletePayment(id);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Payment deleted successfully");
        response.put("paymentId", id);

        return ResponseEntity.ok(response);
    }
    @PutMapping("/{id}")
    public ResponseEntity<PaymentResponse> updatePayment(
            @PathVariable String id,
            @Valid @RequestBody UpdatePaymentRequest request
    ) {
        log.info("Updating payment with ID: {}", id);
        PaymentResponse response = paymentService.updatePayment(id, request);
        return ResponseEntity.ok(response);
    }
    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> testEndpoint() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Payment Controller is working!");
        response.put("status", "SUCCESS");
        return ResponseEntity.ok(response);
    }

    
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPaymentById(@PathVariable String id) {
        log.info("Fetching payment with ID: {}", id);
        PaymentResponse response = paymentService.getPaymentById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "payment-service");
        return ResponseEntity.ok(status);
    }
}
