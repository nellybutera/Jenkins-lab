package com.amali.api;

import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@Epic("FakeStoreAPI")
@Feature("PUT /products/{id}")
class PutProductTest extends BaseTest {

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @Description("PUT /products/1 with a full payload returns 200 and echoes the updated fields (simulated, not persisted)")
    void updateProduct_withFullPayload_returnsOk() {
        Map<String, Object> payload = Map.of(
            "title", "Updated Title",
            "price", 19.99,
            "description", "Updated description",
            "image", "https://example.com/updated.jpg",
            "category", "electronics"
        );

        given()
            .contentType("application/json")
            .body(payload)
            .when().put("/products/1")
            .then()
                .statusCode(200)
                .contentType("application/json")
                .body("title", equalTo(payload.get("title")))
                .body("category", equalTo(payload.get("category")));
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @Description("PUT /products/{id} on a different valid id also returns 200")
    void updateProduct_onAnotherValidId_returnsOk() {
        given()
            .contentType("application/json")
            .body(Map.of("title", "Second Product Update", "price", 5.5))
            .when().put("/products/5")
            .then()
                .statusCode(200)
                .body("title", equalTo("Second Product Update"));
    }
}
