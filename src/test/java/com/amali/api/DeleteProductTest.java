package com.amali.api;

import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@Epic("FakeStoreAPI")
@Feature("DELETE /products/{id}")
class DeleteProductTest extends BaseTest {

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @Description(
        "DELETE /products/1 returns 200 and echoes back the full deleted product object " +
        "(not an empty body) -- this is FakeStoreAPI's simulated-delete behavior, asserted as observed."
    )
    void deleteProduct_returnsDeletedProductBody() {
        given()
            .when().delete("/products/1")
            .then()
                .statusCode(200)
                .contentType("application/json")
                .body("id", equalTo(1))
                .body("title", notNullValue());
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @Description("DELETE on another valid product id also returns 200 with that product's body")
    void deleteAnotherProduct_returnsOk() {
        given()
            .when().delete("/products/10")
            .then()
                .statusCode(200)
                .body("id", equalTo(10));
    }
}
