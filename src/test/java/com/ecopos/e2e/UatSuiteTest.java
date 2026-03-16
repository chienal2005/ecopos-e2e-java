package com.ecopos.e2e;

import com.ecopos.api.*;
import com.ecopos.common.GlobalConstants;
import com.ecopos.common.TelegramReporter;
import com.ecopos.data.DriverPayload;
import com.ecopos.data.OrderStatusPayload;
import com.ecopos.helpers.OrderFlow;
import org.testng.Assert;
import org.testng.ITestResult;
import org.testng.annotations.*;

import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════════════
 *  UatSuiteTest — Bộ kiểm thử UAT kịch bản đầu cuối (End-to-End)
 * ═══════════════════════════════════════════════════════════════════════
 *
 *  5 kịch bản UAT:
 *    E2E-01  Happy Case - Luồng chuẩn (giao thành công)
 *    E2E-02  Tài xế từ chối - Reassign tài xế #2
 *    E2E-03  Tài xế hủy đơn sau bàn giao - Reassign tài xế #2
 *    E2E-04  Từ chối do thiếu tồn kho (số lượng > 9999)
 *    E2E-05  POS chủ động từ chối đơn
 *
 *  Chạy: mvn test -Dtest=UatSuiteTest  hoặc  mvn test -DAPP_ENV=uat -Dtest=UatSuiteTest
 * ═══════════════════════════════════════════════════════════════════════
 */
public class UatSuiteTest {

    private String token;
    private final List<TelegramReporter.TestRow> results = Collections.synchronizedList(new ArrayList<>());
    private long startTime;

    private static final String SITE_ID = GlobalConstants.SITE_ID;

    // ── Setup / Teardown ─────────────────────────────────────────────────────────

    @BeforeClass
    public void setup() {
        startTime = System.currentTimeMillis();
        token = OrderFlow.login();
        System.out.println("✅ UAT Suite — Đăng nhập OK. Token: " + token.substring(0, 20) + "...");
    }

    @AfterMethod
    public void gatherResult(ITestResult r) {
        String name  = r.getMethod().getMethodName();
        String desc  = r.getMethod().getDescription();
        String suite = desc != null ? desc.replaceAll("\\s*—.*", "") : name;
        String status = switch (r.getStatus()) {
            case ITestResult.SUCCESS -> "PASSED";
            case ITestResult.FAILURE -> "FAILED";
            default                  -> "SKIPPED";
        };
        String error = r.getThrowable() != null ? r.getThrowable().getMessage() : null;
        results.add(new TelegramReporter.TestRow(
                suite, name, desc != null ? desc : name,
                status, r.getEndMillis() - r.getStartMillis(), error));
    }

