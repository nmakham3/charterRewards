package org.charter.rewards.api;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(RewardsApiIntegrationTest.FixedClockConfiguration.class)
class RewardsApiIntegrationTest {
    @Autowired private MockMvc mvc;

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Test
    void customerRewardsReturnCompleteJsonContract() throws Exception {
        mvc.perform(get("/rewards/1").param("startMonth", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "customerId": 1,
                          "monthlyRewards": [
                            {"month": "2026-08", "points": 90},
                            {"month": "2026-09", "points": 50},
                            {"month": "2026-10", "points": 150}
                          ],
                          "totalPoints": 290
                        }
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test
    void customerWithoutPurchasesReturnsAllThreeZeroPointMonths() throws Exception {
        mvc.perform(get("/rewards/3"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "customerId": 3,
                          "monthlyRewards": [
                            {"month": "2026-08", "points": 0},
                            {"month": "2026-09", "points": 0},
                            {"month": "2026-10", "points": 0}
                          ],
                          "totalPoints": 0
                        }
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test
    void customerTransactionsReturnCompleteJsonContract() throws Exception {
        mvc.perform(get("/transactions").param("customerId", "2").param("startMonth", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        [
                          {"id": 5, "customerId": 2, "date": "2026-08-05", "amount": 75.50},
                          {"id": 6, "customerId": 2, "date": "2026-09-20", "amount": 200.00},
                          {"id": 7, "customerId": 2, "date": "2026-10-01", "amount": 49.99}
                        ]
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @ParameterizedTest
    @CsvSource({
            "/rewards/0, customerId must be positive",
            "/transactions?customerId=-1, customerId must be positive",
            "/rewards?startMonth=0000-01, startMonth year must be between 0001 and 9998",
            "/rewards/1?startMonth=9999-12, startMonth year must be between 0001 and 9998",
            "/transactions?startMonth=9999-12, startMonth year must be between 0001 and 9998"
    })
    void invalidValuesReturnExplanatoryProblemResponse(String url, String detail) throws Exception {
        mvc.perform(get(url)).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(detail));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/rewards/1?startMonth=bad", "/transactions?startMonth=2026-13",
            "/transactions?customerId=1.5", "/rewards/9223372036854775808"})
    void unparseableParametersReturnBadRequest(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void exposesOpenApiSpecificationAndSwaggerUi() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Charter Rewards API"))
                .andExpect(jsonPath("$.paths['/rewards'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/rewards/{customerId}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/transactions'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/transactions'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/transactions'].post.requestBody.required").value(true))
                .andExpect(jsonPath("$.components.schemas.MonthlyRewards.properties.month.type").value("string"));
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void retrievesAllCustomersAndOneCustomerWithMonthlyArray() throws Exception {
        mvc.perform(get("/rewards")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].totalPoints").value(290))
                .andExpect(jsonPath("$[1].totalPoints").value(275))
                .andExpect(jsonPath("$[2].totalPoints").value(0));
        mvc.perform(get("/rewards/1").param("startMonth", "2026-08"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customerId").value(1))
                .andExpect(jsonPath("$.monthlyRewards[0].month").value("2026-08"))
                .andExpect(jsonPath("$.monthlyRewards[0].points").value(90))
                .andExpect(jsonPath("$.monthlyRewards[1].points").value(50))
                .andExpect(jsonPath("$.monthlyRewards[2].points").value(150));
    }

    @Test
    void retrievesFilteredTransactionsAndEmptyPeriods() throws Exception {
        mvc.perform(get("/transactions")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7));
        mvc.perform(get("/transactions").param("customerId", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].amount").value(75.50));
        mvc.perform(get("/transactions").param("customerId", "3"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/rewards/1").param("startMonth", "2020-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalPoints").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/rewards/abc", "/rewards/0", "/rewards/-1", "/transactions?customerId=abc",
            "/transactions?customerId=0", "/rewards?startMonth=bad", "/rewards?startMonth=2026-13",
            "/rewards?startMonth=0000-01", "/rewards?startMonth=9999-12"})
    void invalidInputsReturnBadRequestWithProblemDetails(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void unknownCustomerReturnsNotFound() throws Exception {
        for (String url : java.util.List.of("/rewards/999", "/transactions?customerId=999")) {
            mvc.perform(get(url)).andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.title").value("Not Found"))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.detail").value("Customer not found: 999"));
        }
    }
}
