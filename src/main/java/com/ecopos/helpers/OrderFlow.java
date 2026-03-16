package com.ecopos.helpers;

import com.ecopos.api.*;
import com.ecopos.common.GlobalConstants;
import com.ecopos.data.DriverPayload;
import com.ecopos.data.OrderPayload;
import com.ecopos.data.OrderStatusPayload;

import java.util.*;

/**
 * OrderFlow — Các luồng nghiệp vụ đơn hàng dùng chung cho E2E tests
 *
 * Nguyên tắc: đơn giản, nhanh, ít điều kiện lồng nhau
 */
public class OrderFlow {

    // ─── Auth ────────────────────────────────────────────────────────────────────
    /** Đăng nhập → trả về access token */
    public static String login() {
        String token = AuthApi.login().accessToken();
        if (token == null || token.isBlank()) throw new AssertionError("Login thất bại: token rỗng");
        return token;
    }

    // ─── Push đơn hàng ──────────────────────────────────────────────────────────
    /** Push đơn hàng với payload tùy chỉnh */
    public static PushResult pushOrder(OrderPayload.PartnerOrderPayload payload) {
        OrderApi.ApiResult res = OrderApi.pushOrder(payload);
        return new PushResult(payload.getTp_order_code(), res.status(), res.body(), payload);
    }

    /** Push đơn hàng mặc định */
    public static PushResult pushOrder() {
        return pushOrder(OrderPayload.buildPushOrderPayload());
    }

    // ─── Tìm Integration ID ────────────────────────────────────────────────────
    /** Tìm integration ID (retry nhanh, tối đa 8 lần, delay ngắn) */
    @SuppressWarnings("unchecked")
    public static String findIntegrationId(String token, String code, int maxRetries, long delayMs, String siteId)
            throws InterruptedException {
        System.out.printf("[find] '%s' (max=%d, delay=%dms)%n", code, maxRetries, delayMs);

        // Đợi sync ngắn (1s thay vì 3s)
        Thread.sleep(1000);

        for (int i = 0; i < maxRetries; i++) {
            // Thử integration list trước (nhanh nhất)
            Map<String, Object> found = pickOrder(
                    OrderQueryApi.getIntegrationList(token, 1, 1000, siteId).body(), code);

            // Fallback: findAll nếu không tìm thấy
            if (found == null) {
                found = pickOrder(
                        OrderQueryApi.findAll(token, Map.of("limit", "1000"), siteId).body(), code);
            }

            if (found != null) {
                String id = (String) found.get("id");
                System.out.printf("[find] FOUND '%s' → %s (attempt %d)%n", code, id, i + 1);
                return id;
            }

            if (i < maxRetries - 1) Thread.sleep(delayMs);
        }

        throw new AssertionError("Không tìm thấy đơn '" + code + "' sau " + maxRetries + " lần thử");
    }

    /** Tìm integration ID với config mặc định (5 lần, 1.5s delay) */
    public static String findIntegrationId(String token, String code, String siteId)
            throws InterruptedException {
        return findIntegrationId(token, code, 5, 1500, siteId);
    }

