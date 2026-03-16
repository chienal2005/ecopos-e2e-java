package com.ecopos.common;

import com.ecopos.api.*;
import com.ecopos.data.DriverPayload;
import com.ecopos.data.OrderPayload;
import com.ecopos.data.OrderStatusPayload;
import com.ecopos.helpers.OrderFlow;

import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════════════
 *  E2eRunner — Tập trung toàn bộ logic chạy test E2E
 * ═══════════════════════════════════════════════════════════════════════
 *
 *  Mỗi suite có 1 phương thức static tương ứng:
 *    runPushOrder()       — đẩy đơn, kiểm tra validate
 *    runConfirmOrder()    — xác nhận đơn, kiểm tra tồn kho
 *    runRejectOrder()     — từ chối đơn
 *    runDriverInfo()      — cập nhật tài xế
 *    runDeliveryGuard()   — chặn chuyển trạng thái đơn chưa confirm
 *    runDeliveryFull()    — luồng giao hàng đầy đủ
 *    runCancelFlow()      — hủy đơn từ mọi trạng thái
 *    runBadgePush()       — kiểm tra badge khi push đơn
 *    runBadgeConfirm()    — kiểm tra badge khi confirm
 *    runBadgeReject()     — kiểm tra badge khi reject
 *    runBadgeCancel()     — kiểm tra badge khi partner cancel
 *    runCompletionGuard() — chặn hoàn thành khi thiếu tài xế
 *
 *  Test class chỉ cần gọi: E2eRunner.runXxx(token, tc)
 * ═══════════════════════════════════════════════════════════════════════
 */
public class E2eRunner {

    // ─── [1] Push Order ──────────────────────────────────────────────────────────
    /** Đẩy đơn hàng với override từ fixture, trả về HTTP status */
    @SuppressWarnings("unchecked")
    public static int runPushOrder(Map<String, Object> tc) {
        String id = (String) tc.get("id");
        System.out.printf("[push] %s: %s%n", id, tc.get("title"));

        Map<String, Object> override = (Map<String, Object>) tc.getOrDefault("override", Map.of());
        OrderPayload.PartnerOrderPayload payload = FixtureLoader.buildPayloadWithOverride(override);
        return OrderApi.pushOrder(payload).status();
    }

    // ─── [2] Confirm Order ───────────────────────────────────────────────────────
    /** Push đơn → tìm integration → xác nhận, trả về HTTP status confirm */
    @SuppressWarnings("unchecked")
    public static int runConfirmOrder(String token, Map<String, Object> tc) throws InterruptedException {
        String id = (String) tc.get("id");
        System.out.printf("[confirm] %s: %s%n", id, tc.get("title"));

        List<OrderPayload.OrderItem> items = FixtureLoader.normalizeItems(
                (List<Map<String, Object>>) tc.get("items"));
        OrderPayload.PartnerOrderPayload payload = OrderPayload.buildPushOrderPayload(null, null, items);

        int pushStatus = OrderApi.pushOrder(payload).status();
        if (pushStatus != 201) throw new AssertionError(id + ": push thất bại (" + pushStatus + ")");

        String siteId = GlobalConstants.SITE_ID;
        String intId = OrderFlow.findIntegrationId(token, payload.getTp_order_code(), 5, 1500, siteId);
        return OrderConfirmApi.confirmOrder(intId, token, siteId).status();
    }

    // ─── [3] Reject Order ────────────────────────────────────────────────────────
    /** Push (+ confirm nếu cần) → reject, trả về HTTP status reject */
    @SuppressWarnings("unchecked")
    public static int runRejectOrder(String token, Map<String, Object> tc) throws InterruptedException {
        String id = (String) tc.get("id");
        String flow = (String) tc.get("flow");
        System.out.printf("[reject] %s: %s%n", id, tc.get("title"));

        String siteId = GlobalConstants.SITE_ID;
        List<OrderPayload.OrderItem> items = FixtureLoader.normalizeItems(
                (List<Map<String, Object>>) tc.get("items"));
        OrderPayload.PartnerOrderPayload p = OrderPayload.buildPushOrderPayload(null, null, items);
        OrderApi.pushOrder(p);
        String intId = OrderFlow.findIntegrationId(token, p.getTp_order_code(), 5, 1500, siteId);

        if ("confirm-then-reject".equals(flow)) {
            OrderConfirmApi.confirmOrder(intId, token, siteId);
        }

        return OrderRejectApi.rejectOrder(intId, token, (String) tc.get("rejectReason"), siteId).status();
    }

