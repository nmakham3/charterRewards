package org.charter.rewards.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.List;
import org.charter.rewards.model.CustomerRewards;
import org.charter.rewards.model.Transaction;
import org.charter.rewards.service.RewardsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@Tag(name = "Rewards", description = "Customer rewards and purchases in a three-month reporting period")
@ApiResponse(responseCode = "400", description = "Invalid customer ID, reporting month, or purchase details",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
public class RewardsController {
    private final RewardsService service;

    public RewardsController(RewardsService service) {
        this.service = service;
    }

    @PostMapping("/transactions")
    @Operation(summary = "Record a purchase",
            description = "Creates a transaction for any positive customer ID with a server-generated transaction ID. "
                    + "An unknown customer ID is added automatically; no onboarding request is needed. "
                    + "Rewards immediately include it when its date falls in the requested reporting period. "
                    + "Data is stored in memory and lost on restart. Each successful POST creates a separate purchase.")
    @ApiResponse(responseCode = "201", description = "Created purchase")
    public ResponseEntity<Transaction> createTransaction(@Valid @RequestBody CreateTransactionRequest request) {
        Transaction transaction = service.createTransaction(request.customerId(), request.date(), request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(transaction);
    }

    @GetMapping("/rewards")
    @Operation(summary = "Get rewards for all customers",
            description = "Returns three chronological month entries and total points for every known customer, including zero-point months.")
    @ApiResponse(responseCode = "200", description = "Customer rewards in the reporting period")
    public List<CustomerRewards> allRewards(
            @Parameter(description = "First of three reporting months; defaults to the current UTC month minus two. Year must be 0001-9998.",
                    schema = @Schema(type = "string", pattern = "^[0-9]{4}-(0[1-9]|1[0-2])$", example = "2026-08"))
            @RequestParam(required = false) @DateTimeFormat(pattern = "uuuu-MM") YearMonth startMonth) {
        return service.allRewards(service.reportingStart(startMonth));
    }

    @GetMapping("/rewards/{customerId}")
    @Operation(summary = "Get rewards for one customer",
            description = "Known customers without purchases receive three zero-point month entries and a zero total.")
    @ApiResponse(responseCode = "200", description = "Monthly and total customer rewards")
    @ApiResponse(responseCode = "404", description = "Customer does not exist",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public CustomerRewards customerRewards(
            @Parameter(description = "Positive customer ID; sample customers are 1, 2, and 3", schema = @Schema(minimum = "1", example = "1"))
            @PathVariable long customerId,
            @Parameter(description = "First of three reporting months; defaults to the current UTC month minus two. Year must be 0001-9998.",
                    schema = @Schema(type = "string", pattern = "^[0-9]{4}-(0[1-9]|1[0-2])$", example = "2026-08"))
            @RequestParam(required = false) @DateTimeFormat(pattern = "uuuu-MM") YearMonth startMonth) {
        return service.customerRewards(customerId, service.reportingStart(startMonth));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Get transactions",
            description = "Returns purchases from the first day of the start month (inclusive) to the first day after three months (exclusive). "
                    + "Omit customerId for all customers. Returns an empty array when no purchases match.")
    @ApiResponse(responseCode = "200", description = "Matching purchases")
    @ApiResponse(responseCode = "404", description = "Specified customer does not exist",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public List<Transaction> transactions(
            @Parameter(description = "Optional positive customer ID; sample customers are 1, 2, and 3", schema = @Schema(minimum = "1", example = "1"))
            @RequestParam(required = false) Long customerId,
            @Parameter(description = "First of three reporting months; defaults to the current UTC month minus two. Year must be 0001-9998.",
                    schema = @Schema(type = "string", pattern = "^[0-9]{4}-(0[1-9]|1[0-2])$", example = "2026-08"))
            @RequestParam(required = false) @DateTimeFormat(pattern = "uuuu-MM") YearMonth startMonth) {
        return service.transactions(customerId, service.reportingStart(startMonth));
    }
}
