package com.ecopos.mobile.pages;

import com.ecopos.mobile.AdbHelper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.List;

/**
 * LoginPage — Đăng nhập app EcoPOS trên tablet
 *
 * CÁC TRẠNG THÁI KHI MỞ APP:
 *   1) Trang Login ("Đăng nhập tài khoản")  → login → chọn chi nhánh → xác nhận
 *   2) Trang chọn chi nhánh ("Chọn cửa hàng") → chọn chi nhánh → xác nhận
 *   3) Dashboard (đã login)                   → skip
 *
 * POPUP GỢI Ý TÀI KHOẢN:
 *   - Khi nhập email → tablet hiện popup gợi ý overlay che mất password field
 *   - FIX: Sau nhập email → pressBack() (đóng keyboard+popup) → đợi → tap password field
 *   - pressBack() khi keyboard đang mở: chỉ đóng keyboard, KHÔNG close app
 */
public class LoginPage {

    private static final int MAX_LOGIN_RETRIES = 3;

    public static void login(String email, String password, String branchName) throws InterruptedException {
        System.out.println("═══ LOGIN PAGE ═══");

        // Mở app (auto force landscape)
        AdbHelper.launchEcoposApp();
        Thread.sleep(3000);

        Document doc = AdbHelper.dumpUI("login_check");

        // ── Detect state ────────────────────────────────────────────────
        if (isAtDashboard(doc)) {
            System.out.println("[Login] ✅ Đã ở dashboard, skip login");
            return;
        }

        if (isAtBranchSelection(doc)) {
            System.out.println("[Login] 📋 Ở trang chọn chi nhánh, skip login");
            selectBranchAndConfirm(doc, branchName);
            return;
        }

        // ── Thực hiện login ─────────────────────────────────────────────
        if (!isAtLoginPage(doc)) {
            System.out.println("[Login] ⚠ Không nhận dạng state, debug:");
            debugUI(doc);
            return;
        }

        System.out.println("[Login] 🔐 Ở trang đăng nhập");

        // Retry login nếu cần
        for (int attempt = 1; attempt <= MAX_LOGIN_RETRIES; attempt++) {
            System.out.println("[Login] --- Attempt " + attempt + "/" + MAX_LOGIN_RETRIES + " ---");

            doc = doLogin(doc, email, password);

            // Đợi kết quả login
            Thread.sleep(5000);
            doc = AdbHelper.dumpUI("after_login_attempt_" + attempt);

            if (isAtBranchSelection(doc)) {
                System.out.println("[Login] ✅ Login thành công → chọn chi nhánh");
                selectBranchAndConfirm(doc, branchName);
                return;
            }
            if (isAtDashboard(doc)) {
                System.out.println("[Login] ✅ Login thành công → dashboard!");
                return;
            }

            // Vẫn ở login page → check lỗi
            Element error = AdbHelper.findByAttributeContains(doc, "content-desc", "Vui lòng");
            if (error != null) {
                System.out.println("[Login] ❌ Lỗi: " + error.getAttribute("content-desc"));
            }
            error = AdbHelper.findByAttributeContains(doc, "content-desc", "không đúng");
            if (error != null) {
                System.out.println("[Login] ❌ Lỗi: " + error.getAttribute("content-desc"));
            }

            if (attempt < MAX_LOGIN_RETRIES) {
                System.out.println("[Login] Thử lại...");
                Thread.sleep(2000);
            }
        }

        System.out.println("[Login] ❌ Login thất bại sau " + MAX_LOGIN_RETRIES + " lần thử");
        debugUI(doc);
    }

