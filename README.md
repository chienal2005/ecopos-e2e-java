# 🧪 EcoPOS E2E Tests — Java

> E2E API Tests cho hệ thống EcoPOS, chuyển từ TypeScript/Playwright sang Java/RestAssured/TestNG

## 📁 Cấu trúc Project

```
src/
├── main/java/com/ecopos/
│   ├── api/                    # API clients (REST endpoints)
│   │   ├── AuthApi.java           # Đăng nhập, lấy token
│   │   ├── OrderApi.java          # Push đơn hàng từ sàn
│   │   ├── OrderConfirmApi.java   # Xác nhận đơn hàng
│   │   ├── OrderRejectApi.java    # Từ chối đơn hàng
│   │   ├── OrderStatusApi.java    # Cập nhật trạng thái đơn
│   │   ├── OrderDriverApi.java    # Cập nhật thông tin tài xế
│   │   ├── OrderEcoposApi.java    # Hoàn thành đơn ECOPOS
│   │   ├── OrderQueryApi.java     # Truy vấn đơn hàng
│   │   └── ProductInventoryApi.java # Kiểm tra tồn kho
│   ├── common/                 # Shared utilities
│   │   ├── GlobalConstants.java   # Cấu hình môi trường (.env)
│   │   ├── FixtureLoader.java     # Đọc JSON fixture + helpers
│   │   └── TelegramReporter.java  # Gửi báo cáo qua Telegram
│   ├── data/                   # Data models & payloads
│   │   ├── OrderPayload.java      # Payload tạo đơn hàng
│   │   ├── DriverPayload.java     # Payload thông tin tài xế
│   │   └── OrderStatusPayload.java # Payload trạng thái đơn
│   └── helpers/                # Business flow helpers
│       └── OrderFlow.java         # Các luồng nghiệp vụ chung
├── test/
│   ├── java/com/ecopos/e2e/
│   │   └── E2eSuiteTest.java     # ★ 55 E2E test cases (data-driven)
│   └── resources/
│       ├── testng.xml             # TestNG configuration
│       └── fixtures/e2e/          # JSON fixture data
│           ├── push-order.json       # 12 TCs
│           ├── confirm-order.json    # 5 TCs
│           ├── reject-order.json     # 2 TCs
│           ├── driver-info.json      # 6 TCs
│           ├── delivery-flow.json    # Status flow definitions
│           └── cancel-flow.json      # 18 TCs
```

## 🚀 Chạy Test

```bash
# Chạy toàn bộ 55 test cases
mvn test

# Chạy riêng E2E suite
mvn test -Dtest=E2eSuiteTest
```

## ⚙️ Cấu hình

Copy `.env` vào thư mục gốc project:

```env
APP_ENV=test                    # test | uat | dev

# Test Environment
TEST_EMAIL_LOGIN=...
TEST_PASSWORD_LOGIN=...
TEST_SITE_ID=...
TEST_MNB_STORE_CODE=...
TEST_PARTNER_TOKEN=...

# Telegram (tùy chọn)
TELEGRAM_BOT_TOKEN=...
TELEGRAM_CHAT_ID=...           # Hỗ trợ nhiều ID, phân cách bằng dấu phẩy
REPORT_TITLE=...
```

## 📊 Test Suites (55 TCs)

| # | Suite | Tests | Mô tả |
|---|-------|-------|-------|
| 1 | Push Order | 12 | API tạo đơn: hợp lệ, thiếu field, giá trị sai |
| 2 | Confirm Order | 5 | Xác nhận đơn: tồn kho, SP bán âm |
| 3 | Reject Order | 2 | Từ chối đơn: pending vs confirmed |
| 4 | Driver Info | 6 | Cập nhật tài xế: validation |
| 5 | Delivery Flow | 3 | Luồng giao hàng: unconfirmed + full flow |
| 6 | Cancel Flow | 18 | Hủy đơn từ mọi trạng thái trung gian |
| 8 | Badge | 8 | Badge pending_confirm: push/confirm/reject/cancel |
| 9 | Completion Guard | 1 | Chặn hoàn thành khi thiếu tài xế |

## 📬 Telegram Report

Sau khi chạy xong, kết quả tự động gửi Telegram nếu đã cấu hình `TELEGRAM_BOT_TOKEN` + `TELEGRAM_CHAT_ID`.

## 🛠 Tech Stack

- **Java 23** + **Maven**
- **TestNG 7.9** (Test framework)
- **REST-Assured 5.4** (HTTP client)
- **Jackson 2.16** (JSON)
- **Lombok 1.18** (Reduce boilerplate)
