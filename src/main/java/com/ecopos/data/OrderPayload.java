package com.ecopos.data;

import com.ecopos.common.GlobalConstants;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * OrderPayload - Tương đương order.payload.ts
 * Xây dựng payload cho đơn hàng đối tác
 */
public class OrderPayload {

    @Data
    @Builder
    @Jacksonized
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderItem {
        private String sku;
        private String name;
        private int qty;
        private int price;
    }

    @Data
    @Builder
    @Jacksonized
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CustomerInfo {
        private String masked_name;
        private String phone_last4;
        private String delivery_address;
    }

    @Data
    @Builder
    @Jacksonized
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PartnerOrderPayload {
        private String tp_order_code;
        private String mnb_store_code;
        private CustomerInfo customer_info;
        private List<OrderItem> items;
    }

    // ─── Danh sách sản phẩm HO PHAT ────────────────────────────────────────────
    public static final List<OrderItem> THO_PHAT_PRODUCT_LIST = Arrays.asList(
            OrderItem.builder().sku("5000159").name("B.Bao TP Heo 2C 600g").qty(1).price(60000).build(),
            OrderItem.builder().sku("5000144").name("B.Bao TP Chay Đ.Biệt 640g").qty(1).price(68000).build(),
            OrderItem.builder().sku("5000085").name("B.Bao TP K.Môn 280g").qty(1).price(28000).build(),
            OrderItem.builder().sku("5000095").name("B.Bao TP KN Cua Xanh Vani 260g").qty(1).price(22000).build(),
            OrderItem.builder().sku("5000282").name("B.Bao TP Gà Nướng P.Mai 400g").qty(1).price(54000).build(),
            OrderItem.builder().sku("5000177").name("B.Bao TP T.Cẩm 1C1M 780g").qty(1).price(120000).build(),
            OrderItem.builder().sku("5000072").name("B.Bao MH Bí đỏ S.Tươi 300g").qty(1).price(28800).build(),
            OrderItem.builder().sku("5000118").name("H.Cảo TP Heo 500g").qty(1).price(70000).build(),
            OrderItem.builder().sku("5000184").name("B.Bao TP X.Xíu 280g").qty(1).price(44000).build(),
            OrderItem.builder().sku("5000083").name("B.Bao TP H.Kim 300g").qty(1).price(60000).build(),
            OrderItem.builder().sku("5000075").name("B.Bao TP Cade 280g").qty(1).price(28000).build()
    );

    private static final Random RANDOM = new Random();

    public static int randomQty() {
        return RANDOM.nextInt(10) + 1;
    }

    /**
     * Chọn ngẫu nhiên n sản phẩm từ danh sách, với qty random
     */
    public static List<OrderItem> pickRandomProducts(List<OrderItem> pool, int count) {
        return pickRandomProducts(pool, count, null);
    }

    public static List<OrderItem> pickRandomProducts(List<OrderItem> pool, int count, Integer fixedQty) {
        List<OrderItem> shuffled = new ArrayList<>(pool);
        java.util.Collections.shuffle(shuffled, RANDOM);
        List<OrderItem> result = new ArrayList<>();
        int take = Math.min(count, shuffled.size());
        for (int i = 0; i < take; i++) {
            OrderItem original = shuffled.get(i);
            result.add(OrderItem.builder()
                    .sku(original.getSku())
                    .name(original.getName())
                    .qty(fixedQty != null ? fixedQty : randomQty())
                    .price(original.getPrice())
                    .build());
        }
        return result;
    }

    /**
     * Xây dựng payload đơn hàng mặc định
     */
    public static PartnerOrderPayload buildPushOrderPayload() {
        return buildPushOrderPayload(null, null, null);
    }

    public static PartnerOrderPayload buildPushOrderPayload(
            String mnbStoreCode, String tpOrderCode, List<OrderItem> items) {

        List<OrderItem> validProducts = THO_PHAT_PRODUCT_LIST.stream()
                .filter(p -> p.getPrice() > 0)
                .toList();
        List<OrderItem> defaultItems = pickRandomProducts(validProducts, 3);

        return PartnerOrderPayload.builder()
                .tp_order_code(tpOrderCode != null ? tpOrderCode : "TP_ORD_" + System.currentTimeMillis())
                .mnb_store_code(mnbStoreCode != null ? mnbStoreCode : GlobalConstants.MNB_STORE_CODE)
                .customer_info(CustomerInfo.builder()
                        .masked_name("Nguyen Van A")
                        .phone_last4("0961478888")
                        .delivery_address("12 Nguyen Hue, Q1, HCM")
                        .build())
                .items(items != null ? items : defaultItems)
                .build();
    }

    /**
     * Build payload sử dụng Tho Phat products với qty tùy chọn
     */
    public static PartnerOrderPayload buildThoPhatOrderPayload(int count, Integer overrideQty) {
        List<OrderItem> validProducts = THO_PHAT_PRODUCT_LIST.stream()
                .filter(p -> p.getPrice() > 0)
                .toList();
        List<OrderItem> items = pickRandomProducts(validProducts, count, overrideQty);

        return PartnerOrderPayload.builder()
                .tp_order_code("TP_ORD_" + System.currentTimeMillis())
                .mnb_store_code(GlobalConstants.MNB_STORE_CODE)
                .customer_info(CustomerInfo.builder()
                        .masked_name("Nguyen Van A")
                        .phone_last4("0961478888")
                        .delivery_address("12 Nguyen Hue, Q1, HCM")
                        .build())
                .items(items)
                .build();
    }
}