    // ═════════════════════════════════════════════════════════════════════
    // doLogin — Nhập email + password + tap Đăng nhập
    // ═════════════════════════════════════════════════════════════════════
    private static Document doLogin(Document doc, String email, String password) throws InterruptedException {
        // ── 1. Nhập email ───────────────────────────────────────────────
        Element emailField = findEmailField(doc);
        if (emailField == null) {
            System.out.println("[Login] ⚠ Không tìm thấy email field!");
            debugUI(doc);
            return doc;
        }

        AdbHelper.tapElement(emailField);
        Thread.sleep(500);
        AdbHelper.clearField();
        AdbHelper.type(email);
        System.out.println("[Login] ✅ Nhập email: " + email);
        Thread.sleep(500);

        // ── 2. Dismiss popup gợi ý ─────────────────────────────────────
        // QUAN TRỌNG: Sau nhập email → keyboard đang mở + popup gợi ý che password
        // pressBack() khi keyboard mở → đóng keyboard VÀ popup (KHÔNG close app)
        System.out.println("[Login] ⏎ pressBack để dismiss keyboard + popup gợi ý...");
        AdbHelper.pressBack();
        Thread.sleep(1000);

        // ── 3. Nhập password ────────────────────────────────────────────
        // Bây giờ keyboard đã đóng, popup đã biến → tap password field sạch
        doc = AdbHelper.dumpUI("ready_for_password");

        // Kiểm tra: nếu pressBack đã close app → quay lại
        if (!isAtLoginPage(doc)) {
            System.out.println("[Login] ⚠ pressBack có thể đã đóng app, mở lại...");
            AdbHelper.launchEcoposApp();
            Thread.sleep(3000);
            doc = AdbHelper.dumpUI("reopen_after_back");
            if (!isAtLoginPage(doc)) {
                return doc; // Có thể đã ở dashboard hoặc branch selection
            }
            // Nhập lại email
            emailField = findEmailField(doc);
            if (emailField != null) {
                AdbHelper.tapElement(emailField);
                Thread.sleep(300);
                AdbHelper.clearField();
                AdbHelper.type(email);
                Thread.sleep(300);
                AdbHelper.pressBack(); // dismiss popup lần 2
                Thread.sleep(1000);
                doc = AdbHelper.dumpUI("ready_for_password_2");
            }
        }

        Element passwordField = findPasswordField(doc);
        if (passwordField != null) {
            System.out.println("[Login] Found password field: " + passwordField.getAttribute("bounds"));
            AdbHelper.tapElement(passwordField);
            Thread.sleep(500);

            // Verify password field đã focus
            doc = AdbHelper.dumpUI("password_focused");
            Element focusedPw = findPasswordField(doc);
            if (focusedPw != null && "true".equals(focusedPw.getAttribute("focused"))) {
                System.out.println("[Login] ✅ Password field focused!");
            } else {
                // Tap lại
                System.out.println("[Login] Password field chưa focus, tap lại...");
                if (focusedPw != null) {
                    AdbHelper.tapElement(focusedPw);
                    Thread.sleep(500);
                }
            }

            AdbHelper.clearField();
            AdbHelper.type(password);
            System.out.println("[Login] ✅ Nhập password");
            Thread.sleep(500);
        } else {
            System.out.println("[Login] ⚠ Không tìm thấy password field! Debug:");
            debugUI(doc);
            return doc;
        }

        // ── 4. Tap nút Đăng nhập ───────────────────────────────────────
        doc = AdbHelper.dumpUI("before_login_tap");

        // Tìm nút "Đăng nhập" (content-desc chính xác, không phải "Đăng nhập tài khoản")
        Element loginBtn = findLoginButton(doc);
        if (loginBtn != null) {
            AdbHelper.tapElement(loginBtn);
            System.out.println("[Login] ✅ Tap nút Đăng nhập");
        } else {
            System.out.println("[Login] ⚠ Không tìm thấy nút Đăng nhập, debug:");
            debugUI(doc);
        }

        return doc;
    }

