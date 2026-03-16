package com.ecopos.mobile.pages;

import com.ecopos.mobile.AdbHelper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.*;

/**
 * OnlineOrderPage — Page Object cho màn hình Đơn Online trên app mobile EcoPOS
 *
 * LƯU Ý QUAN TRỌNG về DOM:
 * - App EcoPOS dùng Flutter → text hiển thị trong content-desc (KHÔNG phải text attribute)
 * - Cần tìm element bằng cả text VÀ content-desc
 *
 * Chức năng:
 * - Navigate tới tab Đơn Online
 * - Refresh danh sách đơn
 * - Tìm đơn hàng theo mã
 * - Verify thông tin đơn trên danh sách (bên trái)
 * - Click vào đơn → verify chi tiết (bên phải)
 */
public class OnlineOrderPage {

    // ═════════════════════════════════════════════════════════════════════
    // Navigation
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Navigate tới tab Đơn Online:
     * 1. Mở sidebar (menu hamburger)
     * 2. Tap "Đơn Online"
     */
    public static Document navigateToOnlineOrders() throws InterruptedException {
        System.out.println("═══ NAVIGATE TO ĐƠN ONLINE ═══");

        Document doc = AdbHelper.dumpUI("nav_start");

        // Kiểm tra đã ở trang Đơn Online chưa (content-desc hoặc text)
        if (findAny(doc, "Đơn online") != null || findAny(doc, "Danh sách đơn online") != null) {
            System.out.println("[Nav] ✅ Đã ở trang Đơn Online");
            return doc;
        }

        // Mở sidebar: tìm hamburger menu bằng content-desc
        Element menuBtn = AdbHelper.findByContentDesc(doc, "Open navigation menu");
        if (menuBtn == null) menuBtn = AdbHelper.findByContentDesc(doc, "Open drawer");
        if (menuBtn == null) menuBtn = AdbHelper.findByContentDesc(doc, "Menu");
        if (menuBtn == null) {
            // Tap vào biểu tượng ☰ ở góc trái trên (Flutter tab bar)
            System.out.println("[Nav] Tap hamburger menu icon...");
            AdbHelper.tap(50, 48);
        } else {
            AdbHelper.tapElement(menuBtn);
        }
        Thread.sleep(2000);

        // Dump UI sau khi mở sidebar
        doc = AdbHelper.dumpUI("sidebar_open");
        AdbHelper.debugListAllText(doc);
        debugListContentDesc(doc);

        // Tap "Đơn Online" — tìm bằng cả text và content-desc
        Element onlineBtn = findAny(doc, "Đơn Online");
        if (onlineBtn == null) onlineBtn = findAny(doc, "Đơn online");
        if (onlineBtn != null) {
            AdbHelper.tapElement(onlineBtn);
            System.out.println("[Nav] ✅ Đã tap Đơn Online");
        } else {
            System.out.println("[Nav] ⚠ Không tìm thấy 'Đơn Online' trong sidebar");
        }

        Thread.sleep(3000);

        // Verify đã vào trang Đơn Online
        doc = AdbHelper.dumpUI("online_orders_page");
        if (findAny(doc, "Đơn online") != null || findAny(doc, "Danh sách đơn online") != null ||
                findAny(doc, "đơn online") != null) {
            System.out.println("[Nav] ✅ Đã vào trang Đơn Online");
        } else {
            System.out.println("[Nav] ⚠ Chưa xác nhận ở trang Đơn Online, debug:");
            AdbHelper.debugListAllText(doc);
            debugListContentDesc(doc);
        }

        return doc;
    }

    // ═════════════════════════════════════════════════════════════════════
    // Refresh
    // ═════════════════════════════════════════════════════════════════════

    /** Full refresh trang đơn online (pull-to-refresh) */
    public static Document refreshOrderList() throws InterruptedException {
        System.out.println("[Refresh] Pull to refresh danh sách đơn online...");
        AdbHelper.pullToRefresh();
        Thread.sleep(3000);
        return AdbHelper.dumpUI("after_refresh");
    }