    @AfterClass
    public void sendTelegram() {
        double sec = (System.currentTimeMillis() - startTime) / 1000.0;
        System.out.printf("%n═══ UAT TỔNG KẾT: %d tests | %.1fs ═══%n%n", results.size(), sec);
        TelegramReporter.sendReport(results, sec);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // E2E-01 — Happy Case: Luồng chuẩn hoàn chỉnh
    // ══════════════════════════════════════════════════════════════════════════════
    /**
     * Kịch bản:
     *  1. POS  → Push đơn Online                     → Chờ xác nhận
     *  2. TP   → Xác nhận đơn Online                 → Đơn nháp
     *  3. AHM  → Gán tài xế #1                       → Hiển thị thông tin tài xế
     *  4. AHM  → DRIVER_ASSIGNED                     → Đang điều phối tài xế
     *  5. AHM  → DRIVER_PICKING                      → Đang điều phối tài xế
     *  6. AHM  → DRIVER_ARRIVED                      → Đang điều phối tài xế
     *  7. POS  → Hoàn thành đơn (bàn giao tài xế)   → Đã bàn giao
     *  8. AHM  → DRIVER_DELIVERING                   → Đang vận chuyển
     *  9. AHM  → DELIVERED                           → COMPLETED
     */
    @Test(description = "E2E-01 — Happy Case - Luồng chuẩn hoàn chỉnh đến COMPLETED")
    public void e2e01_HappyCase() throws InterruptedException {
        System.out.println("\n══════════ E2E-01: Happy Case ══════════");

        // Bước 1-2: Push + Confirm
        OrderFlow.PushAndConfirmResult confirmed = OrderFlow.pushAndConfirm(token);
        String code = confirmed.tpOrderCode();
        System.out.printf("[E2E-01] Push + Confirm OK → code: %s%n", code);

        // Bước 3: Gán tài xế #1 (update driver info)
        int driverStatus = OrderDriverApi.updateDriver(DriverPayload.buildDriverInfoPayload(code)).status();
        Assert.assertTrue(driverStatus == 200 || driverStatus == 201,
                "E2E-01 Bước 3: Gán tài xế thất bại, got " + driverStatus);
        System.out.println("[E2E-01] Bước 3 — Gán tài xế #1 OK");

        // Bước 4: DRIVER_ASSIGNED
        int assignedStatus = OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_ASSIGNED).status();
        Assert.assertEquals(assignedStatus, 200, "E2E-01 Bước 4: DRIVER_ASSIGNED thất bại");
        System.out.println("[E2E-01] Bước 4 — DRIVER_ASSIGNED OK");

        // Bước 5: DRIVER_PICKING
        int pickingStatus = OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_PICKING).status();
        Assert.assertEquals(pickingStatus, 200, "E2E-01 Bước 5: DRIVER_PICKING thất bại");
        System.out.println("[E2E-01] Bước 5 — DRIVER_PICKING OK");

