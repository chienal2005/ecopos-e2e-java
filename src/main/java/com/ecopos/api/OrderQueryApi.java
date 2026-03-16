package com.ecopos.api;

import com.ecopos.common.GlobalConstants;
import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.util.Map;

/**
 * OrderQueryApi — Truy vấn đơn hàng (integration list, findAll)
 */
public class OrderQueryApi {

    /** GET /order-integrations */
    public static ApiResult getIntegrationList(String token, int page, int limit, String siteId) {
        String site = resolve(siteId);
        Response res = RestAssured.given()
                .baseUri(GlobalConstants.API_BASE_URL)
                .contentType("application/json")
                .header("Authorization", "Bearer " + token)
                .header("x-site-id", site)
                .header("ui_mode", "backoffice")
                .queryParam("page", page).queryParam("limit", limit)
                .get("/order-integrations");

        return parse(res, "integrations");
    }

    /** POST /Orders/findAll */
    public static ApiResult findAll(String token, Map<String, String> params, String siteId) {
        String site = resolve(siteId);
        Response res = RestAssured.given()
                .baseUri(GlobalConstants.API_BASE_URL)
                .contentType("application/json")
                .header("Authorization", "Bearer " + token)
                .header("x-site-id", site)
                .queryParam("page", params.getOrDefault("page", "1"))
                .queryParam("limit", params.getOrDefault("limit", "1000"))
                .body(Map.of())
                .post("/Orders/findAll");

        return parse(res, "findAll");
    }

    private static String resolve(String siteId) {
        return (siteId != null && !siteId.isBlank()) ? siteId : GlobalConstants.SITE_ID;
    }

    private static ApiResult parse(Response res, String label) {
        Object body;
        try { body = res.jsonPath().get(); }
        catch (Exception e) { body = Map.of(); }
        System.out.printf("[%s] %d%n", label, res.statusCode());
        return new ApiResult(res.statusCode(), body, res);
    }

    public record ApiResult(int status, Object body, Response response) {}
}
