package com.ecopos.api;

import com.ecopos.common.GlobalConstants;
import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.util.Map;

/**
 * OrderEcoposApi - Tương đương order.ecopos.api.ts
 * Hoàn thành đơn hàng trong hệ thống nội bộ ECOPOS
 */
public class OrderEcoposApi {

    /**
     * Hoàn thành đơn hàng ECOPOS
     * PATCH /orders/complete/:id
     */
    public static ApiResult completeOrder(String orderId, String accessToken, String siteId) {
        Response res = RestAssured.given()
                .baseUri(GlobalConstants.API_BASE_URL)
                .contentType("application/json")
                .header("Authorization", "Bearer " + accessToken)
                .header("x-site-id", siteId)
                .header("Content-Type", "application/json")
                .body(Map.of())
                .patch("/orders/complete/" + orderId);

        Object body;
        try {
            body = res.jsonPath().get();
        } catch (Exception e) {
            body = Map.of();
        }

        System.out.printf("[OrderEcoposApi.completeOrder] id=%s | STATUS: %d | BODY: %s%n",
                orderId, res.statusCode(),
                res.body().asString().substring(0, Math.min(500, res.body().asString().length())));
        return new ApiResult(res.statusCode(), body, res);
    }

    public record ApiResult(int status, Object body, Response response) {}
}