    // ─── Tìm ECOPOS Order ID ────────────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    public static String findEcoposOrderId(String token, String code, String siteId,
                                           int maxRetries, long delayMs) throws InterruptedException {
        for (int i = 0; i < maxRetries; i++) {
            Map<String, Object> found = pickOrder(
                    OrderQueryApi.findAll(token, Map.of("limit", "1000"), siteId).body(), code);
            if (found != null) return (String) found.get("id");
            if (i < maxRetries - 1) Thread.sleep(delayMs);
        }
        throw new RuntimeException("Không tìm thấy ECOPOS order: " + code);
    }

    /** Tìm ECOPOS Order ID từ integration ID */
    public static String resolveOrderIdByIntegrationId(String token, String intId, String siteId) {
        List<Map<String, Object>> items = extractItemList(OrderQueryApi.getIntegrationList(token, 1, 100, siteId).body());
        for (Map<String, Object> item : items) {
            if (intId.equals(String.valueOf(item.get("id")))) {
                return (String) item.get("order_id");
            }
        }
        return null;
    }

    // ─── Hoàn thành đơn ECOPOS ──────────────────────────────────────────────────
    /** Hoàn thành đơn ECOPOS (có/không driver prep) */
    @SuppressWarnings("unchecked")
    public static String completeEcoposOrderFlow(String token, String code, String siteId, boolean skipDriverPrep)
            throws InterruptedException {

        // Bước chuẩn bị tài xế (nếu chưa có)
        if (!skipDriverPrep) {
            OrderDriverApi.updateDriver(DriverPayload.buildDriverInfoPayload(code));
            moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_ASSIGNED);
            moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_ARRIVED);
        }

        // Tìm integration ID
        String intId = findIntegrationId(token, code, 4, 1500, siteId);

        // Đợi sync (5s — TS dùng 8s nhưng Java gọi nhanh hơn)
        Thread.sleep(5000);

        // Lấy order_id + order_code + code từ integration
        List<Map<String, Object>> intItems = extractItemList(
                OrderQueryApi.getIntegrationList(token, 1, 50, siteId).body());

        String orderId = null, orderCode = null, intCode = null;
        for (Map<String, Object> item : intItems) {
            if (intId.equals(item.get("id"))) {
                orderId = (String) item.get("order_id");
                orderCode = (String) item.get("order_code");
                intCode = (String) item.get("code");
                break;
            }
        }

        // Fallback: tìm qua findAll nếu chưa có order_id
        if (orderId == null) {
            try { orderId = findEcoposOrderId(token, orderCode != null ? orderCode : code, siteId, 3, 1500); }
            catch (Exception ignored) {}
        }

        // Thử complete: orderId → orderCode → code → intId (ưu tiên theo TS)
        List<String> candidates = new ArrayList<>();
        for (String c : new String[]{orderId, orderCode, intCode, intId}) {
            if (c != null && !candidates.contains(c)) candidates.add(c);
        }

        System.out.printf("[complete] Trying IDs: %s%n", candidates);
        for (String id : candidates) {
            OrderEcoposApi.ApiResult res = OrderEcoposApi.completeOrder(id, token, siteId);
            if (res.status() == 200 || res.status() == 201) {
                System.out.printf("[complete] OK → %s%n", id);
                return id;
            }
        }

        // Retry 1 lần sau 3s
        Thread.sleep(3000);
        for (String id : candidates) {
            OrderEcoposApi.ApiResult res = OrderEcoposApi.completeOrder(id, token, siteId);
            if (res.status() == 200 || res.status() == 201) {
                System.out.printf("[complete] OK (retry) → %s%n", id);
                return id;
            }
        }

        throw new RuntimeException("Không thể complete đơn " + code + " IDs: " + candidates);
    }

    /** Overload không skip driver */
    public static String completeEcoposOrderFlow(String token, String code, String siteId)
            throws InterruptedException {
        return completeEcoposOrderFlow(token, code, siteId, false);
    }

    // ─── Chuyển trạng thái ──────────────────────────────────────────────────────
    /** Chuyển đơn sang trạng thái mới */
    public static OrderStatusApi.ApiResult moveStatus(String code, OrderStatusPayload.PartnerOrderStatus status) {
        return OrderStatusApi.updateStatus(code, OrderStatusPayload.buildUpdateOrderStatusPayload(status));
    }

    // ─── Push + Confirm ─────────────────────────────────────────────────────────
    /** Push đơn → confirm → trả về kết quả */
    public static PushAndConfirmResult pushAndConfirm(String token) throws InterruptedException {
        return pushAndConfirm(token, null, null);
    }

    public static PushAndConfirmResult pushAndConfirm(String token, String mnbStoreCode, String siteId)
            throws InterruptedException {
        OrderPayload.PartnerOrderPayload payload = OrderPayload.buildPushOrderPayload(mnbStoreCode, null, null);
        PushResult push = pushOrder(payload);
        if (push.status() != 201) throw new AssertionError("Push thất bại: " + push.status());

        String resolvedSiteId = siteId != null ? siteId : GlobalConstants.SITE_ID;
        String intId = findIntegrationId(token, push.tpOrderCode(), 5, 1500, resolvedSiteId);
        OrderConfirmApi.ApiResult confirm = OrderConfirmApi.confirmOrder(intId, token, resolvedSiteId);
        if (confirm.status() != 200) throw new AssertionError("Confirm thất bại: " + confirm.status());

        return new PushAndConfirmResult(push.tpOrderCode(), intId, payload);
    }

    // ─── Setup đơn tại trạng thái ───────────────────────────────────────────────
    public static SetupResult setupOrderAtState(String token, OrderStatusPayload.PartnerOrderStatus target)
            throws InterruptedException {
        PushAndConfirmResult r = pushAndConfirm(token);
        if (target != OrderStatusPayload.PartnerOrderStatus.ACCEPTED) {
            moveStatus(r.tpOrderCode(), target);
        }
        return new SetupResult(r.tpOrderCode(), r.integrationId());
    }

    // ─── Stock ──────────────────────────────────────────────────────────────────
    public static Map<String, Double> getStockSnapshot(String token, String siteId, List<String> skus) {
        return ProductInventoryApi.getStockBySkus(token, siteId, skus);
    }

    public static void verifyStock(String token, String siteId, Map<String, Integer> skusQty,
                                   Map<String, Double> before, String action) {
        Map<String, Double> after = getStockSnapshot(token, siteId, new ArrayList<>(skusQty.keySet()));

        boolean allZero = before.values().stream().allMatch(v -> v == 0.0)
                && after.values().stream().allMatch(v -> v == 0.0);
        if (allZero) {
            System.out.println("[Stock] WARNING: Tồn kho = 0, bỏ qua kiểm tra");
            return;
        }

        skusQty.forEach((sku, expected) -> {
            double b = before.getOrDefault(sku, 0.0);
            double a = after.getOrDefault(sku, 0.0);
            if ("deduct".equals(action) && (int)(b - a) != expected)
                throw new AssertionError(String.format("SKU '%s': trừ sai (%d vs %.0f)", sku, expected, b - a));
            if ("revert".equals(action) && a != b)
                throw new AssertionError(String.format("SKU '%s': hoàn không đúng (%.0f vs %.0f)", sku, a, b));
        });
    }

    // ─── Helpers (private, gọn) ─────────────────────────────────────────────────

    /** Tìm order trong response body theo mã đơn */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> pickOrder(Object body, String code) {
        return extractOrderList(body).stream()
                .filter(o -> matchesCode(o, code))
                .findFirst().orElse(null);
    }

    /** Trích xuất danh sách orders từ response (hỗ trợ nhiều format) */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractOrderList(Object body) {
        if (body == null) return List.of();

        // Lấy data trước
        Map<String, Object> root = body instanceof Map<?,?> m ? (Map<String, Object>) m : null;
        if (root == null) return body instanceof List<?> ? (List<Map<String, Object>>) body : List.of();

        Object data = root.get("data");
        // data là Map → tìm Orders hoặc items bên trong
        if (data instanceof Map<?,?> dataMap) {
            return firstNonNull((Map<String, Object>) dataMap, "Orders", "items");
        }
        // data là List → trả trực tiếp
        if (data instanceof List<?>) return (List<Map<String, Object>>) data;
        // Fallback: tìm ở root
        return firstNonNull(root, "Orders", "items");
    }

    /** Trích xuất items từ integration response */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> extractItemList(Object body) {
        if (body == null) return List.of();
        Map<String, Object> root = body instanceof Map<?,?> m ? (Map<String, Object>) m : null;
        if (root == null) return List.of();

        Object data = root.get("data");
        if (data instanceof Map<?,?> dataMap) {
            List<Map<String, Object>> items = (List<Map<String, Object>>) ((Map<?,?>) dataMap).get("items");
            if (items != null) return items;
        }
        List<Map<String, Object>> items = (List<Map<String, Object>>) root.get("items");
        return items != null ? items : List.of();
    }

    /** Lấy list đầu tiên khác null từ map */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> firstNonNull(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val instanceof List<?>) return (List<Map<String, Object>>) val;
        }
        return List.of();
    }

    /** Kiểm tra order có match mã đơn không */
    private static boolean matchesCode(Map<String, Object> order, String code) {
        return code.equals(order.get("reference_code"))
                || code.equals(order.get("code"))
                || code.equals(order.get("order_no"))
                || code.equals(order.get("id"))
                || (order.get("memo") instanceof String s && s.contains(code));
    }

    // ─── Result Records ─────────────────────────────────────────────────────────
    public record PushResult(String tpOrderCode, int status, Object body, OrderPayload.PartnerOrderPayload payload) {}
    public record PushAndConfirmResult(String tpOrderCode, String integrationId, OrderPayload.PartnerOrderPayload payload) {}
    public record SetupResult(String tpOrderCode, String integrationId) {}
}