    // ═════════════════════════════════════════════════════════════════════
    // Find Order in List
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Tìm đơn hàng trên danh sách UI theo mã tham chiếu (tp_order_code)
     * Tìm bằng cả text VÀ content-desc
     */
    public static Document findOrderInList(String tpOrderCode, int maxScrolls) throws InterruptedException {
        System.out.printf("[Find] Tìm đơn '%s' trên danh sách...%n", tpOrderCode);

        for (int i = 0; i <= maxScrolls; i++) {
            Document doc = AdbHelper.dumpUI("find_order_" + i);

            // Tìm bằng text hoặc content-desc
            Element el = findAny(doc, tpOrderCode);
            if (el != null) {
                System.out.printf("[Find] ✅ Tìm thấy đơn '%s' (attempt %d)%n", tpOrderCode, i + 1);
                return doc;
            }

            if (i < maxScrolls) {
                System.out.printf("[Find] Chưa thấy, scroll lên tìm... (%d/%d)%n", i + 1, maxScrolls);
                AdbHelper.scrollUp();
                Thread.sleep(1500);
            }
        }

        throw new RuntimeException("Không tìm thấy đơn '" + tpOrderCode + "' trên UI sau " + maxScrolls + " lần scroll");
    }

    // ═════════════════════════════════════════════════════════════════════
    // Verify Order List (Bảng bên trái)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Verify thông tin đơn hàng trên danh sách (bảng bên trái)
     */
    public static Map<String, String> verifyOrderListInfo(Document doc, Map<String, String> expected) {
        Map<String, String> errors = new LinkedHashMap<>();
        System.out.println("═══ VERIFY ĐƠN TRÊN DANH SÁCH (BẢNG TRÁI) ═══");

        verifyFieldAny(doc, "Mã tham chiếu", expected.get("tp_order_code"), errors);
        verifyFieldAny(doc, "Mã đơn bán", expected.get("order_code"), errors);
        verifyFieldAny(doc, "Khách hàng", expected.get("customer_name"), errors);
        verifyFieldAny(doc, "Địa chỉ", expected.get("address"), errors);
        verifyFieldAny(doc, "Trạng thái", expected.get("status"), errors);

        if (expected.containsKey("total")) {
            verifyFieldAny(doc, "Tổng tiền", expected.get("total"), errors);
        }

        if (errors.isEmpty()) {
            System.out.println("[Verify] ✅ Tất cả thông tin trên danh sách đúng!");
        } else {
            System.out.println("[Verify] ❌ Có " + errors.size() + " lỗi:");
            errors.forEach((k, v) -> System.out.printf("  - %s: %s%n", k, v));
        }

        return errors;
    }

    // ═════════════════════════════════════════════════════════════════════
    // Click Order & Verify Detail (Bảng bên phải)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Click vào đơn hàng → verify chi tiết ở panel bên phải
     *
     * LƯU Ý: Nếu panel chi tiết đang mở cho đơn khác:
     *   - Click lần 1: đóng panel cũ (deselect)
     *   - Click lần 2: mở panel chi tiết cho đơn mới
     */
    public static Document clickOrderAndGetDetail(String tpOrderCode) throws InterruptedException {
        System.out.printf("[Detail] Click vào đơn '%s' để xem chi tiết...%n", tpOrderCode);

        Document doc = AdbHelper.dumpUI("before_click_order");
        Element orderRow = findAny(doc, tpOrderCode);
        if (orderRow == null) {
            throw new RuntimeException("Không tìm thấy đơn '" + tpOrderCode + "' để click");
        }

        // Click lần 1
        AdbHelper.tapElement(orderRow);
        Thread.sleep(2000);

        doc = AdbHelper.dumpUI("after_click_1");

        // Kiểm tra panel chi tiết đã hiển thị cho đơn này chưa
        boolean hasDetailPanel = findAny(doc, "Xác nhận") != null || findAny(doc, "Từ chối") != null
                || findAny(doc, "Chuyển khoản") != null || findAny(doc, "Tiền mặt") != null;

        if (!hasDetailPanel) {
            System.out.println("[Detail] Panel chi tiết chưa hiện → click lần 2...");
            // Re-find element vì UI đã thay đổi
            orderRow = findAny(doc, tpOrderCode);
            if (orderRow != null) {
                AdbHelper.tapElement(orderRow);
            } else {
                // Đơn có thể đã ở vị trí khác, tìm lại
                doc = AdbHelper.dumpUI("refind_order");
                orderRow = findAny(doc, tpOrderCode);
                if (orderRow != null) {
                    AdbHelper.tapElement(orderRow);
                }
            }
            Thread.sleep(2000);
            doc = AdbHelper.dumpUI("order_detail");
        } else {
            System.out.println("[Detail] ✅ Panel chi tiết đã hiện");
        }

        return doc;
    }

