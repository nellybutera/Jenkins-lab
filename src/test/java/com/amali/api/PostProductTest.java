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
import static org.hamcrest.Matchers.notNullValue;

@Epic("FakeStoreAPI")
@Feature("POST /products")
class PostProductTest extends BaseTest {

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @Description(
        "POST /products with a full payload returns 201 and echoes the submitted fields back. " +
        "The response id is a simulated auto-increment (not persisted) so we assert it is present, " +
        "not a specific value."
    )
    void createProduct_withFullPayload_returnsCreated() {
        Map<String, Object> payload = Map.of(
            "title", "Automation Test Product",
            "price", 29.99,
            "description", "Created by the Jenkins CI/CD lab test suite",
            "image", "https://example.com/image.jpg",
            "category", "electronics"
        );

        given()
            .contentType("application/json")
            .body(payload)
            .when().post("/products")
            .then()
                .statusCode(201)
                .contentType("application/json")
                .body("id", notNullValue())
                .body("title", equalTo(payload.get("title")))
                .body("price", equalTo(29.99f))
                .body("category", equalTo(payload.get("category")));
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /products with a partial payload still returns 201 with an id present")
    void createProduct_withPartialPayload_returnsCreated() {
        Map<String, Object> payload = Map.of(
            "title", "Partial Payload Product",
            "price", 9.99
        );

        given()
            .contentType("application/json")
            .body(payload)
            .when().post("/products")
            .then()
                .statusCode(201)
                .body("id", notNullValue());
    }
}
