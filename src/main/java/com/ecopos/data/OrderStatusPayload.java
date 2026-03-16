package com.ecopos.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * OrderStatusPayload - Tương đương order.status.payload.ts
 * Enum trạng thái đơn hàng và payload cập nhật
 */
public class OrderStatusPayload {

    /**
     * Trạng thái đơn hàng đối tác (TP)
     */
    public enum PartnerOrderStatus {
        ACCEPTED,
        REJECTED,
        DRIVER_ASSIGNED,
        DRIVER_PICKING,
        DRIVER_ARRIVED,
        HANDOVER,
        DRIVER_DELIVERING,
        DELIVERED,
        DRIVER_CANCELLED,
        DELIVERY_FAILED,
        ORDER_CANCELLED,
        TIMEOUT_CANCELLED
    }

    /**
     * Trạng thái nội bộ ECOPOS
     */
    public enum EcoOrderStatus {
        PENDING_CONFIRM,
        CONFIRMED,
        REJECTED,
        CANCELLED,
        SEARCHING_DRIVER,
        HANDOVER,
        IN_TRANSIT,
        COMPLETED
    }

    /**
     * Mapping TP → ECO status
     */
    public static EcoOrderStatus mapToEcoStatus(PartnerOrderStatus partnerStatus) {
        return switch (partnerStatus) {
            case ACCEPTED -> EcoOrderStatus.CONFIRMED;
            case REJECTED -> EcoOrderStatus.REJECTED;
            case DRIVER_ASSIGNED, DRIVER_PICKING, DRIVER_ARRIVED -> EcoOrderStatus.SEARCHING_DRIVER;
            case HANDOVER -> EcoOrderStatus.HANDOVER;
            case DELIVERED -> EcoOrderStatus.COMPLETED;
            case DELIVERY_FAILED, ORDER_CANCELLED, TIMEOUT_CANCELLED -> EcoOrderStatus.CANCELLED;
            case DRIVER_CANCELLED, DRIVER_DELIVERING -> EcoOrderStatus.IN_TRANSIT;
        };
    }

    /**
     * Payload cập nhật trạng thái
     */
    @Data
    @Builder
    @Jacksonized
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UpdateOrderStatusPayload {
        private String status;
        private String message;
        private String reason;
        private String timestamp;
    }

    /**
     * Builder payload cập nhật trạng thái
     */
    public static UpdateOrderStatusPayload buildUpdateOrderStatusPayload(PartnerOrderStatus status) {
        return buildUpdateOrderStatusPayload(status, null, null);
    }

    public static UpdateOrderStatusPayload buildUpdateOrderStatusPayload(
            PartnerOrderStatus status, String message, String reason) {
        // Format timestamp: 2026-03-13 10:30:00+07
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(
                java.time.ZoneId.of("Asia/Bangkok"));
        String timestamp = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "+07";

        return UpdateOrderStatusPayload.builder()
                .status(status.name())
                .message(message != null ? message : "Auto update by API")
                .reason(reason != null ? reason : "Automation testing")
                .timestamp(timestamp)
                .build();
    }
}