        // Bước 6: DRIVER_ARRIVED
        int arrivedStatus = OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_ARRIVED).status();
        Assert.assertEquals(arrivedStatus, 200, "E2E-01 Bước 6: DRIVER_ARRIVED thất bại");
        System.out.println("[E2E-01] Bước 6 — DRIVER_ARRIVED OK");

        // Bước 7: POS hoàn thành đơn (bàn giao tài xế) — skipDriverPrep=true vì đã gán tài xế ở trên
        OrderFlow.completeEcoposOrderFlow(token, code, SITE_ID, true);
        System.out.println("[E2E-01] Bước 7 — Hoàn thành đơn ECOPOS (Đã bàn giao) OK");

        // Bước 8: DRIVER_DELIVERING
        int deliveringStatus = OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_DELIVERING).status();
        Assert.assertEquals(deliveringStatus, 200, "E2E-01 Bước 8: DRIVER_DELIVERING thất bại");
        System.out.println("[E2E-01] Bước 8 — DRIVER_DELIVERING OK");

        // Bước 9: DELIVERED → COMPLETED
        int deliveredStatus = OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DELIVERED).status();
        Assert.assertEquals(deliveredStatus, 200, "E2E-01 Bước 9: DELIVERED thất bại");
        System.out.println("[E2E-01] Bước 9 — DELIVERED → COMPLETED ✅");
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // E2E-02 — Tài xế từ chối: Reassign tài xế #2
    // ══════════════════════════════════════════════════════════════════════════════
    /**
     * Kịch bản:
     *  1. POS  → Push đơn Online                          → Chờ xác nhận
     *  2. TP   → Xác nhận đơn Online                      → Đơn nháp
     *  3. AHM  → Gán tài xế #1                            → Hiển thị tài xế
     *  4. AHM  → Tài xế #1 không nhận → Reassign tài xế #2 → Gán tài xế #2 thành công
     *  5. AHM  → DRIVER_ASSIGNED (tài xế #2)             → Đang điều phối
     *  6. AHM  → DRIVER_PICKING                           → Đang điều phối
     *  7. AHM  → DRIVER_ARRIVED                           → Đang điều phối
     *  8. POS  → Hoàn thành đơn                          → Đã bàn giao
     *  9. AHM  → DRIVER_DELIVERING                        → Đang vận chuyển
     * 10. AHM  → DELIVERED                                → COMPLETED
     */
    @Test(description = "E2E-02 — Tài xế từ chối - Reassign tài xế #2 rồi giao thành công")
    public void e2e02_DriverRefusedReassign() throws InterruptedException {
        System.out.println("\n══════════ E2E-02: Driver Refused → Reassign ══════════");

        // Bước 1-2: Push + Confirm
        OrderFlow.PushAndConfirmResult confirmed = OrderFlow.pushAndConfirm(token);
        String code = confirmed.tpOrderCode();
        System.out.printf("[E2E-02] Push + Confirm OK → code: %s%n", code);

        // Bước 3: Gán tài xế #1
        int driver1Status = OrderDriverApi.updateDriver(DriverPayload.buildDriverInfoPayload(code)).status();
        Assert.assertTrue(driver1Status == 200 || driver1Status == 201,
                "E2E-02 Bước 3: Gán tài xế #1 thất bại, got " + driver1Status);
        System.out.println("[E2E-02] Bước 3 — Gán tài xế #1 OK");

        // Bước 4: Tài xế #1 không nhận → Reassign tài xế #2 (gọi lại updateDriver với info mới)
        // Mô phỏng: AhaMove cập nhật tài xế #2 thay thế tài xế #1
        int driver2Status = OrderDriverApi.updateDriver(DriverPayload.buildDriverInfoPayload(code)).status();
        Assert.assertTrue(driver2Status == 200 || driver2Status == 201,
                "E2E-02 Bước 4: Reassign tài xế #2 thất bại, got " + driver2Status);
        System.out.println("[E2E-02] Bước 4 — Reassign tài xế #2 OK");

        // Bước 5: DRIVER_ASSIGNED (tài xế #2)
        int assignedStatus = OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_ASSIGNED).status();
        Assert.assertEquals(assignedStatus, 200, "E2E-02 Bước 5: DRIVER_ASSIGNED thất bại");
        System.out.println("[E2E-02] Bước 5 — DRIVER_ASSIGNED OK");

        // Bước 6: DRIVER_PICKING
        int pickingStatus = OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_PICKING).status();
        Assert.assertEquals(pickingStatus, 200, "E2E-02 Bước 6: DRIVER_PICKING thất bại");
        System.out.println("[E2E-02] Bước 6 — DRIVER_PICKING OK");

        // Bước 7: DRIVER_ARRIVED
        int arrivedStatus = OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_ARRIVED).status();
        Assert.assertEquals(arrivedStatus, 200, "E2E-02 Bước 7: DRIVER_ARRIVED thất bại");
        System.out.println("[E2E-02] Bước 7 — DRIVER_ARRIVED OK");

        // Bước 8: POS hoàn thành đơn
        OrderFlow.completeEcoposOrderFlow(token, code, SITE_ID, true);
        System.out.println("[E2E-02] Bước 8 — Hoàn thành đơn ECOPOS (Đã bàn giao) OK");

        // Bước 9: DRIVER_DELIVERING
        int deliveringStatus = OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_DELIVERING).status();
        Assert.assertEquals(deliveringStatus, 200, "E2E-02 Bước 9: DRIVER_DELIVERING thất bại");
        System.out.println("[E2E-02] Bước 9 — DRIVER_DELIVERING OK");

        // Bước 10: DELIVERED → COMPLETED
        int deliveredStatus = OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DELIVERED).status();
        Assert.assertEquals(deliveredStatus, 200, "E2E-02 Bước 10: DELIVERED thất bại");
        System.out.println("[E2E-02] Bước 10 — DELIVERED → COMPLETED ✅");
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // E2E-03 — Tài xế hủy sau bàn giao: Reassign tài xế #2
    // ══════════════════════════════════════════════════════════════════════════════
    /**
     * Kịch bản:
     *  1-6 : Giống E2E-01 (Push → Confirm → Driver → ASSIGNED → PICKING → ARRIVED)
     *  7.  POS  → Hoàn thành đơn (POS bàn giao)           → Đã bàn giao
     *  8.  AHM  → DRIVER_DELIVERING                        → Đang vận chuyển
     *  9.  AHM  → DRIVER_CANCELLED (tài xế hủy chuyến)    → Hệ thống tìm tài xế mới
     * 10.  AHM  → Gán tài xế #2                           → Update thông tin tài xế mới
     * 11.  AHM  → DRIVER_ASSIGNED                         → Đang điều phối
     * 12.  AHM  → DRIVER_PICKING                          → Đang điều phối
     * 13.  AHM  → DRIVER_ARRIVED                          → Đang điều phối
     * 14.  POS  → Hoàn thành đơn lần 2                    → Đã bàn giao
     * 15.  AHM  → DRIVER_DELIVERING                        → Đang vận chuyển
     * 16.  AHM  → DELIVERED                               → COMPLETED
     */
    @Test(description = "E2E-03 — Tài xế hủy đơn sau bàn giao - Reassign tài xế #2 giao thành công")
    public void e2e03_DriverCancelledReassign() throws InterruptedException {
        System.out.println("\n══════════ E2E-03: Driver Cancelled → Reassign ══════════");

        // Bước 1-2: Push + Confirm
        OrderFlow.PushAndConfirmResult confirmed = OrderFlow.pushAndConfirm(token);
        String code = confirmed.tpOrderCode();
        System.out.printf("[E2E-03] Push + Confirm OK → code: %s%n", code);

        // Bước 3: Gán tài xế #1
        int driver1Status = OrderDriverApi.updateDriver(DriverPayload.buildDriverInfoPayload(code)).status();
        Assert.assertTrue(driver1Status == 200 || driver1Status == 201,
                "E2E-03 Bước 3: Gán tài xế #1 thất bại, got " + driver1Status);
        System.out.println("[E2E-03] Bước 3 — Gán tài xế #1 OK");

        // Bước 4: DRIVER_ASSIGNED
        Assert.assertEquals(
                OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_ASSIGNED).status(),
                200, "E2E-03 Bước 4: DRIVER_ASSIGNED thất bại");
        System.out.println("[E2E-03] Bước 4 — DRIVER_ASSIGNED OK");

        // Bước 5: DRIVER_PICKING
        Assert.assertEquals(
                OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_PICKING).status(),
                200, "E2E-03 Bước 5: DRIVER_PICKING thất bại");
        System.out.println("[E2E-03] Bước 5 — DRIVER_PICKING OK");

        // Bước 6: DRIVER_ARRIVED
        Assert.assertEquals(
                OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_ARRIVED).status(),
                200, "E2E-03 Bước 6: DRIVER_ARRIVED thất bại");
        System.out.println("[E2E-03] Bước 6 — DRIVER_ARRIVED OK");

        // Bước 7: POS hoàn thành đơn lần đầu (bàn giao tài xế #1)
        OrderFlow.completeEcoposOrderFlow(token, code, SITE_ID, true);
        System.out.println("[E2E-03] Bước 7 — Hoàn thành đơn ECOPOS lần 1 (Đã bàn giao) OK");

        // Bước 8: DRIVER_DELIVERING (tài xế #1 đang giao)
        Assert.assertEquals(
                OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_DELIVERING).status(),
                200, "E2E-03 Bước 8: DRIVER_DELIVERING thất bại");
        System.out.println("[E2E-03] Bước 8 — DRIVER_DELIVERING OK");

        // Bước 9: DRIVER_CANCELLED (tài xế #1 hủy chuyến)
        int cancelledStatus = OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_CANCELLED).status();
        Assert.assertEquals(cancelledStatus, 200, "E2E-03 Bước 9: DRIVER_CANCELLED thất bại");
        System.out.println("[E2E-03] Bước 9 — DRIVER_CANCELLED (tài xế #1 hủy) OK → Hệ thống tìm tài xế mới");

        // Bước 10: Gán tài xế #2
        int driver2Status = OrderDriverApi.updateDriver(DriverPayload.buildDriverInfoPayload(code)).status();
        Assert.assertTrue(driver2Status == 200 || driver2Status == 201,
                "E2E-03 Bước 10: Gán tài xế #2 thất bại, got " + driver2Status);
        System.out.println("[E2E-03] Bước 10 — Gán tài xế #2 OK");

        // Bước 11: DRIVER_ASSIGNED (tài xế #2)
        Assert.assertEquals(
                OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_ASSIGNED).status(),
                200, "E2E-03 Bước 11: DRIVER_ASSIGNED (tài xế #2) thất bại");
        System.out.println("[E2E-03] Bước 11 — DRIVER_ASSIGNED (tài xế #2) OK");

        // Bước 12: DRIVER_PICKING (tài xế #2)
        Assert.assertEquals(
                OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_PICKING).status(),
                200, "E2E-03 Bước 12: DRIVER_PICKING (tài xế #2) thất bại");
        System.out.println("[E2E-03] Bước 12 — DRIVER_PICKING (tài xế #2) OK");

        // Bước 13: DRIVER_ARRIVED (tài xế #2)
        Assert.assertEquals(
                OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_ARRIVED).status(),
                200, "E2E-03 Bước 13: DRIVER_ARRIVED (tài xế #2) thất bại");
        System.out.println("[E2E-03] Bước 13 — DRIVER_ARRIVED (tài xế #2) OK");

        // Bước 14: POS hoàn thành đơn lần 2 (bàn giao tài xế #2)
        OrderFlow.completeEcoposOrderFlow(token, code, SITE_ID, true);
        System.out.println("[E2E-03] Bước 14 — Hoàn thành đơn ECOPOS lần 2 (Đã bàn giao tài xế #2) OK");

        // Bước 15: DRIVER_DELIVERING (tài xế #2 đang giao)
        Assert.assertEquals(
                OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DRIVER_DELIVERING).status(),
                200, "E2E-03 Bước 15: DRIVER_DELIVERING (tài xế #2) thất bại");
        System.out.println("[E2E-03] Bước 15 — DRIVER_DELIVERING (tài xế #2) OK");

        // Bước 16: DELIVERED → COMPLETED
        int deliveredStatus = OrderFlow.moveStatus(code, OrderStatusPayload.PartnerOrderStatus.DELIVERED).status();
        Assert.assertEquals(deliveredStatus, 200, "E2E-03 Bước 16: DELIVERED thất bại");
        System.out.println("[E2E-03] Bước 16 — DELIVERED → COMPLETED ✅");
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // E2E-04 — Từ chối do thiếu tồn kho (số lượng > 9999)
    // ══════════════════════════════════════════════════════════════════════════════
    /**
     * Kịch bản:
     *  1. POS    → Push đơn Online (SP với qty > 9999)  → Hệ thống nhận đơn → Chờ xác nhận
     *  2. TP     → Kiểm tra tồn kho → Kho không đủ     → Thông báo lỗi tồn kho
     *  3. TP     → Không xác nhận đơn                  → Online: Không xác nhận
     *  4. System → Thông báo lỗi "Không đủ tồn kho"   → Đơn bị từ chối
     *
     *  Expect: Confirm API trả về 4xx (422 hoặc 400) — tồn kho không đủ
     */
    @Test(description = "E2E-04 — Từ chối do thiếu tồn kho - Đơn với số lượng > 9999 bị từ chối")
    public void e2e04_RejectInsufficientStock() throws InterruptedException {
        System.out.println("\n══════════ E2E-04: Thiếu tồn kho → Từ chối ══════════");

        // Bước 1: Push đơn với số lượng vượt tồn kho (qty = 10000 > 9999)
        com.ecopos.data.OrderPayload.OrderItem largeQtyItem = com.ecopos.data.OrderPayload.OrderItem.builder()
                .sku("3000305")           // SKU hộp giấy TP (SKU tồn tại trong hệ thống)
                .name("Hộp giấy TP")
                .qty(10000)              // Số lượng > 9999 → vượt tồn kho
                .price(600)
                .build();

        com.ecopos.data.OrderPayload.PartnerOrderPayload payload =
                com.ecopos.data.OrderPayload.buildPushOrderPayload(null, null, List.of(largeQtyItem));

        int pushStatus = com.ecopos.api.OrderApi.pushOrder(payload).status();
        Assert.assertEquals(pushStatus, 201, "E2E-04 Bước 1: Push đơn thất bại, got " + pushStatus);
        System.out.printf("[E2E-04] Bước 1 — Push đơn (qty=10000) OK → code: %s%n", payload.getTp_order_code());

        // Bước 2-3: Tìm integration ID → thử Confirm → phải bị từ chối do thiếu tồn kho
        String intId = OrderFlow.findIntegrationId(token, payload.getTp_order_code(), 5, 1500, SITE_ID);
        int confirmStatus = com.ecopos.api.OrderConfirmApi.confirmOrder(intId, token, SITE_ID).status();

        System.out.printf("[E2E-04] Bước 2-3 — Confirm status: %d%n", confirmStatus);

        // Bước 4: Kiểm tra đơn bị từ chối (4xx) do không đủ tồn kho
        Assert.assertTrue(confirmStatus == 400 || confirmStatus == 422 || confirmStatus == 409,
                "E2E-04 Bước 4: Phải bị từ chối (400/409/422) do thiếu tồn kho, got " + confirmStatus);
        System.out.printf("[E2E-04] Bước 4 — Đơn bị từ chối do thiếu tồn kho ✅ (status=%d)%n", confirmStatus);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // E2E-05 — POS chủ động từ chối đơn
    // ══════════════════════════════════════════════════════════════════════════════
    /**
     * Kịch bản:
     *  1. POS    → Nhận đơn Online                 → Chờ xác nhận
     *  2. POS    → Nhấn Từ chối đơn               → Gửi trạng thái từ chối
     *  3. System → Update trạng thái → Thông báo cho TP → Đơn bị từ chối
     *
     *  Expect: Reject API trả về 200 và đơn ở trạng thái từ chối
     */
    @Test(description = "E2E-05 — POS chủ động từ chối đơn - Reject API trả về 200")
    public void e2e05_PosActiveReject() throws InterruptedException {
        System.out.println("\n══════════ E2E-05: POS chủ động từ chối đơn ══════════");

        // Bước 1: Push đơn Online (đơn ở trạng thái Chờ xác nhận)
        OrderFlow.PushResult push = OrderFlow.pushOrder();
        Assert.assertEquals(push.status(), 201, "E2E-05 Bước 1: Push đơn thất bại, got " + push.status());
        String code = push.tpOrderCode();
        System.out.printf("[E2E-05] Bước 1 — Nhận đơn Online → Chờ xác nhận OK → code: %s%n", code);

        // Tìm integration ID của đơn vừa push
        String intId = OrderFlow.findIntegrationId(token, code, 5, 1500, SITE_ID);
        System.out.printf("[E2E-05] Tìm được integration ID: %s%n", intId);

        // Bước 2-3: POS từ chối đơn
        String rejectReason = "POS chủ động từ chối — UAT E2E-05";
        int rejectStatus = com.ecopos.api.OrderRejectApi.rejectOrder(intId, token, rejectReason, SITE_ID).status();

        System.out.printf("[E2E-05] Bước 2-3 — Reject status: %d%n", rejectStatus);
        Assert.assertEquals(rejectStatus, 200,
                "E2E-05 Bước 3: Từ chối đơn thất bại, got " + rejectStatus);
        System.out.println("[E2E-05] Bước 3 — Đơn bị từ chối thành công ✅");
    }
}
