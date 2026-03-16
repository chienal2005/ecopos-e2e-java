package com.ecopos.data;

import com.ecopos.common.GlobalConstants;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * DriverPayload - Tương đương driver.payload.ts
 * Payload thông tin tài xế
 */
public class DriverPayload {

    @Data
    @Builder
    @Jacksonized
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DriverInfo {
        private String name;
        private String phone;
        private String license_plate;
        private String provider;
    }

    @Data
    @Builder
    @Jacksonized
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DriverInfoPayload {
        private String tp_order_code;
        private String mnb_store_code;
        private DriverInfo driver_info;
    }

    /**
     * Xây dựng payload thông tin tài xế mặc định
     */
    public static DriverInfoPayload buildDriverInfoPayload(String tpOrderCode) {
        return buildDriverInfoPayload(tpOrderCode, null, null, null, null);
    }

    public static DriverInfoPayload buildDriverInfoPayload(
            String tpOrderCode, String name, String phone, String licensePlate, String provider) {
        return DriverInfoPayload.builder()
                .tp_order_code(tpOrderCode)
                .mnb_store_code(GlobalConstants.MNB_STORE_CODE)
                .driver_info(DriverInfo.builder()
                        .name(name != null ? name : "Nguyen Van Tai")
                        .phone(phone != null ? phone : "0909123456")
                        .license_plate(licensePlate != null ? licensePlate : "59A-123.45")
                        .provider(provider != null ? provider : "Lalamove")
                        .build())
                .build();
    }
}
