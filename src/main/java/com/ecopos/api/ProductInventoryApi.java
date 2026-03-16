package com.ecopos.api;

import com.ecopos.common.GlobalConstants;
import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ProductInventoryApi - Tương đương product.inventory.api.ts
 * Quản lý tồn kho sản phẩm
 */
public class ProductInventoryApi {

    /**
     * Lấy danh sách sản phẩm để kiểm tra tồn kho
     * POST /Products/findAll?limit=1000
     */
    public static ApiResult getProducts(String accessToken, String siteId, int limit) {
        Response res = RestAssured.given()
                .baseUri(GlobalConstants.API_BASE_URL)
                .contentType("application/json")
                .header("Authorization", "Bearer " + accessToken)
                .header("x-site-id", siteId)
                .header("Content-Type", "application/json")
                .queryParam("limit", limit)
                .queryParam("page", 1)
                .body(Map.of())
                .post("/Products/findAll");

        Object body;
        try {
            body = res.jsonPath().get();
        } catch (Exception e) {
            body = Map.of();
        }
        return new ApiResult(res.statusCode(), body, res);
    }

    /**
     * Lấy tồn kho cho danh sách SKUs
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Double> getStockBySkus(String accessToken, String siteId, List<String> skus) {
        ApiResult result = getProducts(accessToken, siteId, 1000);
        Map<String, Object> bodyMap = (Map<String, Object>) result.body();

        List<Map<String, Object>> products = null;
        if (bodyMap != null) {
            Map<String, Object> data = (Map<String, Object>) bodyMap.get("data");
            if (data != null) {
                products = (List<Map<String, Object>>) data.get("Products");
                if (products == null) products = (List<Map<String, Object>>) data.get("items");
                if (products == null) products = (List<Map<String, Object>>) data;
            }
            if (products == null) products = (List<Map<String, Object>>) bodyMap.get("Products");
            if (products == null) products = (List<Map<String, Object>>) bodyMap.get("items");
        }

        Map<String, Double> stockMap = new HashMap<>();
        final List<Map<String, Object>> finalProducts = products;

        for (String sku : skus) {
            double qty = 0;
            if (finalProducts != null) {
                for (Map<String, Object> item : finalProducts) {
                    if (sku.equals(item.get("sku"))) {
                        Object invQty = item.get("inventory_quantity");
                        if (invQty != null) {
                            qty = Double.parseDouble(invQty.toString());
                        }
                        break;
                    }
                }
            }
            stockMap.put(sku, qty);
        }
        return stockMap;
    }

    public record ApiResult(int status, Object body, Response response) {}
}
