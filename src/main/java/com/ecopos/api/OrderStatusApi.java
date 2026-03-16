package com.ecopos.api;

import com.ecopos.common.GlobalConstants;
import com.ecopos.data.OrderStatusPayload;
import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.util.Map;

/**
 * OrderStatusApi - Tương đương order.status.api.ts
 * Cập nhật trạng thái đơn hàng
 */
public class OrderStatusApi {

    /**
     * Cập nhật trạng thái đơn hàng
     * PUT /partner/pos/orders/:tpOrderCode/status
     */
    public static ApiResult updateStatus(String tpOrderCode, OrderStatusPayload.UpdateOrderStatusPayload payload) {
        Response res = RestAssured.given()
                .baseUri(GlobalConstants.API_BASE_URL)
                .contentType("application/json")
                .header("Authorization", "Bearer " + GlobalConstants.PARTNER_TOKEN)
                .header("Content-Type", "application/json")
                .body(payload)
                .put("/partner/pos/orders/" + tpOrderCode + "/status");

        Object body;
        try {
            body = res.jsonPath().get();
        } catch (Exception e) {
            body = Map.of();
        }
        return new ApiResult(res.statusCode(), body, res);
    }

    public record ApiResult(int status, Object body, Response response) {}
}