    /**
     * Verify chi tiết đơn hàng trên panel bên phải
     */
    public static Map<String, String> verifyOrderDetail(Document doc, Map<String, Object> expectedDetail) {
        Map<String, String> errors = new LinkedHashMap<>();
        System.out.println("═══ VERIFY CHI TIẾT ĐƠN (PANEL PHẢI) ═══");

        if (expectedDetail.containsKey("status")) {
            verifyFieldAny(doc, "Trạng thái detail", (String) expectedDetail.get("status"), errors);
        }

        // Đơn đặt hàng (mã đơn đặt DDH...)
        if (expectedDetail.containsKey("order_code_ddh")) {
            String ddhCode = (String) expectedDetail.get("order_code_ddh");
            Element el = findAny(doc, "DDH");
            if (el != null) {
                String actual = getDisplayText(el);
                System.out.printf("[Verify] Đơn đặt hàng: '%s'%n", actual);
                if (ddhCode != null && !actual.contains(ddhCode)) {
                    errors.put("order_code_ddh", "Expected contains '" + ddhCode + "' but got '" + actual + "'");
                }
            } else {
                System.out.println("[Verify] ℹ Không tìm thấy mã DDH trên detail panel");
            }
        }

        // Thông tin khách hàng
        if (expectedDetail.containsKey("customer_info")) {
            String expectedCustomer = (String) expectedDetail.get("customer_info");
            verifyFieldAny(doc, "Thông tin KH", expectedCustomer, errors);
        }

        // Phương thức thanh toán
        if (expectedDetail.containsKey("payment_method")) {
            String expectedPayment = (String) expectedDetail.get("payment_method");
            verifyFieldAny(doc, "PTTT", expectedPayment, errors);
        }

        // Sản phẩm
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> expectedItems = (List<Map<String, Object>>) expectedDetail.get("items");
        if (expectedItems != null) {
            for (Map<String, Object> item : expectedItems) {
                String itemName = (String) item.get("name");
                // Tên SP có thể bị cắt bớt → dùng chỉ 10 ký tự đầu
                String shortName = itemName.length() > 10 ? itemName.substring(0, 10) : itemName;
                Element itemEl = findAny(doc, shortName);
                if (itemEl != null) {
                    System.out.printf("[Verify] ✅ SP '%s' tìm thấy%n", shortName);
                } else {
                    errors.put("item_" + itemName, "Không tìm thấy SP '" + shortName + "'");
                }

                if (item.containsKey("price")) {
                    String priceStr = formatPrice(((Number) item.get("price")).intValue());
                    Element priceEl = findAny(doc, priceStr);
                    if (priceEl != null) {
                        System.out.printf("[Verify] ✅ Giá SP '%s': %s%n", shortName, priceStr);
                    }
                }

                if (item.containsKey("qty")) {
                    String qtyStr = String.valueOf(((Number) item.get("qty")).intValue());
                    Element qtyEl = findAny(doc, qtyStr);
                    if (qtyEl != null) {
                        System.out.printf("[Verify] ✅ SL SP '%s': %s%n", shortName, qtyStr);
                    }
                }

                if (item.containsKey("total")) {
                    String totalStr = formatPrice(((Number) item.get("total")).intValue());
                    Element totalEl = findAny(doc, totalStr);
                    if (totalEl != null) {
                        System.out.printf("[Verify] ✅ Thành tiền SP '%s': %s%n", shortName, totalStr);
                    }
                }
            }
        }

        // Tổng tiền
        if (expectedDetail.containsKey("grand_total")) {
            String grandTotal = formatPrice(((Number) expectedDetail.get("grand_total")).intValue());
            Element totalEl = findAny(doc, grandTotal);
            if (totalEl != null) {
                System.out.printf("[Verify] ✅ Tổng tiền: %s%n", grandTotal);
            } else {
                errors.put("grand_total", "Tổng tiền '" + grandTotal + "' không tìm thấy trên UI");
            }
        }

        if (errors.isEmpty()) {
            System.out.println("[Verify] ✅ Tất cả chi tiết đúng!");
        } else {
            System.out.println("[Verify] ❌ Có " + errors.size() + " lỗi:");
            errors.forEach((k, v) -> System.out.printf("  - %s: %s%n", k, v));
        }

        return errors;
    }

