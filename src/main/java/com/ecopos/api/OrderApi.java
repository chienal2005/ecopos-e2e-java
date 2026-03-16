package com.ecopos.api;

import com.ecopos.common.GlobalConstants;
import io.restassured.RestAssured;
import io.restassured.response.Response;

/**
 * OrderApi — Push đơn hàng từ sàn vào hệ thống
 * POST /partner/pos/orders/push
 */
public class OrderApi {

    /** Push đơn hàng (retry 1 lần nếu 5xx, delay 500ms thay vì 2s) */
    public static ApiResult pushOrder(Object payload) {
        Response res = doPush(payload);

        // Retry 1 lần nếu server error (5xx)
        if (res.statusCode() >= 500) {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            res = doPush(payload);
        }

        Object body;
        try { body = res.jsonPath().get(); }
        catch (Exception e) { body = null; }

        System.out.printf("[push] %d | %s%n", res.statusCode(),
                res.body().asString().substring(0, Math.min(200, res.body().asString().length())));
        return new ApiResult(res.statusCode(), body, res);
    }

    /** Lấy chi tiết đơn hàng */
    public static Response getDetail(String orderId, String accessToken) {
        return RestAssured.given()
                .baseUri(GlobalConstants.API_BASE_URL)
                .header("Authorization", "Bearer " + accessToken)
                .get("/Orders/" + orderId);
    }

    private static Response doPush(Object payload) {
        return RestAssured.given()
                .baseUri(GlobalConstants.API_BASE_URL)
                .contentType("application/json")
                .header("Authorization", "Bearer " + GlobalConstants.PARTNER_TOKEN)
                .body(payload)
                .post("/partner/pos/orders/push");
    }

    public record ApiResult(int status, Object body, Response response) {}
}