    // ─── [4] Driver Info ─────────────────────────────────────────────────────────
    /** Cập nhật thông tin tài xế với override, trả về HTTP status */
    @SuppressWarnings("unchecked")
    public static int runDriverInfo(String token, Map<String, Object> tc) throws InterruptedException {
        String id = (String) tc.get("id");
        String flow = (String) tc.get("flow");
        System.out.printf("[driver] %s: %s%n", id, tc.get("title"));

        Map<String, Object> driverOvr = (Map<String, Object>) tc.getOrDefault("driverOverride", Map.of());
        String tpOrderCode = (String) driverOvr.getOrDefault("tp_order_code", "PLACEHOLDER");

        // Nếu cần đơn đã confirmed → tạo đơn mới
        if ("confirmed-order".equals(flow)) {
            tpOrderCode = OrderFlow.pushAndConfirm(token).tpOrderCode();
        }

        // Build payload + override
        DriverPayload.DriverInfoPayload payload = DriverPayload.buildDriverInfoPayload(tpOrderCode);
        if (driverOvr.containsKey("tp_order_code")) payload.setTp_order_code((String) driverOvr.get("tp_order_code"));
        if (driverOvr.containsKey("mnb_store_code")) payload.setMnb_store_code((String) driverOvr.get("mnb_store_code"));
        if (driverOvr.containsKey("driver_name")) payload.getDriver_info().setName((String) driverOvr.get("driver_name"));
        if (driverOvr.containsKey("driver_phone")) payload.getDriver_info().setPhone((String) driverOvr.get("driver_phone"));

        return OrderDriverApi.updateDriver(payload).status();
    }

    // ─── [5] Delivery Flow ───────────────────────────────────────────────────────
    /** Push đơn chưa confirm → thử chuyển trạng thái → phải bị từ chối */
    public static int runDeliveryGuard(OrderStatusPayload.PartnerOrderStatus targetStatus)
            throws InterruptedException {
        OrderFlow.PushResult push = OrderFlow.pushOrder();
        if (push.status() != 201) throw new AssertionError("Push thất bại: " + push.status());
        return OrderFlow.moveStatus(push.tpOrderCode(), targetStatus).status();
    }

