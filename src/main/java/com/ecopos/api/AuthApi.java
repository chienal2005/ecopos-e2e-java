package com.ecopos.api;

import com.ecopos.common.GlobalConstants;
import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.util.Map;

/**
 * AuthApi - Tương đương auth.api.ts
 * Xử lý đăng nhập và lấy access token
 */
public class AuthApi {

    /**
     * Đăng nhập và trả về access token
     */
    public static LoginResult login() {
        Map<String, String> body = Map.of(
                "email", GlobalConstants.EMAIL,
                "password", GlobalConstants.PASSWORD
        );

        Response res = RestAssured.given()
                .baseUri(GlobalConstants.API_BASE_URL)
                .contentType("application/json")
                .header("Content-Type", "application/json")
                .body(body)
                .post("/auth/login");

        String accessToken = res.jsonPath().getString("data.accessToken");
        int status = res.statusCode();

        return new LoginResult(status, accessToken);
    }

    public record LoginResult(int status, String accessToken) {}
}
