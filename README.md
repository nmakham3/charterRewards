# Charter Rewards API

Spring Boot 3.5.5 / Java 21 implementation of the retailer rewards assessment.
The API starts with an in-memory sample dataset and accepts new purchases.
No database setup is needed. New purchases are lost when the application restarts.

## Run

Install a JDK 21 and set `JAVA_HOME` to its installation directory. The Maven
wrapper downloads Maven 3.9.11 on first use; Maven Central access is needed for
the first build. Spring Boot's Java compatibility is documented in the
[official requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html).

Windows PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

macOS / Linux:

```sh
sh mvnw clean verify
sh mvnw spring-boot:run
```

The application listens at `http://localhost:8080`. Alternatively, after building:

```sh
java -jar target/charterRewards-1.0-SNAPSHOT.jar
```

## API documentation

After starting the application, open [Swagger UI](http://localhost:8080/swagger-ui.html)
to browse endpoints and execute requests using **Try it out**. Each operation
documents its reporting window, customer filters, response model, and error statuses.

The generated OpenAPI specification is available as
[JSON](http://localhost:8080/v3/api-docs) or
[YAML](http://localhost:8080/v3/api-docs.yaml), suitable for importing into Postman
or generating clients. Documentation uses springdoc's Spring Boot 3 compatible
starter; see the [official compatibility matrix](https://springdoc.org/v2/).

## Endpoints

| Method | Path | Result |
| --- | --- | --- |
| GET | `/rewards` | Monthly and total rewards for every customer |
| GET | `/rewards/{customerId}` | Monthly and total rewards for one customer |
| GET | `/transactions` | Transactions in the reporting period |
| GET | `/transactions?customerId=1` | Transactions for one customer in the reporting period |
| POST | `/transactions` | Record a purchase for an existing or new customer ID |

All GET endpoints accept an optional `startMonth=YYYY-MM`. The reporting window
includes that month and the following two calendar months. Without this parameter,
the window includes the current UTC month and the previous two months.
The start date is inclusive and the first day after the third month is exclusive.
Filtering is applied when querying the repository; old and future data remain in
the dataset to demonstrate that filtering is independent of data retention.

```powershell
Invoke-RestMethod 'http://localhost:8080/rewards'
Invoke-RestMethod 'http://localhost:8080/rewards/1'
Invoke-RestMethod 'http://localhost:8080/transactions?customerId=1'
Invoke-RestMethod 'http://localhost:8080/rewards/1?startMonth=2026-08'
```

Example response for customer 1 in the default period when running in October 2026:

```json
{
  "customerId": 1,
  "monthlyRewards": [
    { "month": "2026-08", "points": 90 },
    { "month": "2026-09", "points": 50 },
    { "month": "2026-10", "points": 150 }
  ],
  "totalPoints": 290
}
```

Known customers with no purchases return three zero-point months; their transaction
list is empty. GET requests for unknown customers return HTTP 404 until their
first valid purchase is recorded. Nonpositive or nonnumeric IDs,
invalid months, and reporting start years outside 0001–9998 return HTTP 400.
Errors use `application/problem+json` with status and explanatory detail.

## Record new transactions

Send `POST /transactions` with `Content-Type: application/json`:

```json
{
  "customerId": 1,
  "date": "2026-10-13",
  "amount": 120.00
}
```

PowerShell example using today's UTC date:

```powershell
$purchase = @{
    customerId = 1
    date = [DateTime]::UtcNow.ToString('yyyy-MM-dd')
    amount = 120.00
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/transactions' `
    -ContentType 'application/json' -Body $purchase
Invoke-RestMethod 'http://localhost:8080/rewards/1'
```

The API returns HTTP **201 Created** with the saved transaction, including its
server-generated `id`. Each successful POST records a separate purchase, even if
the body is identical. A $120 purchase adds 90 points to its customer's purchase
month and total when that month falls within the requested reporting period.
Purchases outside the default window can be retrieved using `startMonth`.

All three fields are required. `customerId` must be a positive integer. Sample
customers have IDs 1, 2, and 3. For a new ID, the first valid purchase automatically
adds that customer ID. No separate onboarding
or registration request is needed. Later purchases for that ID use the same customer,
identified by `customerId`. Invalid purchases do not add customers.
`date` must be a valid ISO date with
year 0001-9999; historical and future dates are accepted. `amount` must be a
nonnegative USD number with at most 15 integer digits and two decimal places.
Zero-value purchases are allowed and earn no points. Invalid bodies return
HTTP 400 with problem details; validation errors include an `errors` object
keyed by field name. POST accepts new IDs; GET still returns HTTP 404 for IDs that
have neither sample data nor a recorded purchase.

For example, submit the same JSON above with `"customerId": 4`. Then
GET `/rewards/4` and GET `/transactions?customerId=4` include the new customer
and their purchases, subject to the reporting period. GET `/rewards` includes
them as well.

Purchases are held in memory. Concurrent requests receive unique transaction IDs.
Restarting the application discards submitted purchases and automatically added
customers, and recreates the samples.

## Reward rules and assumptions

Each transaction earns one point per whole dollar above $50 through $100 and
two points per whole dollar above $100. Cents do not earn fractional points and
are discarded separately for each purchase, before monthly totals are added.
The assessment leaves fractional-dollar behavior unspecified; this project
explicitly uses whole dollars. Amounts use `BigDecimal`, never floating point.

For each purchase, let `dollars` be its amount rounded down to a whole dollar:

```text
points = min(max(dollars - 50, 0), 50) + 2 * max(dollars - 100, 0)
```

Calculate points separately for every purchase, then sum them per calendar month
and customer. Two $60 purchases earn 20 points in total; combining them into
a $120 purchase before applying thresholds would incorrectly produce 90 points.

| Purchase | Points |
| --- | ---: |
| $50.00 | 0 |
| $75.50 | 25 |
| $100.00 | 50 |
| $100.99 | 50 |
| $120.00 | 90 |
| $150.75 | 150 |

The sample data is generated relative to the startup UTC month:

| Customer | Two months ago | Previous month | Current month | Total points |
| --- | --- | --- | --- | ---: |
| 1 | $120 + $50 | $100 | $150.75 | 290 |
| 2 | $75.50 | $200 | $49.99 | 275 |
| 3 | No purchases | No purchases | No purchases | 0 |

Two additional $1,000 purchases sit immediately outside the default window.
The current-month purchases occur on its first day so samples are not future dated.
Sample dates remain fixed until the application restarts. Purchase amounts must
be nonnegative with at most two significant decimal places; returns and refunds
are outside this assessment's scope.

## Structure and verification

`api` holds controllers and error handling, `service` holds calculation and
aggregation, `repository` owns sample data and filtered reads, and `model` holds
immutable records. Time is supplied using an injected `Clock` so tests remain
repeatable across months and years.

`./mvnw test` (Windows: `.\mvnw.cmd test`) runs calculator boundary tests,
service regression tests, model validation tests, and full-context HTTP acceptance
tests using MockMvc. Tests cover $100, cents, monthly totals, customer-scoped queries,
out-of-period transactions, year rollover, empty data, and invalid requests.
Purchase creation tests verify HTTP 201, immediate reward updates, validation,
out-of-period submissions, automatic customer creation, and unique IDs during
concurrent writes without duplicate customers.

## MockMvc integration tests

`RewardsApiIntegrationTest` and `TransactionApiIntegrationTest` use
`@SpringBootTest` and `@AutoConfigureMockMvc` to make HTTP-style requests through
the real Spring MVC controllers, JSON serialization, validation, exception
handlers, services, and in-memory repository. No service or repository is mocked.
MockMvc runs in-process; no separately running server is required.

The clock is fixed to October 13, 2026. Tests that create purchases reset the
application context afterward so each test sees fresh sample data.

| Scenario | Response checks |
| --- | --- |
| GET all rewards, customer rewards, and filtered transactions | 200, JSON media type, customer IDs, monthly values and totals |
| Customer with no purchases | Three zero-point months and empty transaction array |
| POST for existing and new customer IDs | 201, saved purchase fields, distinct IDs, subsequent GET reward updates |
| Whole-dollar reward thresholds and reporting window boundaries | Correct points and inclusion/exclusion of purchase dates |
| Invalid IDs, months, fields, and malformed JSON | 400, problem details and field validation errors |
| GET unknown customer | 404 and explanatory problem details |
| Unsupported HTTP method or body media type | 405 with allowed methods, or 415 with problem details |
| Rejected purchase | No new customer or transaction and unchanged reward totals |

Run only these integration tests on Windows:

```powershell
.\mvnw.cmd '-Dtest=RewardsApiIntegrationTest,TransactionApiIntegrationTest' test
```

On macOS / Linux:

```sh
sh mvnw '-Dtest=RewardsApiIntegrationTest,TransactionApiIntegrationTest' test
```

Run all tests with `.\mvnw.cmd verify` (macOS / Linux: `sh mvnw verify`).
Test reports are written to `target/surefire-reports`.

## Manual acceptance checks

1. GET `/rewards`: customer 1 has 290 points, customer 2 has 275, customer 3 has 0.
2. GET `/rewards/1`: three chronological monthly totals are 90, 50, 150.
3. GET `/transactions`: seven purchases are returned, excluding the two $1,000 purchases.
4. GET `/transactions?customerId=3`: an empty array is returned.
5. GET `/rewards/999`: 404; GET `/rewards/0`: 400.
6. GET `/rewards?startMonth=invalid`: 400 with problem details.
7. POST a $120 purchase for customer 1 dated in the current UTC month: 201 with an ID;
   GET `/rewards/1` now reports 380 total points. Baseline checks above assume a
   fresh application before any purchases are submitted.

## GitHub submission

Repository: [nmakham3/charterRewards](https://github.com/nmakham3/charterRewards).

Build output, IDE metadata, and logs are excluded by `.gitignore`. The local commit
contains the source, tests, README, POM, and Maven wrapper. Publication is pending
GitHub write access to the repository.
No assessment document or external project folder needs to be committed.

## Assessment coverage review

Reviewed against `Assessment Web Api Developers (1).docx` on October 13, 2026.

| Requirement or reviewer concern | Implementation / evidence |
| --- | --- |
| Spring Boot and Java 8 or newer | Spring Boot 3.5.5, Java 21, executable Maven build |
| All-customer rewards, customer rewards, transactions | GET `/rewards`, `/rewards/{customerId}`, `/transactions` |
| Points per purchase, per month, and total | `RewardCalculator` and `RewardsService`; $100 earns 50, $120 earns 90 |
| Three-month period enforced independently of sample data | Explicit start-inclusive/end-exclusive repository filtering; outside-period data retained |
| Convenient monthly response format | Three chronological month/points objects, including zero months |
| Sample dataset | Three customers; threshold, cents, empty-customer, old and future purchase examples |
| Clear unit, regression, and acceptance tests | Calculator, service, model, repository, and full-context MockMvc tests; manual acceptance steps above |
| Date and fractional-point regressions | Fixed clock, year rollover, leap day, date boundaries, cents discarded per purchase |
| Input validation and error handling | Required purchase fields, positive IDs, amount limits, valid dates/months, problem details for 400/404/405/415 |
| Simple component structure and meaningful names | Separate API, service, repository, model, configuration, and exception packages |
| README, gitignore, Maven wrapper | Run/API/test instructions; IDE/build exclusions; `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` |
| Avoid unnecessary libraries | Spring Web, validation, test starter; springdoc supports the requested Swagger documentation |
| GitHub submission | Local `main` is committed and connected to [nmakham3/charterRewards](https://github.com/nmakham3/charterRewards); push pending write access |

The assessment does not specify cents handling or which three months to use.
This implementation documents whole-dollar points and a default of the current
UTC month plus the previous two months, with `startMonth` for an explicit period.
The requested POST endpoint additionally supports automatic customer IDs without
onboarding. Storage is deliberately in memory; persistence is not an assessment
requirement. Formal stakeholder UAT sign-off remains external to these automated
tests and the documented manual acceptance checks.
