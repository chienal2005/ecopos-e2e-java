package com.ecopos.api;

import com.ecopos.common.GlobalConstants;
import com.ecopos.data.DriverPayload;
import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.util.Map;

/**
 * OrderDriverApi - Tương đương order.driver.api.ts
 * Cập nhật thông tin tài xế
 */
public class OrderDriverApi {

    /**
     * Cập nhật thông tin tài xế
     * PUT /partner/pos/orders/driver-info
     */
    public static ApiResult updateDriver(DriverPayload.DriverInfoPayload payload) {
        Response res = RestAssured.given()
                .baseUri(GlobalConstants.API_BASE_URL)
                .contentType("application/json")
                .header("Authorization", "Bearer " + GlobalConstants.PARTNER_TOKEN)
                .header("Content-Type", "application/json")
                .body(payload)
                .put("/partner/pos/orders/driver-info");

        int status = res.statusCode();
        Object body;
        try {
            body = res.jsonPath().get();
        } catch (Exception e) {
            body = Map.of();
        }

        System.out.println("====== UPDATE DRIVER INFO ======");
        System.out.println("PAYLOAD: " + payload);
        System.out.println("STATUS: " + status);
        System.out.println("RESPONSE: " + body);
        System.out.println("================================");

        return new ApiResult(status, body, res);
    }

    public record ApiResult(int status, Object body, Response response) {}
}