    // ═════════════════════════════════════════════════════════════════════
    // Verify Actions (Buttons)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Verify các nút hành động: Xác nhận / Từ chối
     */
    public static Map<String, String> verifyPendingActions(Document doc) {
        Map<String, String> errors = new LinkedHashMap<>();
        System.out.println("═══ VERIFY ACTIONS (CHỜ XÁC NHẬN) ═══");

        // Kiểm tra trạng thái
        Element statusEl = findAny(doc, "xác nhận");
        if (statusEl == null) statusEl = findAny(doc, "Chờ");
        if (statusEl != null) {
            System.out.printf("[Verify] ✅ Trạng thái: '%s'%n", getDisplayText(statusEl));
        }

        // Button Xác nhận
        Element confirmBtn = findAny(doc, "Xác nhận");
        if (confirmBtn != null) {
            System.out.println("[Verify] ✅ Button 'Xác nhận' hiện diện");
        } else {
            errors.put("confirm_button", "Không tìm thấy button 'Xác nhận'");
        }

        // Button Từ chối
        Element rejectBtn = findAny(doc, "Từ chối");
        if (rejectBtn != null) {
            System.out.println("[Verify] ✅ Button 'Từ chối' hiện diện");
        } else {
            errors.put("reject_button", "Không tìm thấy button 'Từ chối'");
        }

        if (errors.isEmpty()) {
            System.out.println("[Verify] ✅ Các nút hành động đúng!");
        }

        return errors;
    }

    // ═════════════════════════════════════════════════════════════════════
    // Search
    // ═════════════════════════════════════════════════════════════════════

    public static Document searchOrder(String keyword) throws InterruptedException {
        System.out.printf("[Search] Tìm kiếm '%s'%n", keyword);
        Document doc = AdbHelper.dumpUI("before_search");

        Element searchField = findAny(doc, "Tìm kiếm");
        if (searchField == null) searchField = findAny(doc, "tìm kiếm");
        if (searchField == null) searchField = findAny(doc, "Search");

        if (searchField != null) {
            AdbHelper.tapElement(searchField);
            Thread.sleep(500);
            AdbHelper.clearField();
            AdbHelper.type(keyword);
            AdbHelper.pressEnter();
            Thread.sleep(2000);
        }

        return AdbHelper.dumpUI("after_search");
    }

    // ═════════════════════════════════════════════════════════════════════
    // Core Helpers — Tìm bằng cả text VÀ content-desc
    // ═════════════════════════════════════════════════════════════════════

    /** Tìm element bằng text HOẶC content-desc (contains match) */
    public static Element findAny(Document doc, String value) {
        if (value == null || value.isEmpty()) return null;
        // Thử text trước
        Element el = AdbHelper.findByTextContains(doc, value);
        if (el != null) return el;
        // Thử content-desc
        return AdbHelper.findByAttributeContains(doc, "content-desc", value);
    }

    /** Lấy display text từ element (text hoặc content-desc) */
    public static String getDisplayText(Element el) {
        if (el == null) return "";
        String text = el.getAttribute("text");
        if (text != null && !text.isEmpty()) return text;
        String desc = el.getAttribute("content-desc");
        return desc != null ? desc : "";
    }

    /** Verify 1 field: tìm trên UI bằng text/content-desc */
    private static void verifyFieldAny(Document doc, String fieldName, String expectedValue, Map<String, String> errors) {
        if (expectedValue == null || expectedValue.isEmpty()) return;
        Element el = findAny(doc, expectedValue);
        if (el != null) {
            System.out.printf("[Verify] ✅ %s: '%s'%n", fieldName, expectedValue);
        } else {
            errors.put(fieldName, "Không tìm thấy '" + expectedValue + "' trên UI");
            System.out.printf("[Verify] ❌ %s: expected '%s' KHÔNG TÌM THẤY%n", fieldName, expectedValue);
        }
    }

    /** Format giá tiền: 60000 → "60,000" */
    public static String formatPrice(int price) {
        return String.format("%,d", price);
    }

    /** Format giá tiền có đ: 60000 → "60,000đ" */
    public static String formatPriceWithDong(int price) {
        return formatPrice(price) + "đ";
    }

    /** Tính tổng tiền */
    public static int calculateGrandTotal(List<Map<String, Object>> items) {
        int total = 0;
        for (Map<String, Object> item : items) {
            total += ((Number) item.get("price")).intValue() * ((Number) item.get("qty")).intValue();
        }
        return total;
    }

    /** Debug: list tất cả content-desc trên UI */
    static void debugListContentDesc(Document doc) {
        System.out.println("══════ ALL CONTENT-DESC ══════");
        listContentDescRecursive(doc.getDocumentElement(), 0);
        System.out.println("══════════════════════════════");
    }

    private static void listContentDescRecursive(org.w3c.dom.Node node, int depth) {
        if (node instanceof Element el) {
            String desc = el.getAttribute("content-desc");
            if (desc != null && !desc.isEmpty()) {
                String indent = "  ".repeat(Math.min(depth, 10));
                System.out.printf("%sdesc='%s' bounds=%s%n", indent, desc, el.getAttribute("bounds"));
            }
        }
        org.w3c.dom.NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            listContentDescRecursive(children.item(i), depth + 1);
        }
    }
}
