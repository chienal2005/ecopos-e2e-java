package com.ecopos.common;

import com.ecopos.data.OrderPayload;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.*;

/**
 * FixtureLoader — Đọc và xử lý dữ liệu test từ file JSON fixture
 *
 * Tất cả fixture JSON nằm trong: src/test/resources/fixtures/e2e/
 * Mỗi file chứa mảng các test case tương ứng với 1 suite
 */
public class FixtureLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Đọc file fixture JSON, trả về danh sách Map
     *
     * @param name tên fixture (không cần .json)
     * @return danh sách test case dạng Map
     */
    public static List<Map<String, Object>> load(String name) {
        String path = "fixtures/e2e/" + name + ".json";
        try (InputStream is = FixtureLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new RuntimeException("Không tìm thấy fixture: " + path);
            return MAPPER.readValue(is, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Lỗi đọc fixture " + path + ": " + e.getMessage(), e);
        }
    }

    /**
     * Chuyển danh sách fixture thành DataProvider format (Object[][])
     */
    public static Object[][] toDataProvider(String fixtureName) {
        return load(fixtureName).stream()
                .map(tc -> new Object[]{tc})
                .toArray(Object[][]::new);
    }

    /**
     * Build payload push order với override từ fixture
     * Xử lý: tp_order_code, mnb_store_code, items, customer_info
     */
    @SuppressWarnings("unchecked")
    public static OrderPayload.PartnerOrderPayload buildPayloadWithOverride(Map<String, Object> override) {
        OrderPayload.PartnerOrderPayload base = OrderPayload.buildPushOrderPayload();

        if (override.containsKey("tp_order_code")) {
            base.setTp_order_code((String) override.get("tp_order_code"));
        }
        if (override.containsKey("mnb_store_code")) {
            base.setMnb_store_code((String) override.get("mnb_store_code"));
        }
        if (override.containsKey("items")) {
            List<Map<String, Object>> rawItems = (List<Map<String, Object>>) override.get("items");
            base.setItems(rawItems.stream().map(item ->
                    OrderPayload.OrderItem.builder()
                            .sku((String) item.get("sku"))
                            .name((String) item.get("name"))
                            .qty(((Number) item.getOrDefault("qty", 1)).intValue())
                            .price(((Number) item.getOrDefault("price", 0)).intValue())
                            .build()
            ).toList());
        }
        if (override.containsKey("customer_info")) {
            Map<String, Object> ci = (Map<String, Object>) override.get("customer_info");
            base.setCustomer_info(OrderPayload.CustomerInfo.builder()
                    .masked_name((String) ci.getOrDefault("masked_name", "Test"))
                    .phone_last4((String) ci.getOrDefault("phone_last4", "0000"))
                    .delivery_address((String) ci.getOrDefault("delivery_address", "HCM"))
                    .build());
        }

        return base;
    }

    /**
     * Chuẩn hóa danh sách items từ fixture (set random qty nếu thiếu)
     */
    public static List<OrderPayload.OrderItem> normalizeItems(List<Map<String, Object>> rawItems) {
        Random random = new Random();
        return rawItems.stream().map(item -> {
            int qty = item.containsKey("qty")
                    ? ((Number) item.get("qty")).intValue()
                    : (random.nextInt(5) + 1);
            return OrderPayload.OrderItem.builder()
                    .sku((String) item.get("sku"))
                    .name((String) item.get("name"))
                    .qty(qty)
                    .price(((Number) item.getOrDefault("price", 0)).intValue())
                    .build();
        }).toList();
    }

    /**
     * Lấy danh sách đơn hàng pending_confirm từ API
     * Dùng cho Badge suite
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> getPendingConfirmList(String token) {
        String siteId = GlobalConstants.SITE_ID;
        com.ecopos.api.OrderQueryApi.ApiResult res =
                com.ecopos.api.OrderQueryApi.getIntegrationList(token, 1, 9999, siteId);
        List<Map<String, Object>> allItems = extractItems(res.body());
        return allItems.stream()
                .filter(o -> "pending_confirm".equalsIgnoreCase(
                        String.valueOf(o.getOrDefault("status", ""))))
                .toList();
    }

    /**
     * Đợi cho tới khi số lượng pending_confirm đạt expected count
     */
    public static List<Map<String, Object>> waitForPendingCount(
            String token, int expectedCount, int timeoutMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        List<Map<String, Object>> list = List.of();
        while (System.currentTimeMillis() - start < timeoutMs) {
            list = getPendingConfirmList(token);
            if (list.size() == expectedCount) return list;
            Thread.sleep(1000);
        }
        System.out.printf("[WARN] waitForPendingCount timeout! Expected: %d, Got: %d%n",
                expectedCount, list.size());
        return list;
    }

    /**
     * Trích xuất danh sách items từ API response body
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractItems(Object body) {
        if (body == null) return List.of();
        if (body instanceof Map<?, ?> map) {
            Map<String, Object> data = (Map<String, Object>) ((Map<?, ?>) map).get("data");
            if (data != null) {
                List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
                if (items != null) return items;
            }
            List<Map<String, Object>> items = (List<Map<String, Object>>) ((Map<String, Object>) map).get("items");
            if (items != null) return items;
        }
        return List.of();
    }
}
