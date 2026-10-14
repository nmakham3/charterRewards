package org.charter.rewards.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI rewardsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Charter Rewards API")
                .version("1.0")
                .description("Retailer rewards API with seeded demonstration data and purchase creation. "
                        + "Each purchase earns one point per whole dollar above $50 up to $100, "
                        + "and two points per whole dollar above $100. Cents are discarded per purchase. "
                        + "A $120 purchase earns 90 points. Every reporting period spans three calendar months. "
                        + "If startMonth is omitted, the period covers the current UTC month and the previous two months. "
                        + "Sample dates are relative to the application's startup month. "
                        + "New purchases are stored in memory and lost when the application restarts."));
    }
}
