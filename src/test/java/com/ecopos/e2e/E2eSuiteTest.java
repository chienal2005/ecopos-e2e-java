package com.ecopos.e2e;

import com.ecopos.common.*;
import com.ecopos.data.OrderStatusPayload;
import org.testng.Assert;
import org.testng.ITestResult;
import org.testng.annotations.*;

import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════════════
 *  E2eSuiteTest — Bộ kiểm thử E2E toàn diện
 * ═══════════════════════════════════════════════════════════════════════
 *
 *  55 test cases | 8 suites | Data-driven từ JSON fixtures
 *  Chạy: mvn test
 *
 *  Logic xử lý nằm trong: com.ecopos.common.E2eRunner
 *  Test class này chỉ khai báo + gọi runner
 * ═══════════════════════════════════════════════════════════════════════
 */
public class E2eSuiteTest {

    private String token;
    private final List<TelegramReporter.TestRow> results = Collections.synchronizedList(new ArrayList<>());
    private long startTime;

    // ── Setup / Teardown ─────────────────────────────────────────────────────────

    @BeforeClass
    public void setup() {
        startTime = System.currentTimeMillis();
        token = com.ecopos.helpers.OrderFlow.login();
        System.out.println("✅ Đăng nhập OK. Token: " + token.substring(0, 20) + "...");
    }

    @AfterMethod
    public void gatherResult(ITestResult r) {
        String name = r.getMethod().getMethodName();
        String desc = r.getMethod().getDescription();
        String suite = desc != null ? desc.replaceAll("\\s*—.*", "") : name;
        String status = r.getStatus() == ITestResult.SUCCESS ? "PASSED"
                : r.getStatus() == ITestResult.FAILURE ? "FAILED" : "SKIPPED";
        String error = r.getThrowable() != null ? r.getThrowable().getMessage() : null;
        String testId = name;
        if (r.getParameters().length > 0 && r.getParameters()[0] instanceof Map<?,?> m) {
            if (m.get("id") != null) testId = m.get("id").toString();
            if (m.get("moveState") != null) testId = m.get("moveState") + "→" + m.get("cancelState");
        }
        results.add(new TelegramReporter.TestRow(suite, testId, desc != null ? desc : name,
                status, r.getEndMillis() - r.getStartMillis(), error));
    }