    // ═════════════════════════════════════════════════════════════════════
    // selectBranchAndConfirm — Chọn chi nhánh + Xác nhận
    // ═════════════════════════════════════════════════════════════════════
    private static void selectBranchAndConfirm(Document doc, String branchName) throws InterruptedException {
        System.out.println("[Branch] Danh sách chi nhánh:");
        debugUI(doc);

        // Chọn chi nhánh
        boolean selected = false;
        if (branchName != null && !branchName.isEmpty()) {
            Element branch = AdbHelper.findByAttributeContains(doc, "content-desc", branchName);
            if (branch != null) {
                AdbHelper.tapElement(branch);
                System.out.println("[Branch] ✅ Chọn: " + branchName);
                selected = true;
                Thread.sleep(1000);
            } else {
                System.out.println("[Branch] ⚠ Không tìm thấy '" + branchName + "'");
            }
        }

        if (!selected) {
            // Chọn chi nhánh đầu tiên trong list
            System.out.println("[Branch] Chọn chi nhánh đầu tiên...");
            AdbHelper.tap(1965, 255);
            Thread.sleep(1000);
        }

        // Tap "Xác nhận"
        doc = AdbHelper.dumpUI("before_confirm");
        Element confirmBtn = AdbHelper.findByAttribute(doc, "content-desc", "Xác nhận");
        if (confirmBtn == null) {
            confirmBtn = AdbHelper.findByAttributeContains(doc, "content-desc", "Xác nhận");
        }
        if (confirmBtn != null) {
            AdbHelper.tapElement(confirmBtn);
            System.out.println("[Branch] ✅ Tap Xác nhận");
        } else {
            System.out.println("[Branch] ⚠ Fallback tap Xác nhận");
            AdbHelper.tap(1965, 1452);
        }

        // Đợi dashboard load
        Thread.sleep(5000);

        doc = AdbHelper.dumpUI("after_confirm");

        if (isAtDashboard(doc)) {
            System.out.println("[Branch] ✅ Vào dashboard thành công!");
        } else {
            System.out.println("[Branch] ⚠ Chưa ở dashboard:");
            debugUI(doc);
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // State Detection
    // ═════════════════════════════════════════════════════════════════════
    private static boolean isAtLoginPage(Document doc) {
        return AdbHelper.findByAttributeContains(doc, "content-desc", "Đăng nhập tài khoản") != null;
    }

    private static boolean isAtBranchSelection(Document doc) {
        return AdbHelper.findByAttributeContains(doc, "content-desc", "Chọn cửa hàng") != null;
    }

    private static boolean isAtDashboard(Document doc) {
        String[] keywords = {
            "Điểm bán hàng", "Đơn Online", "Đơn online",
            "Đơn hàng", "Danh sách đơn online", "Trang chủ"
        };
        for (String kw : keywords) {
            if (AdbHelper.findByTextContains(doc, kw) != null) return true;
            if (AdbHelper.findByAttributeContains(doc, "content-desc", kw) != null) return true;
        }
        return false;
    }

    // ═════════════════════════════════════════════════════════════════════
    // Find Elements
    // ═════════════════════════════════════════════════════════════════════
    private static Element findEmailField(Document doc) {
        List<Element> editTexts = AdbHelper.findAllByAttribute(doc, "class", "android.widget.EditText");
        for (Element el : editTexts) {
            if ("false".equals(el.getAttribute("password"))) {
                return el;
            }
        }
        if (!editTexts.isEmpty()) return editTexts.get(0);
        return null;
    }

    private static Element findPasswordField(Document doc) {
        List<Element> editTexts = AdbHelper.findAllByAttribute(doc, "class", "android.widget.EditText");
        for (Element el : editTexts) {
            if ("true".equals(el.getAttribute("password"))) {
                return el;
            }
        }
        if (editTexts.size() >= 2) return editTexts.get(1);
        return null;
    }

    /** Tìm nút Đăng nhập (chính xác, không phải "Đăng nhập tài khoản") */
    private static Element findLoginButton(Document doc) {
        // Tìm tất cả element có content-desc chứa "Đăng nhập"
        List<Element> candidates = AdbHelper.findAllByAttribute(doc, "content-desc", "Đăng nhập");
        // Trả về element có content-desc CHÍNH XÁC = "Đăng nhập" (không phải "Đăng nhập tài khoản")
        for (Element el : candidates) {
            if ("Đăng nhập".equals(el.getAttribute("content-desc")) &&
                "true".equals(el.getAttribute("clickable"))) {
                return el;
            }
        }
        // Fallback: tìm contains nhưng loại "tài khoản"
        Element el = AdbHelper.findByAttributeContains(doc, "content-desc", "Đăng nhập");
        if (el != null && !el.getAttribute("content-desc").contains("tài khoản")) {
            return el;
        }
        return null;
    }

    // ═════════════════════════════════════════════════════════════════════
    // Debug
    // ═════════════════════════════════════════════════════════════════════
    private static void debugUI(Document doc) {
        AdbHelper.debugListAllText(doc);
        System.out.println("══════ CONTENT-DESC ══════");
        listDescRecursive(doc.getDocumentElement(), 0);
        System.out.println("══════════════════════════");
    }

    private static void listDescRecursive(org.w3c.dom.Node node, int depth) {
        if (node instanceof Element el) {
            String desc = el.getAttribute("content-desc");
            if (desc != null && !desc.isEmpty()) {
                System.out.printf("%sdesc='%s' bounds=%s%n", "  ".repeat(Math.min(depth, 10)), desc, el.getAttribute("bounds"));
            }
        }
        org.w3c.dom.NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            listDescRecursive(children.item(i), depth + 1);
        }
    }
}
