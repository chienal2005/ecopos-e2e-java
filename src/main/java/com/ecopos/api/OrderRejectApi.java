package com.ecopos.api;

import com.ecopos.common.GlobalConstants;
import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.util.Map;

/**
 * OrderRejectApi - Tương đương order.reject.api.ts
 * Từ chối đơn hàng sàn
 */
public class OrderRejectApi {

    /**
     * Từ chối đơn hàng sàn
     * PUT /order-integrations/:id/reject
     */
    public static ApiResult rejectOrder(String integrationId, String accessToken, String reason, String siteId) {
        String resolvedSiteId = (siteId != null && !siteId.isBlank()) ? siteId : GlobalConstants.SITE_ID;
        String resolvedReason = (reason != null && !reason.isBlank()) ? reason : "Từ chối đơn hàng - E2E Test";

        Response res = RestAssured.given()
                .baseUri(GlobalConstants.API_BASE_URL)
                .contentType("application/json")
                .header("Authorization", "Bearer " + accessToken)
                .header("x-site-id", resolvedSiteId)
                .header("Content-Type", "application/json")
                .body(Map.of("reason", resolvedReason))
                .put("/order-integrations/" + integrationId + "/reject");

        Object body;
        try {
            body = res.jsonPath().get();
        } catch (Exception e) {
            body = Map.of();
        }

        System.out.printf("[rejectOrder] id=%s | STATUS: %d | BODY: %s%n",
                integrationId, res.statusCode(),
                res.body().asString().substring(0, Math.min(300, res.body().asString().length())));
        return new ApiResult(res.statusCode(), body, res);
    }

    public record ApiResult(int status, Object body, Response response) {}
}
