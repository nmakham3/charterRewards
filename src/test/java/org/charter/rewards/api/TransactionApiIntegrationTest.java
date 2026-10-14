package org.charter.rewards.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(RewardsApiIntegrationTest.FixedClockConfiguration.class)
class TransactionApiIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DirtiesContext
    void createsPurchasesWithDistinctIdsAndImmediatelyUpdatesRewards() throws Exception {
        String body = """
                {"customerId": 3, "date": "2026-10-13", "amount": 120.00}
                """;
        String first = mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.customerId").value(3))
                .andExpect(jsonPath("$.date").value("2026-10-13"))
                .andExpect(jsonPath("$.amount").value(120.00))
                .andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertNotEquals(objectMapper.readTree(first).get("id").asLong(),
                objectMapper.readTree(second).get("id").asLong(), "Each POST must assign a distinct transaction ID");
        mvc.perform(get("/transactions?customerId=3")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/rewards/3")).andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyRewards[2].points").value(180))
                .andExpect(jsonPath("$.totalPoints").value(180));
        mvc.perform(get("/rewards")).andExpect(status().isOk())
                .andExpect(jsonPath("$[2].totalPoints").value(180));
    }

    @Test
    @DirtiesContext
    void recordsOutOfPeriodPurchasesWithoutChangingDefaultRewards() throws Exception {
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": 3, "date": "2020-01-01", "amount": 100.00}
                                """))
                .andExpect(status().isCreated());
        mvc.perform(get("/rewards/3")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPoints").value(0));
        mvc.perform(get("/rewards/3?startMonth=2020-01")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPoints").value(50));
        mvc.perform(get("/transactions?customerId=3&startMonth=2020-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "null", "", "{",
            "{\"customerId\":0,\"date\":\"2026-10-13\",\"amount\":120}",
            "{\"customerId\":-1,\"date\":\"2026-10-13\",\"amount\":120}",
            "{\"customerId\":1.5,\"date\":\"2026-10-13\",\"amount\":120}",
            "{\"customerId\":3,\"date\":null,\"amount\":120}",
            "{\"customerId\":3,\"date\":\"2026-02-30\",\"amount\":120}",
            "{\"customerId\":3,\"date\":\"0000-01-01\",\"amount\":120}",
            "{\"customerId\":3,\"date\":\"2026-10-13\",\"amount\":null}",
            "{\"customerId\":3,\"date\":\"2026-10-13\",\"amount\":-1}",
            "{\"customerId\":3,\"date\":\"2026-10-13\",\"amount\":1.001}",
            "{\"customerId\":3,\"date\":\"2026-10-13\",\"amount\":1000000000000000}"
    })
    void rejectsInvalidBodiesWithoutSavingPurchases(String body) throws Exception {
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").exists());
        mvc.perform(get("/transactions?customerId=3")).andExpect(content().json("[]"));
    }

    @Test
    void reportsFieldValidationErrors() throws Exception {
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.customerId").exists())
                .andExpect(jsonPath("$.errors.date").exists())
                .andExpect(jsonPath("$.errors.amount").exists());
    }

    @Test
    @DirtiesContext
    void firstPurchaseAddsNewCustomerAndSubsequentPurchasesReuseCustomer() throws Exception {
        mvc.perform(get("/rewards/999")).andExpect(status().isNotFound());
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": 999, "date": "2026-10-13", "amount": 120}
                                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.customerId").value(999));
        mvc.perform(get("/rewards/999")).andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(999))
                .andExpect(jsonPath("$.totalPoints").value(90));
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": 999, "date": "2026-10-13", "amount": 100}
                                """))
                .andExpect(status().isCreated());
        mvc.perform(get("/transactions?customerId=999")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/rewards/999")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPoints").value(140));
        mvc.perform(get("/rewards")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[3].customerId").value(999))
                .andExpect(jsonPath("$[3].totalPoints").value(140));
    }

    @Test
    void invalidFirstPurchaseDoesNotIntroduceCustomer() throws Exception {
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": 999, "date": "2026-10-13", "amount": -1}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/rewards/999")).andExpect(status().isNotFound());
        mvc.perform(get("/rewards")).andExpect(jsonPath("$.length()").value(3));
    }

    @ParameterizedTest(name = "Purchase of ${0} produces {1} reward points")
    @CsvSource({"0.00,0", "50.99,0", "100.00,50", "100.99,50", "120.00,90", "150.75,150"})
    @DirtiesContext
    void submittedPurchasesEarnExpectedPointsThroughApi(String amount, long points) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "customerId", 4, "date", "2026-10-13", "amount", new java.math.BigDecimal(amount)));
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.customerId").value(4))
                .andExpect(jsonPath("$.date").value("2026-10-13"));
        mvc.perform(get("/rewards/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyRewards[0].points").value(0))
                .andExpect(jsonPath("$.monthlyRewards[1].points").value(0))
                .andExpect(jsonPath("$.monthlyRewards[2].points").value(points))
                .andExpect(jsonPath("$.totalPoints").value(points));
    }

    @Test
    @DirtiesContext
    void reportingWindowIncludesFirstAndLastDaysAndExcludesOutsideDays() throws Exception {
        for (String date : java.util.List.of("2026-07-31", "2026-08-01", "2026-09-15", "2026-10-31", "2026-11-01")) {
            String body = objectMapper.writeValueAsString(java.util.Map.of("customerId", 4, "date", date, "amount", 120));
            mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());
        }
        mvc.perform(get("/transactions?customerId=4&startMonth=2026-08"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].date").value("2026-08-01"))
                .andExpect(jsonPath("$[1].date").value("2026-09-15"))
                .andExpect(jsonPath("$[2].date").value("2026-10-31"));
        mvc.perform(get("/rewards/4?startMonth=2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyRewards[0].points").value(90))
                .andExpect(jsonPath("$.monthlyRewards[1].points").value(90))
                .andExpect(jsonPath("$.monthlyRewards[2].points").value(90))
                .andExpect(jsonPath("$.totalPoints").value(270));
    }

    @Test
    void malformedJsonReturnsUsefulProblemDetailsAndDoesNotChangeRewards() throws Exception {
        mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(
                        "A JSON body with numeric customerId, YYYY-MM-DD date, and numeric amount is required"));
        mvc.perform(get("/rewards/1")).andExpect(status().isOk()).andExpect(jsonPath("$.totalPoints").value(290));
        mvc.perform(get("/transactions")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7));
    }

    @Test
    void unsupportedContentTypeReturns415WithoutSavingPurchase() throws Exception {
        mvc.perform(post("/transactions").contentType(MediaType.TEXT_PLAIN)
                        .content("""
                                {"customerId": 4, "date": "2026-10-13", "amount": 120}
                                """))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(415));
        mvc.perform(get("/rewards/4")).andExpect(status().isNotFound());
    }

    @Test
    @DirtiesContext
    void rewardsApplyThresholdToEachPurchaseRatherThanCombinedMonthlySpend() throws Exception {
        for (int purchase = 0; purchase < 2; purchase++) {
            mvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                            {"customerId": 4, "date": "2026-10-13", "amount": 60.00}
                            """))
                    .andExpect(status().isCreated());
        }
        mvc.perform(get("/rewards/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyRewards[2].points").value(20))
                .andExpect(jsonPath("$.totalPoints").value(20));
        mvc.perform(get("/rewards/1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalPoints").value(290));
    }

    @Test
    void unsupportedHttpMethodReturns405AndAllowedMethods() throws Exception {
        mvc.perform(delete("/transactions"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")))
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("POST")))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(405));
    }
}