    @AfterClass
    public void sendTelegram() {
        double sec = (System.currentTimeMillis() - startTime) / 1000.0;
        System.out.printf("%n═══ TỔNG KẾT: %d tests | %.1fs ═══%n%n", results.size(), sec);
        TelegramReporter.sendReport(results, sec);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // [1] PUSH ORDER — 12 TCs: Tạo đơn hàng (validate fields)
    // ══════════════════════════════════════════════════════════════════════════════

    @DataProvider(name = "push")
    public Object[][] push() { return FixtureLoader.toDataProvider("push-order"); }

    @Test(dataProvider = "push", description = "[1] Push Order")
    @SuppressWarnings("unchecked")
    public void suite1_Push(Map<String, Object> tc) {
        int status = E2eRunner.runPushOrder(tc);
        E2eRunner.assertStatus((String) tc.get("id"), status, (Map<String, Object>) tc.get("expect"));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // [2] CONFIRM ORDER — 5 TCs: Xác nhận đơn (tồn kho, SP bán âm)
    // ══════════════════════════════════════════════════════════════════════════════

    @DataProvider(name = "confirm")
    public Object[][] confirm() { return FixtureLoader.toDataProvider("confirm-order"); }

    @Test(dataProvider = "confirm", description = "[2] Confirm Order")
    @SuppressWarnings("unchecked")
    public void suite2_Confirm(Map<String, Object> tc) throws InterruptedException {
        int status = E2eRunner.runConfirmOrder(token, tc);
        int expected = ((Number)((Map<String,Object>) tc.get("expect")).get("status")).intValue();
        Assert.assertEquals(status, expected, tc.get("id") + ": expected " + expected);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // [3] REJECT ORDER — 2 TCs: Từ chối đơn (pending / confirmed)
    // ══════════════════════════════════════════════════════════════════════════════

    @DataProvider(name = "reject")
    public Object[][] reject() { return FixtureLoader.toDataProvider("reject-order"); }

    @Test(dataProvider = "reject", description = "[3] Reject Order")
    @SuppressWarnings("unchecked")
    public void suite3_Reject(Map<String, Object> tc) throws InterruptedException {
        int status = E2eRunner.runRejectOrder(token, tc);
        E2eRunner.assertStatus((String) tc.get("id"), status, (Map<String, Object>) tc.get("expect"));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // [4] DRIVER INFO — 6 TCs: Cập nhật tài xế (validate)
    // ══════════════════════════════════════════════════════════════════════════════

    @DataProvider(name = "driver")
    public Object[][] driver() { return FixtureLoader.toDataProvider("driver-info"); }

    @Test(dataProvider = "driver", description = "[4] Driver Info")
    @SuppressWarnings("unchecked")
    public void suite4_Driver(Map<String, Object> tc) throws InterruptedException {
        int status = E2eRunner.runDriverInfo(token, tc);
        E2eRunner.assertStatus((String) tc.get("id"), status, (Map<String, Object>) tc.get("expect"));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // [5] DELIVERY FLOW — 3 TCs: Luồng giao hàng
    // ══════════════════════════════════════════════════════════════════════════════

    @Test(description = "[5] Delivery Flow — Chưa confirm → DRIVER_ASSIGNED bị chặn")
    public void delivery_GuardAssigned() throws InterruptedException {
        Assert.assertNotEquals(
                E2eRunner.runDeliveryGuard(OrderStatusPayload.PartnerOrderStatus.DRIVER_ASSIGNED), 200);
    }

    @Test(description = "[5] Delivery Flow — Chưa confirm → DELIVERED bị chặn")
    public void delivery_GuardDelivered() throws InterruptedException {
        Assert.assertNotEquals(
                E2eRunner.runDeliveryGuard(OrderStatusPayload.PartnerOrderStatus.DELIVERED), 200);
    }

    @Test(description = "[5] Delivery Flow — Full flow → DELIVERED thành công")
    public void delivery_FullFlow() throws InterruptedException {
        Assert.assertEquals(E2eRunner.runDeliveryFullFlow(token), 200);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // [6] CANCEL FLOW — 18 TCs: Hủy đơn từ mọi trạng thái
    // ══════════════════════════════════════════════════════════════════════════════

    @DataProvider(name = "cancel")
    public Object[][] cancel() { return FixtureLoader.toDataProvider("cancel-flow"); }

    @Test(dataProvider = "cancel", description = "[6] Cancel Flow")
    public void suite6_Cancel(Map<String, Object> tc) throws InterruptedException {
        Assert.assertEquals(E2eRunner.runCancelFlow(token, tc), 200,
                tc.get("moveState") + "→" + tc.get("cancelState") + " thất bại");
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // [8] BADGE — 8 TCs: Kiểm tra badge pending_confirm
    // ══════════════════════════════════════════════════════════════════════════════

    @Test(description = "[8] Badge — Push 1 → +1")
    public void badge01() throws InterruptedException {
        int before = E2eRunner.getPendingCount(token);
        E2eRunner.pushBadgeOrder("B01");
        Assert.assertEquals(E2eRunner.waitPending(token, before + 1, 20000), before + 1);
    }

    @Test(description = "[8] Badge — Push 20 → +20")
    public void badge02() throws InterruptedException {
        int before = E2eRunner.getPendingCount(token);
        E2eRunner.pushBadgeOrders(20, "B02");
        Assert.assertEquals(E2eRunner.waitPending(token, before + 20, 30000), before + 20);
    }

    @Test(description = "[8] Badge — Confirm 1 → -1")
    public void badge03() throws InterruptedException {
        String code = E2eRunner.pushBadgeOrder("B03");
        Thread.sleep(2000);
        int before = E2eRunner.getPendingCount(token);
        E2eRunner.confirmAll(token, E2eRunner.findIntegrationIds(token, List.of(code)));
        Assert.assertEquals(E2eRunner.waitPending(token, before - 1, 20000), before - 1);
    }

    @Test(description = "[8] Badge — Confirm 10 → -10")
    public void badge04() throws InterruptedException {
        List<String> codes = E2eRunner.pushBadgeOrders(10, "B04");
        List<String> ids = E2eRunner.findIntegrationIds(token, codes);
        int before = E2eRunner.getPendingCount(token);
        E2eRunner.confirmAll(token, ids);
        Assert.assertEquals(E2eRunner.waitPending(token, before - 10, 30000), before - 10);
    }

    @Test(description = "[8] Badge — Reject 1 → -1")
    public void badge05() throws InterruptedException {
        String code = E2eRunner.pushBadgeOrder("B05");
        List<String> ids = E2eRunner.findIntegrationIds(token, List.of(code));
        int before = E2eRunner.getPendingCount(token);
        E2eRunner.rejectAll(token, ids, "Badge reject");
        Assert.assertEquals(E2eRunner.waitPending(token, before - 1, 20000), before - 1);
    }

    @Test(description = "[8] Badge — Reject 10 → -10")
    public void badge06() throws InterruptedException {
        List<String> codes = E2eRunner.pushBadgeOrders(10, "B06");
        List<String> ids = E2eRunner.findIntegrationIds(token, codes);
        int before = E2eRunner.getPendingCount(token);
        E2eRunner.rejectAll(token, ids, "Badge reject 10");
        Assert.assertEquals(E2eRunner.waitPending(token, before - 10, 30000), before - 10);
    }

    @Test(description = "[8] Badge — Push 1 → +1 → Hủy → -1")
    public void badge07() throws InterruptedException {
        int before = E2eRunner.getPendingCount(token);
        List<String> codes = List.of(E2eRunner.pushBadgeOrder("B07"));
        Assert.assertEquals(E2eRunner.waitPending(token, before + 1, 20000), before + 1);
        E2eRunner.cancelAllByPartner(codes);
        Assert.assertEquals(E2eRunner.waitPending(token, before, 30000), before);
    }

    @Test(description = "[8] Badge — Push 10 → +10 → Hủy 10 → -10")
    public void badge08() throws InterruptedException {
        int before = E2eRunner.getPendingCount(token);
        List<String> codes = E2eRunner.pushBadgeOrders(10, "B08");
        Assert.assertEquals(E2eRunner.waitPending(token, before + 10, 30000), before + 10);
        E2eRunner.cancelAllByPartner(codes);
        Assert.assertEquals(E2eRunner.waitPending(token, before, 30000), before);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // [9] COMPLETION GUARD — 1 TC: Chặn hoàn thành khi thiếu tài xế
    // ══════════════════════════════════════════════════════════════════════════════

    @Test(description = "[9] Guard — Chặn complete khi CHƯA có tài xế")
    public void guard01_NoDriver() throws InterruptedException {
        int status = E2eRunner.runCompletionGuard(token);
        Assert.assertTrue(status == 400 || status == 422 || status == 403, "Phải bị chặn (40x/422), got " + status);
    }

    @Test(description = "[9] Guard — Chặn complete khi tài xế mới được ASSIGNED")
    public void guard02_Assigned() throws InterruptedException {
        int status = E2eRunner.runCompletionGuardWithStatus(token, OrderStatusPayload.PartnerOrderStatus.DRIVER_ASSIGNED);
        Assert.assertTrue(status == 400 || status == 422 || status == 403, "Phải bị chặn, got " + status);
    }

    @Test(description = "[9] Guard — Chặn complete khi tài xế đang PICKING")
    public void guard03_Picking() throws InterruptedException {
        int status = E2eRunner.runCompletionGuardWithStatus(token, OrderStatusPayload.PartnerOrderStatus.DRIVER_PICKING);
        Assert.assertTrue(status == 400 || status == 422 || status == 403, "Phải bị chặn, got " + status);
    }

    @Test(description = "[9] Guard — CHO PHÉP complete khi tài xế đã ARRIVED")
    public void guard04_Arrived() throws InterruptedException {
        int status = E2eRunner.runCompletionGuardWithStatus(token, OrderStatusPayload.PartnerOrderStatus.DRIVER_ARRIVED);
        Assert.assertTrue(status == 200 || status == 201, "Phải thành công (200/201), got " + status);
    }
}