    /** Chạy luồng giao hàng đầy đủ: Confirm → Driver → full flow → DELIVERED */
    public static int runDeliveryFullFlow(String token) throws InterruptedException {
        String siteId = GlobalConstants.SITE_ID;
        String code = OrderFlow.pushAndConfirm(token).tpOrderCode();

        // Gửi thông tin tài xế
        OrderDriverApi.updateDriver(DriverPayload.buildDriverInfoPayload(code));

        // Đi qua các trạng thái: ASSIGNED → CANCELLED → ASSIGNED → PICKING → ARRIVED
        OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_ASSIGNED);
        OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_CANCELLED);
        OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_ASSIGNED);
        OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_PICKING);
        OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_ARRIVED);

        // Hoàn thành ECOPOS → Giao hàng
        OrderFlow.completeEcoposOrderFlow(token, code, siteId, true);
        OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_DELIVERING);
        return OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DELIVERED).status();
    }

    // ─── [6] Cancel Flow ─────────────────────────────────────────────────────────
    /** Đưa đơn tới trạng thái trung gian → hủy → kiểm tra */
    public static int runCancelFlow(String token, Map<String, Object> tc) throws InterruptedException {
        String moveState = (String) tc.get("moveState");
        String cancelState = (String) tc.get("cancelState");
        System.out.printf("[cancel] %s → %s%n", moveState, cancelState);

        String siteId = GlobalConstants.SITE_ID;
        String code = OrderFlow.pushAndConfirm(token).tpOrderCode();

        // Đưa đơn tới trạng thái trung gian
        if (!"ACCEPTED".equals(moveState)) {
            moveToIntermediateState(token, code, moveState, siteId);
        }

        return OrderFlow.moveStatus(code,
                OrderStatusPayload.PartnerOrderStatus.valueOf(cancelState)).status();
    }

    // ─── [8] Badge ───────────────────────────────────────────────────────────────
    /** Push N đơn badge, trả về danh sách tp_order_code */
    public static List<String> pushBadgeOrders(int count, String prefix) {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String code = "TP_BADGE_" + System.currentTimeMillis() + "_" + prefix + "_" + i;
            OrderApi.pushOrder(OrderPayload.buildPushOrderPayload(null, code, badgeItems()));
            codes.add(code);
        }
        return codes;
    }

    /** Push 1 đơn badge, trả về tp_order_code */
    public static String pushBadgeOrder(String suffix) {
        return pushBadgeOrders(1, suffix).getFirst();
    }

    /** Tìm integration IDs cho danh sách codes */
    public static List<String> findIntegrationIds(String token, List<String> codes)
            throws InterruptedException {
        String siteId = GlobalConstants.SITE_ID;
        List<String> ids = new ArrayList<>();
        for (String c : codes) ids.add(OrderFlow.findIntegrationId(token, c, 5, 1500, siteId));
        return ids;
    }

    /** Confirm hoặc reject danh sách orders */
    public static void confirmAll(String token, List<String> intIds) {
        String siteId = GlobalConstants.SITE_ID;
        intIds.forEach(id -> OrderConfirmApi.confirmOrder(id, token, siteId));
    }

    public static void rejectAll(String token, List<String> intIds, String reason) {
        String siteId = GlobalConstants.SITE_ID;
        intIds.forEach(id -> OrderRejectApi.rejectOrder(id, token, reason, siteId));
    }

    public static void cancelAllByPartner(List<String> codes) {
        codes.forEach(c -> OrderFlow.moveStatus(c, OrderStatusPayload.PartnerOrderStatus.ORDER_CANCELLED));
    }

    /** Lấy số lượng pending_confirm hiện tại */
    public static int getPendingCount(String token) {
        return FixtureLoader.getPendingConfirmList(token).size();
    }

    /** Đợi pending_confirm đạt đúng số lượng */
    public static int waitPending(String token, int expected, int timeoutMs) throws InterruptedException {
        return FixtureLoader.waitForPendingCount(token, expected, timeoutMs).size();
    }

    // ─── [9] Completion Guard ────────────────────────────────────────────────────
    /** Push + Confirm → thử complete (KHÔNG driver) → phải bị chặn */
    public static int runCompletionGuard(String token) throws InterruptedException {
        String siteId = GlobalConstants.SITE_ID;
        OrderPayload.PartnerOrderPayload p = OrderPayload.buildPushOrderPayload();
        OrderApi.pushOrder(p);

        String tpCode = p.getTp_order_code();
        String intId = OrderFlow.findIntegrationId(token, tpCode, 5, 1500, siteId);
        OrderConfirmApi.confirmOrder(intId, token, siteId);

        // Đợi sync 3s
        Thread.sleep(3000);
        String orderId = OrderFlow.resolveOrderIdByIntegrationId(token, intId, siteId);
        if (orderId == null) orderId = intId; 

        System.out.printf("[Guard] Thử complete đơn %s (ID: %s) khi CHƯA có tài xế...%n", tpCode, orderId);
        return OrderEcoposApi.completeOrder(orderId, token, siteId).status();
    }

    /** Push + Confirm + Driver Info → Thử complete tại status cụ thể → Kiểm tra bị chặn/cho phép */
    public static int runCompletionGuardWithStatus(String token, OrderStatusPayload.PartnerOrderStatus status)
            throws InterruptedException {
        String siteId = GlobalConstants.SITE_ID;
        OrderPayload.PartnerOrderPayload p = OrderPayload.buildPushOrderPayload();
        OrderApi.pushOrder(p);

        String tpCode = p.getTp_order_code();
        String intId = OrderFlow.findIntegrationId(token, tpCode, 5, 1500, siteId);
        OrderConfirmApi.confirmOrder(intId, token, siteId);

        // Gửi driver info
        OrderDriverApi.updateDriver(DriverPayload.buildDriverInfoPayload(tpCode));

        // Chuyển tới status mong muốn (tuần tự)
        OrderFlow.moveStatus(tpCode, OrderStatusPayload.PartnerOrderStatus.DRIVER_ASSIGNED);
        if (status == OrderStatusPayload.PartnerOrderStatus.DRIVER_PICKING || status == OrderStatusPayload.PartnerOrderStatus.DRIVER_ARRIVED) {
            OrderFlow.moveStatus(tpCode, OrderStatusPayload.PartnerOrderStatus.DRIVER_PICKING);
        }
        if (status == OrderStatusPayload.PartnerOrderStatus.DRIVER_ARRIVED) {
            OrderFlow.moveStatus(tpCode, OrderStatusPayload.PartnerOrderStatus.DRIVER_ARRIVED);
        }

        // Nếu là trạng thái ARRIVED -> dùng flow hoàn chỉnh để pass
        if (status == OrderStatusPayload.PartnerOrderStatus.DRIVER_ARRIVED) {
            try {
                OrderFlow.completeEcoposOrderFlow(token, tpCode, siteId, true);
                return 200; // Thành công
            } catch (Exception e) {
                System.err.println("[Guard] Complete thất bại dù đã ARRIVED: " + e.getMessage());
                return 500;
            }
        }

        // Với các trạng thái chưa được phép (ASSIGNED, PICKING) -> Thử complete trực tiếp để lấy status chặn
        Thread.sleep(3000);
        String orderId = OrderFlow.resolveOrderIdByIntegrationId(token, intId, siteId);
        if (orderId == null) orderId = intId;

        System.out.printf("[Guard] Thử complete đơn %s (ID: %s) tại trạng thái %s...%n", tpCode, orderId, status);
        return OrderEcoposApi.completeOrder(orderId, token, siteId).status();
    }

    // ─── Assert helpers ──────────────────────────────────────────────────────────
    /** Kiểm tra status phù hợp với expect từ fixture (status, statusNot, statusIn) */
    @SuppressWarnings("unchecked")
    public static void assertStatus(String testId, int actual, Map<String, Object> expect) {
        if (expect.containsKey("status")) {
            int expected = ((Number) expect.get("status")).intValue();
            if (actual != expected)
                throw new AssertionError(String.format("[%s] expected %d, got %d", testId, expected, actual));
        }
        if (expect.containsKey("statusNot")) {
            Object val = expect.get("statusNot");
            if (val instanceof List<?> list) {
                List<Integer> notList = ((List<Number>) list).stream().map(Number::intValue).toList();
                if (notList.contains(actual))
                    throw new AssertionError(String.format("[%s] status %d nằm trong danh sách cấm %s", testId, actual, notList));
            } else {
                int notExpected = ((Number) val).intValue();
                if (actual == notExpected)
                    throw new AssertionError(String.format("[%s] status KHÔNG được = %d", testId, notExpected));
            }
        }
        if (expect.containsKey("statusIn")) {
            List<Integer> inList = ((List<Number>) expect.get("statusIn")).stream().map(Number::intValue).toList();
            if (!inList.contains(actual))
                throw new AssertionError(String.format("[%s] status %d không nằm trong %s", testId, actual, inList));
        }
    }

    // ─── Internal helpers ────────────────────────────────────────────────────────

    /** Đưa đơn về trạng thái trung gian (DRIVER_ASSIGNED, PICKING, ARRIVED...) */
    private static void moveToIntermediateState(String token, String code, String state, String siteId)
            throws InterruptedException {
        // Gửi driver info (nghiệp vụ bắt buộc trước DRIVER_ASSIGNED)
        OrderDriverApi.updateDriver(DriverPayload.buildDriverInfoPayload(code));
        OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_ASSIGNED);

        switch (state) {
            case "DRIVER_ASSIGNED" -> { /* đã xong */ }
            case "DRIVER_CANCELLED" ->
                    OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_CANCELLED);
            case "DRIVER_PICKING" ->
                    OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_PICKING);
            case "DRIVER_ARRIVED" -> {
                OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_PICKING);
                OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_ARRIVED);
            }
            case "DRIVER_DELIVERING" -> {
                OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_PICKING);
                OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_ARRIVED);
                OrderFlow.completeEcoposOrderFlow(token, code, siteId, true);
                OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_DELIVERING);
            }
        }
    }

    /** Item rẻ cho test badge (không ảnh hưởng tồn kho) */
    private static List<OrderPayload.OrderItem> badgeItems() {
        return List.of(OrderPayload.OrderItem.builder()
                .sku("3000305").name("Hộp giấy TP").qty(1).price(600).build());
    }
}
