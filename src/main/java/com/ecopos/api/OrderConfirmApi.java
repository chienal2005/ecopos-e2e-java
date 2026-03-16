package com.ecopos.api;

import com.ecopos.common.GlobalConstants;
import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.util.Map;

/**
 * OrderConfirmApi - Tương đương order.confirm.api.ts
 * Xác nhận đơn hàng sàn
 */
public class OrderConfirmApi {

    /**
     * Xác nhận đơn hàng sàn
     * PUT /order-integrations/:id/confirm
     */
    public static ApiResult confirmOrder(String integrationId, String accessToken, String siteId) {
        String resolvedSiteId = (siteId != null && !siteId.isBlank()) ? siteId : GlobalConstants.SITE_ID;

        Response res = RestAssured.given()
                .baseUri(GlobalConstants.API_BASE_URL)
                .contentType("application/json")
                .header("Authorization", "Bearer " + accessToken)
                .header("x-site-id", resolvedSiteId)
                .header("Content-Type", "application/json")
                .body(Map.of())
                .put("/order-integrations/" + integrationId + "/confirm");

        int status = res.statusCode();
        Object body;
        try {
            body = res.jsonPath().get();
        } catch (Exception e) {
            body = Map.of();
        }

        System.out.printf("[confirmOrder] id=%s | STATUS: %d | BODY: %s%n",
                integrationId, status,
                res.body().asString().substring(0, Math.min(300, res.body().asString().length())));
        return new ApiResult(status, body, res);
    }

    public record ApiResult(int status, Object body, Response response) {}
}
