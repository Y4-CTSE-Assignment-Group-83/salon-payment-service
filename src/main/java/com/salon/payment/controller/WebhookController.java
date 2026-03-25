package com.salon.payment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salon.payment.dto.PaymentResponse;
import com.salon.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/webhook/lemon-squeezy")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @Value("${lemon.squeezy.webhook-secret}")
    private String webhookSecret;

    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestHeader(value = "X-Event-Name", required = false) String headerEventName) {

        log.info("========== LEMON SQUEEZY WEBHOOK RECEIVED ==========");

        try {
            // Print full raw payload for debugging
            log.debug("Webhook Raw Payload: {}", payload);
            log.info("Webhook Signature Header: {}", signature);
            log.info("Webhook Event Header: {}", headerEventName);

            // Verify webhook signature
            if (signature != null && !verifySignature(payload, signature)) {
                log.error("Webhook signature verification FAILED");
                return ResponseEntity.status(401).body("Invalid signature");
            }

            // Parse JSON
            JsonNode root = objectMapper.readTree(payload);

            // Get event name from meta
            String eventName = null;
            if (root.has("meta") && root.get("meta").has("event_name")) {
                eventName = root.get("meta").get("event_name").asText();
            }
            if (eventName == null) {
                eventName = headerEventName;
            }

            // Get custom data from meta (this contains our payment_id and booking_id)
            JsonNode customData = null;
            String paymentId = null;
            String bookingId = null;

            if (root.has("meta") && root.get("meta").has("custom_data")) {
                customData = root.get("meta").get("custom_data");
                paymentId = getText(customData, "payment_id");
                bookingId = getText(customData, "booking_id");
            }

            // Get data node.
            JsonNode data = root.path("data");
            JsonNode attributes = data.path("attributes");

            log.info("Webhook Event Name: {}", eventName);
            log.info("Webhook Data Node: {}", data.toPrettyString());

            // Extract fields
            String orderId = getText(data, "id");
            String status = getText(attributes, "status");

            // Get checkout ID from attributes if available
            String checkoutId = getText(attributes, "checkout_id");

            log.info("Parsed Webhook Data:");
            log.info("Order ID: {}", orderId);
            log.info("Status: {}", status);
            log.info("Checkout ID: {}", checkoutId);
            log.info("Booking ID (custom): {}", bookingId);
            log.info("Payment ID (custom): {}", paymentId);

            // Process order events
            if ("order_created".equals(eventName) || "order_paid".equals(eventName)) {

                if (orderId != null && status != null) {

                    // Always use paymentId from custom data if available - this is the most reliable
                    if (paymentId != null && !paymentId.isEmpty()) {
                        try {
                            log.info("Attempting to update payment using payment ID: {}", paymentId);
                            PaymentResponse response = paymentService.updatePaymentStatus(orderId, status, checkoutId, paymentId);
                            log.info("Payment status updated successfully using payment ID: {}", paymentId);
                        } catch (Exception e) {
                            log.error("Failed to update payment using payment ID: {}", paymentId, e);
                            throw e;
                        }
                    } else {
                        // Fallback to using order ID
                        log.info("No payment ID in custom data, trying with order ID: {}", orderId);
                        PaymentResponse response = paymentService.updatePaymentStatus(orderId, status, checkoutId, null);
                        log.info("Payment status updated successfully for order: {}", orderId);
                    }

                } else {
                    log.error("Missing orderId or status in webhook");
                }

            } else if ("order_refunded".equals(eventName)) {

                if (orderId != null) {
                    paymentService.updatePaymentStatus(orderId, "refunded", checkoutId, paymentId);
                    log.info("Payment refunded successfully for order: {}", orderId);
                }

            } else {
                log.info("Unhandled webhook event: {}", eventName);
            }

            log.info("========== WEBHOOK PROCESSED SUCCESSFULLY ==========");
            return ResponseEntity.ok("Webhook processed");

        } catch (Exception e) {
            log.error("Webhook processing FAILED", e);
            return ResponseEntity.status(500).body("Webhook processing error: " + e.getMessage());
        }
    }

    private String getText(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private boolean verifySignature(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(secretKey);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String s = Integer.toHexString(0xff & b);
                if (s.length() == 1) hex.append('0');
                hex.append(s);
            }

            boolean valid = hex.toString().equals(signature);
            log.info("Webhook signature verification result: {}", valid);
            return valid;

        } catch (Exception e) {
            log.error("Signature verification error", e);
            return false;
        }
    }
}