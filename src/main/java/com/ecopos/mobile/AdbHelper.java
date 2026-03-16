package com.ecopos.mobile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * AdbHelper — Core engine cho mobile UI automation
 * 
 * Sử dụng ADB shell commands trực tiếp (không cần Appium):
 * - uiautomator dump → lấy XML hierarchy
 * - input tap / text / swipe → tương tác UI
 * - screencap → screenshot
 * 
 * Tất cả thao tác đều retry-safe và có logging chi tiết.
 */
public class AdbHelper {

    private static final String DUMP_DIR = "mobile-dumps";
    private static final String DEVICE_DUMP_PATH = "/sdcard/window_dump.xml";

    private static final int MAX_DUMP_RETRIES = 3;

    // ─── Execute ADB Command ────────────────────────────────────────────
    /** Chạy lệnh ADB, trả về output */
    public static String exec(String... args) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("adb");
            cmd.addAll(Arrays.asList(args));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                output = sb.toString().trim();
            }
            proc.waitFor();
            return output;
        } catch (Exception e) {
            throw new RuntimeException("ADB command failed: " + String.join(" ", args), e);
        }
    }

    /** Chạy lệnh ADB shell */
    public static String shell(String command) {
        return exec("shell", command);
    }

    // ─── Device Check ───────────────────────────────────────────────────
    /** Kiểm tra thiết bị đã kết nối */
    public static boolean isDeviceConnected() {
        String output = exec("devices");
        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.trim().endsWith("device") && !line.contains("List")) {
                return true;
            }
        }
        return false;
    }

    /** Lấy device serial */
    public static String getDeviceSerial() {
        String output = exec("devices");
        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.endsWith("device") && !line.contains("List")) {
                return line.split("\\s+")[0];
            }
        }
        return null;
    }

    // ─── UI Dump (XML Hierarchy) ────────────────────────────────────────
    /** Dump UI hierarchy ra XML, trả về Document */
    public static Document dumpUI() {
        return dumpUI("ui_dump");
    }

    public static Document dumpUI(String label) {
        // Tạo thư mục dump nếu chưa có
        try { Files.createDirectories(Paths.get(DUMP_DIR)); }
        catch (IOException ignored) {}

        for (int retry = 0; retry < MAX_DUMP_RETRIES; retry++) {
            try {
                // Dump UI hierarchy trên device
                shell("uiautomator dump " + DEVICE_DUMP_PATH);
                Thread.sleep(500);

                // Pull file về local
                String localPath = DUMP_DIR + "/" + label + ".xml";
                exec("pull", DEVICE_DUMP_PATH, localPath);

                // Parse XML
                File xmlFile = new File(localPath);
                if (!xmlFile.exists() || xmlFile.length() == 0) {
                    System.out.printf("[ADB] Dump empty, retry %d/%d%n", retry + 1, MAX_DUMP_RETRIES);
                    Thread.sleep(1000);
                    continue;
                }

                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(xmlFile);
                System.out.printf("[ADB] ✅ UI dump OK: %s (%d bytes)%n", label, xmlFile.length());
                return doc;

            } catch (Exception e) {
                System.out.printf("[ADB] Dump failed (retry %d): %s%n", retry + 1, e.getMessage());
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }
        throw new RuntimeException("UI dump thất bại sau " + MAX_DUMP_RETRIES + " lần thử");
    }

    /** Dump UI trả về XML string */
    public static String dumpUIAsString() {
        shell("uiautomator dump " + DEVICE_DUMP_PATH);
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        return shell("cat " + DEVICE_DUMP_PATH);
    }

    // ─── Find Elements ──────────────────────────────────────────────────
    /** Tìm Element theo text chính xác */
    public static Element findByText(Document doc, String text) {
        return findByAttribute(doc, "text", text);
    }

    /** Tìm Element theo text chứa chuỗi */
    public static Element findByTextContains(Document doc, String text) {
        return findByAttributeContains(doc, "text", text);
    }

    /** Tìm Element theo resource-id */
    public static Element findByResourceId(Document doc, String resourceId) {
        return findByAttribute(doc, "resource-id", resourceId);
    }

    /** Tìm Element theo content-desc */
    public static Element findByContentDesc(Document doc, String desc) {
        return findByAttribute(doc, "content-desc", desc);
    }

    /** Tìm Element theo class */
    public static Element findByClass(Document doc, String className) {
        return findByAttribute(doc, "class", className);
    }

    /** Tìm tất cả Elements theo attribute value */
    public static List<Element> findAllByAttribute(Document doc, String attr, String value) {
        List<Element> results = new ArrayList<>();
        findAllRecursive(doc.getDocumentElement(), attr, value, false, results);
        return results;
    }

    /** Tìm tất cả Elements chứa text */
    public static List<Element> findAllByTextContains(Document doc, String text) {
        List<Element> results = new ArrayList<>();
        findAllRecursive(doc.getDocumentElement(), "text", text, true, results);
        return results;
    }

    /** Tìm element theo attribute match chính xác */
    public static Element findByAttribute(Document doc, String attr, String value) {
        return findRecursive(doc.getDocumentElement(), attr, value, false);
    }

    /** Tìm element theo attribute chứa chuỗi */
    public static Element findByAttributeContains(Document doc, String attr, String value) {
        return findRecursive(doc.getDocumentElement(), attr, value, true);
    }

    // ─── Element Info ───────────────────────────────────────────────────
    /** Lấy bounds [x1,y1][x2,y2] → int[4] */
    public static int[] getBounds(Element el) {
        String bounds = el.getAttribute("bounds");
        if (bounds == null || bounds.isEmpty()) return null;

        // Format: [x1,y1][x2,y2]
        bounds = bounds.replace("][", ",").replace("[", "").replace("]", "");
        String[] parts = bounds.split(",");
        return new int[]{
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3])
        };
    }

    /** Lấy tâm điểm của element */
    public static int[] getCenter(Element el) {
        int[] bounds = getBounds(el);
        if (bounds == null) return null;
        return new int[]{(bounds[0] + bounds[2]) / 2, (bounds[1] + bounds[3]) / 2};
    }

    /** Lấy text của element */
    public static String getText(Element el) {
        return el != null ? el.getAttribute("text") : null;
    }

    // ─── Tap / Click ────────────────────────────────────────────────────
    /** Tap vào tọa độ x, y */
    public static void tap(int x, int y) {
        System.out.printf("[ADB] TAP → (%d, %d)%n", x, y);
        shell("input tap " + x + " " + y);
        sleep(500);
    }

    /** Tap vào center của element */
    public static void tapElement(Element el) {
        int[] center = getCenter(el);
        if (center == null) throw new RuntimeException("Element không có bounds");
        System.out.printf("[ADB] TAP element '%s' → (%d, %d)%n",
                el.getAttribute("text"), center[0], center[1]);
        tap(center[0], center[1]);
    }

    /** Tap vào element có text chứa chuỗi */
    public static boolean tapByText(Document doc, String text) {
        Element el = findByText(doc, text);
        if (el == null) {
            el = findByTextContains(doc, text);
        }
        if (el != null) {
            tapElement(el);
            return true;
        }
        System.out.printf("[ADB] ⚠ Không tìm thấy element text='%s'%n", text);
        return false;
    }

    /** Tap vào element tìm bằng text (dump mới rồi tap) */
    public static boolean freshTapByText(String text) {
        Document doc = dumpUI("tap_" + text.replaceAll("\\s+", "_"));
        return tapByText(doc, text);
    }

    // ─── Type Text ──────────────────────────────────────────────────────
    /** Nhập text (ADB input text — escape các ký tự đặc biệt cho shell) */
    public static void type(String text) {
        String escaped = text.replace(" ", "%s")
                .replace("&", "\\&")
                .replace("<", "\\<").replace(">", "\\>")
                .replace("(", "\\(").replace(")", "\\)")
                .replace("|", "\\|").replace(";", "\\;")
                .replace("'", "\\'").replace("\"", "\\\"")
                .replace("@", "\\@")
                .replace("#", "\\#")
                .replace("$", "\\$")
                .replace("%", "\\%")
                .replace("^", "\\^")
                .replace("!", "\\!")
                .replace("*", "\\*")
                .replace("?", "\\?");
        shell("input text \"" + escaped + "\"");
        sleep(500);
    }

    /** Clear text field (select all → delete) */
    public static void clearField() {
        // Ctrl+A (select all)
        shell("input keyevent KEYCODE_CTRL_LEFT KEYCODE_A");
        sleep(200);
        // Delete
        shell("input keyevent KEYCODE_DEL");
        sleep(200);
    }

    // ─── Key Events ─────────────────────────────────────────────────────
    public static void pressBack() { shell("input keyevent 4"); sleep(500); }
    public static void pressHome() { shell("input keyevent 3"); sleep(500); }
    public static void pressEnter() { shell("input keyevent 66"); sleep(500); }
    public static void pressTab() { shell("input keyevent 61"); sleep(200); }

    // ─── Scroll / Swipe ─────────────────────────────────────────────────
    /** Swipe từ (x1,y1) đến (x2,y2) trong duration(ms) */
    public static void swipe(int x1, int y1, int x2, int y2, int durationMs) {
        shell(String.format("input swipe %d %d %d %d %d", x1, y1, x2, y2, durationMs));
        sleep(500);
    }

    /** Scroll down (kéo từ giữa xuống) */
    public static void scrollDown() {
        // Lấy kích thước màn hình
        String size = shell("wm size");
        String[] parts = size.split(":\\s*")[1].split("x");
        int w = Integer.parseInt(parts[0].trim());
        int h = Integer.parseInt(parts[1].trim());
        swipe(w / 2, h * 3 / 4, w / 2, h / 4, 500);
    }

    /** Scroll up */
    public static void scrollUp() {
        String size = shell("wm size");
        String[] parts = size.split(":\\s*")[1].split("x");
        int w = Integer.parseInt(parts[0].trim());
        int h = Integer.parseInt(parts[1].trim());
        swipe(w / 2, h / 4, w / 2, h * 3 / 4, 500);
    }

    /** Pull-to-refresh (swipe xuống nhanh từ top) */
    public static void pullToRefresh() {
        String size = shell("wm size");
        String[] parts = size.split(":\\s*")[1].split("x");
        int w = Integer.parseInt(parts[0].trim());
        int h = Integer.parseInt(parts[1].trim());
        swipe(w / 2, h / 6, w / 2, h * 2 / 3, 300);
        sleep(2000); // Đợi refresh xong
    }

    // ─── App Control ────────────────────────────────────────────────────
    /** Mở app bằng package name */
    public static void launchApp(String packageName) {
        shell("monkey -p " + packageName + " -c android.intent.category.LAUNCHER 1");
        sleep(3000);
    }

    /** Mở app EcoPOS Test — giữ nguyên hướng màn hình hiện tại */
    public static void launchEcoposApp() {
        // Tắt auto-rotate → lock giữ nguyên hướng màn hình user đang để
        // KHÔNG đổi rotation vì tablet có thể khác phone (rotation=1 trên tablet = portrait)
        disableAutoRotate();
        // Launch app
        launchApp("vn.com.finviet.ecopos.test");
    }

    // ─── Screen Rotation ────────────────────────────────────────────────
    /** Tắt auto-rotate → lock orientation hiện tại */
    public static void disableAutoRotate() {
        shell("settings put system accelerometer_rotation 0");
        System.out.println("[ADB] 🔒 Tắt auto-rotate");
    }

    /** Bật auto-rotate */
    public static void enableAutoRotate() {
        shell("settings put system accelerometer_rotation 1");
        System.out.println("[ADB] 🔓 Bật auto-rotate");
    }

    /** Force landscape mode (rotation=1) */
    public static void setLandscape() {
        shell("settings put system user_rotation 1");
        System.out.println("[ADB] 🔄 Set landscape mode (rotation=1)");
        sleep(500);
    }

    /** Force portrait mode (rotation=0) */
    public static void setPortrait() {
        shell("settings put system user_rotation 0");
        System.out.println("[ADB] 🔄 Set portrait mode (rotation=0)");
        sleep(500);
    }

    /** Đóng app */
    public static void forceStop(String packageName) {
        shell("am force-stop " + packageName);
        sleep(1000);
    }

    /** Kiểm tra app đang chạy */
    public static boolean isAppRunning(String packageName) {
        String output = shell("pidof " + packageName);
        return output != null && !output.trim().isEmpty();
    }

    // ─── Screenshot ─────────────────────────────────────────────────────
    /** Chụp screenshot, lưu local */
    public static String screenshot(String label) {
        try {
            Files.createDirectories(Paths.get(DUMP_DIR));
            String devicePath = "/sdcard/screenshot_" + label + ".png";
            shell("screencap -p " + devicePath);
            String localPath = DUMP_DIR + "/screenshot_" + label + ".png";
            exec("pull", devicePath, localPath);
            System.out.printf("[ADB] 📸 Screenshot: %s%n", localPath);
            return localPath;
        } catch (IOException e) {
            throw new RuntimeException("Screenshot failed", e);
        }
    }

    // ─── Wait for Element ───────────────────────────────────────────────
    /** Đợi element xuất hiện (text match), timeout tính bằng ms */
    public static Element waitForText(String text, int timeoutMs) throws InterruptedException {
        return waitForText(text, timeoutMs, false);
    }

    public static Element waitForText(String text, int timeoutMs, boolean contains)
            throws InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            Document doc = dumpUI("wait_" + text.replaceAll("[^a-zA-Z0-9]", ""));
            Element el = contains ? findByTextContains(doc, text) : findByText(doc, text);
            if (el != null) return el;
            Thread.sleep(1500);
        }
        throw new RuntimeException("Timeout đợi element text='" + text + "' sau " + timeoutMs + "ms");
    }

    /** Đợi element text xuất hiện, trả về Document chứa nó */
    public static Document waitForTextAndGetDoc(String text, int timeoutMs, boolean contains)
            throws InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            Document doc = dumpUI("wait_" + text.replaceAll("[^a-zA-Z0-9]", ""));
            Element el = contains ? findByTextContains(doc, text) : findByText(doc, text);
            if (el != null) return doc;
            Thread.sleep(1500);
        }
        throw new RuntimeException("Timeout đợi text='" + text + "' sau " + timeoutMs + "ms");
    }

    // ─── Debug: List all elements ───────────────────────────────────────
    /** In ra tất cả elements clickable (useful for debugging) */
    public static void debugListClickable(Document doc) {
        System.out.println("══════ CLICKABLE ELEMENTS ══════");
        listRecursive(doc.getDocumentElement(), 0, true);
        System.out.println("════════════════════════════════");
    }

    /** In ra tất cả elements có text */
    public static void debugListAllText(Document doc) {
        System.out.println("══════ ALL TEXT ELEMENTS ══════");
        listAllTextRecursive(doc.getDocumentElement(), 0);
        System.out.println("═══════════════════════════════");
    }

    // ─── Internal helpers ───────────────────────────────────────────────
    private static Element findRecursive(Node node, String attr, String value, boolean contains) {
        if (node instanceof Element el) {
            String attrVal = el.getAttribute(attr);
            if (attrVal != null && !attrVal.isEmpty()) {
                if (contains ? attrVal.contains(value) : attrVal.equals(value)) {
                    return el;
                }
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Element found = findRecursive(children.item(i), attr, value, contains);
            if (found != null) return found;
        }
        return null;
    }

    private static void findAllRecursive(Node node, String attr, String value, boolean contains, List<Element> results) {
        if (node instanceof Element el) {
            String attrVal = el.getAttribute(attr);
            if (attrVal != null && !attrVal.isEmpty()) {
                if (contains ? attrVal.contains(value) : attrVal.equals(value)) {
                    results.add(el);
                }
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            findAllRecursive(children.item(i), attr, value, contains, results);
        }
    }

    private static void listRecursive(Node node, int depth, boolean clickableOnly) {
        if (node instanceof Element el) {
            boolean clickable = "true".equals(el.getAttribute("clickable"));
            if (!clickableOnly || clickable) {
                String text = el.getAttribute("text");
                String resId = el.getAttribute("resource-id");
                String desc = el.getAttribute("content-desc");
                String cls = el.getAttribute("class");
                if ((text != null && !text.isEmpty()) || (resId != null && !resId.isEmpty())
                        || (desc != null && !desc.isEmpty())) {
                    String indent = "  ".repeat(depth);
                    System.out.printf("%s[%s] text='%s' res-id='%s' desc='%s' bounds=%s%n",
                            indent, cls, text, resId, desc, el.getAttribute("bounds"));
                }
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            listRecursive(children.item(i), depth + 1, clickableOnly);
        }
    }

    private static void listAllTextRecursive(Node node, int depth) {
        if (node instanceof Element el) {
            String text = el.getAttribute("text");
            if (text != null && !text.isEmpty()) {
                String indent = "  ".repeat(depth);
                System.out.printf("%s text='%s' bounds=%s%n", indent, text, el.getAttribute("bounds"));
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            listAllTextRecursive(children.item(i), depth + 1);
        }
    }

    private static void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
