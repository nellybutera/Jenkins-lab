package com.amali.api;

import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.module.jsv.JsonSchemaValidator;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@Epic("FakeStoreAPI")
@Feature("GET /products")
class GetProductsTest extends BaseTest {

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /products returns 200, JSON content type, and the default 20-item page")
    void getAllProducts_returnsDefaultPage() {
        given()
            .when().get("/products")
            .then()
                .statusCode(200)
                .contentType("application/json")
                .body("size()", equalTo(20))
                .body("id", everyItem(notNullValue()));
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @Description("GET /products?limit=5 filters the result set to the requested size")
    void getProducts_withLimit_returnsLimitedResults() {
        given()
            .queryParam("limit", 5)
            .when().get("/products")
            .then()
                .statusCode(200)
                .body("size()", equalTo(5));
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /products/1 returns a single product matching the product JSON schema")
    void getSingleProduct_matchesSchema() {
        given()
            .when().get("/products/1")
            .then()
                .statusCode(200)
                .contentType("application/json")
                .body("id", equalTo(1))
                .body(JsonSchemaValidator.matchesJsonSchemaInClasspath("schemas/product-schema.json"));
    }

    @Test
    @Severity(SeverityLevel.MINOR)
    @Description(
        "FakeStoreAPI does not follow REST 404 semantics for a missing product id: it returns 200 " +
        "with an empty body instead of 404. This test documents that observed behavior rather than " +
        "asserting a 'correct' REST response."
    )
    void getNonExistentProduct_returnsEmptyBodyNot404() {
        given()
            .when().get("/products/99999")
            .then()
                .statusCode(200)
                .body(equalTo(""));
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @Description("GET /products/categories returns the known set of product categories")
    void getCategories_returnsKnownCategories() {
        given()
            .when().get("/products/categories")
            .then()
                .statusCode(200)
                .body("$", hasSize(greaterThan(0)))
                .body("$", hasItems("electronics", "jewelery", "men's clothing", "women's clothing"));
    }
}
