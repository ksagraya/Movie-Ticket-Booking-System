# Movie Ticket Booking System

A production-style Spring Boot backend for a multi-city, multi-theater movie ticket
booking platform with seat-level holds, multiple pricing tiers, discount codes,
refund policies, mock payments, and asynchronous notifications.

> Built as an SDE-2 take-home. **No UI** — REST APIs only.

---

## Submission Checklist

Every item the brief asks for, and where it lives in this repo:

| Requirement | Where it is in this repo |
|---|---|
| **GitHub repository link** | [`https://github.com/ksagraya/Movie-Ticket-Booking-System`](https://github.com/ksagraya/Movie-Ticket-Booking-System/blob/main/README.md) |
| **Multiple commits during development** | `git log --oneline` shows **20 incremental commits** — bootstrap → domain → security → DTOs/exceptions → booking/scheduler/pricing → controllers → tests → quality fixes (SpotBugs/refactor) → docs → repo cleanup. |
| **README.md** | This file. |
| **Agents.md / Claude.md** | [`CLAUDE.md`](CLAUDE.md) — the AI workflow, the prompts that kept the model on rails, and the bugs the AI got wrong & how they were caught. |
| **Skills used during development** | [`SKILLS.md`](SKILLS.md) — the engineering skills exercised end-to-end. |
| **All raw files used during development** | [`docs/ai-artifacts/`](docs/ai-artifacts/) — verbatim problem statement, raw design notes, and any other artefacts the AI saw or produced. |
| **REST APIs covering the core flows** | See §8 (full REST table) and [`docs/SMOKE_TEST.md`](docs/SMOKE_TEST.md) for a runnable curl walkthrough. |
| **Persistence** | MySQL 8 (MariaDB-compatible) via Spring Data JPA; H2 in MySQL-mode for tests. |
| **Basic RBAC** | Spring Security HTTP Basic + bcrypt; `/api/admin/**` locked to `ROLE_ADMIN`. |
| **Input validation & error handling** | Jakarta Bean Validation on DTO records + `GlobalExceptionHandler` returning a stable JSON error envelope. |
| **Unit & Integration tests for the core flows** | `mvn test` → **11 tests pass** (pricing unit tests, RBAC integration tests, end-to-end booking flow, concurrent double-booking test, hold-expiry test). |

---

## 1. Tech Stack & Why

| Layer | Choice | Reasoning |
|-------|--------|-----------|
| Language / Framework | **Java 17 + Spring Boot 3.2** | Industry-standard for transactional REST services; Spring Boot's auto-config keeps the project small without sacrificing power. |
| Database | **MySQL 8 (MariaDB-compatible)** | Mature row-level locking semantics, perfect for the "no double-booking" requirement. Tests run on H2 in MySQL mode for speed. |
| Persistence | **Spring Data JPA / Hibernate 6** | Declarative repositories, `@Lock(PESSIMISTIC_WRITE)` for seat serialization. |
| Auth | **Spring Security — HTTP Basic + bcrypt** | Brief explicitly excluded advanced auth; basic auth gives RBAC with minimal moving parts. |
| Concurrency | **DB pessimistic write-lock + scheduled hold-release job + `@Version` optimistic guard** | Robust, single-node solution that requires no external infra (Redis etc.). |
| Async | **Spring `@Async`** | Notification dispatch off the booking critical path. |
| Validation | **jakarta.validation (Bean Validation)** | Declarative request validation with `@Valid`. |
| Build | **Maven** | Default for Spring Boot, easy to run anywhere. |
| Tests | **JUnit 5 + MockMvc + spring-security-test** | Fast in-JVM integration tests over the whole HTTP layer. |

---

## 2. Quick Start

### 2.1 Prerequisites
- Java 17+
- Maven 3.8+
- MySQL 8 / MariaDB 10+ running locally on port 3306

### 2.2 Database setup
```sql
CREATE DATABASE movie_booking;
CREATE USER 'movieapp'@'localhost' IDENTIFIED BY 'movieapp123';
GRANT ALL PRIVILEGES ON movie_booking.* TO 'movieapp'@'localhost';
FLUSH PRIVILEGES;
```
(Credentials can be overridden via `spring.datasource.*` in `application.properties` or environment variables.)

### 2.3 Run
```bash
mvn spring-boot:run
# App on http://localhost:8090
```

### 2.4 Run tests (uses in-memory H2, no MySQL required)
```bash
mvn test
```

### 2.5 Seeded users
| Username | Password | Role |
|----------|----------|------|
| `admin`  | `admin123` | ADMIN |
| `customer` | `customer123` | CUSTOMER |

Authenticate with HTTP Basic, e.g.:
```bash
curl -u admin:admin123 http://localhost:8090/api/auth/me
```

---

## 3. Domain Model

```
City  1───*  Theater  1───*  Screen  1───*  Seat
                                  │
                                  │ (Show is hosted on a Screen)
                                  ▼
Movie 1───*  Show  1───*  ShowSeat  *───*  Booking  1───1  Payment
                                                │
                                                └── DiscountCode (optional)
                                                └── governed by RefundPolicy on cancel
```

Key entities:
- **`ShowSeat`** – the *materialized* row for one seat × one show. Carries
  `status ∈ {AVAILABLE, HELD, BOOKED}`, `holdExpiresAt`, `heldByUserId`, and the
  tier-derived `price`. This is the single source of truth that concurrent
  bookings race over.
- **`Booking`** – owned by a user, references the show and a many-to-many of
  ShowSeats. Stores subtotal / discount / total / refund.
- **`RefundPolicy`** – tiered "cancel at least N hours before show → P% refund".
  The most generous applicable tier wins.

---

## 4. Concurrency: How Double-Booking Is Prevented

When a customer holds seats, the service:

1. Receives the candidate `showSeatIds`.
2. Sorts them ascending (deadlock avoidance — all transactions lock in the same order).
3. Acquires a **`PESSIMISTIC_WRITE` row-lock** on every requested ShowSeat
   inside a single `READ_COMMITTED` transaction
   (`ShowSeatRepository.findAllByIdsForUpdate`).
4. Validates each row's status. Any seat that is `BOOKED`, or `HELD` with a
   non-expired `holdExpiresAt`, aborts the whole request with **HTTP 409**.
5. Atomically flips the rows to `HELD`, sets `holdExpiresAt = now + 5 min`,
   `heldByUserId`, and persists the `Booking` row in `PENDING`.

Other concurrent transactions on the same seat are forced to wait at the
lock and observe the new state on resume, so they cleanly conflict. The
integration test `concurrent_bookings_for_same_seat_only_one_succeeds` runs
8 threads against one seat and asserts exactly one succeeds (201) and the
other seven get 409.

The hold is automatically released by `HoldExpiryScheduler` every 30 s
*and* by the hold logic itself which treats expired `HELD` as available.
A `@Version` field on `ShowSeat` gives a final optimistic safety net.

---

## 5. Pricing & Discounts

Final per-seat price = `base × tier-multiplier`:

| `SeatCategory` | Base column |
|---|---|
| REGULAR | `Show.basePriceRegular` |
| PREMIUM | `Show.basePricePremium` |

| `PricingTier` | Multiplier |
|---|---|
| REGULAR | 1.00× |
| PREMIUM | 1.15× |
| WEEKEND | 1.25× |

Discount codes carry `percentage` (0–100) and an optional `maxDiscount` cap.
Validity is checked against `validFrom`/`validTo` and `maxUses`/`usedCount`.

---

## 6. Refund Policy

On cancellation of a `CONFIRMED` booking the system computes
`hoursToShow`. It iterates active `RefundPolicy` rows (sorted desc by
`hoursBeforeShow`) and uses the **first matching tier** (i.e. the most
generous policy whose threshold is satisfied).

Example admin setup:
```json
[
  {"name":"Early",   "hoursBeforeShow":48, "refundPercentage":100},
  {"name":"Standard","hoursBeforeShow":24, "refundPercentage":80},
  {"name":"Late",    "hoursBeforeShow":2,  "refundPercentage":40}
]
```
A cancel 30 h before the show → 80%. A cancel after the show start → policy
denies refund and returns 400.

---

## 7. Notifications

`NotificationService` exposes `sendBookingConfirmation`, `sendCancellationNotice`,
and `sendReminder`, each annotated `@Async`. They are invoked from
`BookingService` after the transaction commits the booking state change, so
the user response is never blocked by I/O. Today they log structured messages;
swapping in SES / SendGrid / Twilio is a one-method change.

---

## 8. REST API

Auth: HTTP Basic with seeded users above, or register your own.

### 8.1 Auth
```
POST   /api/auth/register     {username,password,email,role?}    (public)
GET    /api/auth/me                                              (any)
```

### 8.2 Admin (role ADMIN)
```
POST   /api/admin/cities          {name}
POST   /api/admin/theaters        {name,address,cityId}
POST   /api/admin/screens         {name,theaterId,seatLayout:[{rowLabel,seatsCount,category}]}
POST   /api/admin/movies          {title,durationMinutes,language?,genre?}
POST   /api/admin/shows           {movieId,screenId,startTime,basePriceRegular,basePricePremium,pricingTier}
POST   /api/admin/discount-codes  {code,percentage,validFrom,validTo,maxDiscount?,maxUses?,active?}
POST   /api/admin/refund-policies {name,hoursBeforeShow,refundPercentage,active?}
```

### 8.3 Catalog (any authenticated user)
```
GET    /api/catalog/cities
GET    /api/catalog/cities/{cityId}/theaters
GET    /api/catalog/movies
GET    /api/catalog/shows?cityId=&movieId=&date=YYYY-MM-DD
GET    /api/catalog/shows/{id}
GET    /api/catalog/shows/{id}/seats
```

### 8.4 Booking (role CUSTOMER or ADMIN on own bookings)
```
POST   /api/bookings/hold                 {showId,showSeatIds:[...]}    -> PENDING
POST   /api/bookings/{id}/apply-discount  {code}
POST   /api/bookings/{id}/pay                                           -> CONFIRMED
POST   /api/bookings/{id}/cancel                                        -> CANCELLED + refund
GET    /api/bookings/my
GET    /api/bookings/{id}
```

All endpoints return JSON. Errors come back as
```json
{ "error": "Seat 17 is already booked", "status": 409 }
```

A ready-to-run end-to-end smoke script lives in
[`docs/SMOKE_TEST.md`](docs/SMOKE_TEST.md).

---

## 9. Testing

```
mvn test
```

| Test class | Covers |
|---|---|
| `PricingCalculatorTest` (unit) | Tier multipliers for REGULAR / PREMIUM / WEEKEND × seat category. |
| `SecurityRbacIntegrationTest` | Anonymous blocked, customer cannot hit admin, admin can, validation 400s. |
| `BookingFlowIntegrationTest.e2e_admin_setup_then_customer_book_and_cancel` | Full happy path: catalog → seats → hold → discount → pay → cancel → 80% refund → seats freed. |
| `BookingFlowIntegrationTest.concurrent_bookings_for_same_seat_only_one_succeeds` | 8 parallel hold requests on one seat → exactly 1× `201`, 7× `409`. |
| `BookingFlowIntegrationTest.expired_hold_releases_seat_for_new_hold` | After hold expiry the seat is bookable again. |

11 tests, all green.

---

## 10. Project Structure

```
src/main/java/com/booking
├── MovieBookingApplication.java        # entry-point
├── config/                              # SecurityConfig, DataInitializer
├── controller/                          # AuthController, AdminController, CatalogController, BookingController
├── dto/                                 # Requests, Responses (records)
├── entity/                              # JPA entities + enums
├── exception/                           # ApiException, GlobalExceptionHandler
├── repository/                          # Spring Data JPA repositories
├── scheduler/                           # HoldExpiryScheduler
├── security/                            # AppUserDetailsService
└── service/                             # AdminService, BookingService, PaymentService, NotificationService, PricingCalculator, AuthHelper

src/test/java/com/booking
├── integration/                         # BookingFlowIntegrationTest, SecurityRbacIntegrationTest
└── service/                             # PricingCalculatorTest
```

---

## 11. Assumptions & Out-of-Scope

Documented assumptions:
- A **city is uniquely named**; the same theater chain in two cities is two `Theater` rows.
- A **screen** has a fixed seat layout supplied at creation; the system does not yet
  allow editing a screen's layout after shows have been created against it.
- **Pricing is per show**: two prices (regular/premium) and a tier multiplier capture
  the requirement of "regular / premium / weekend" without explosion of base prices.
- **Hold duration** is configurable (`booking.hold.duration-seconds`, default 5 min).
- **Mock payment** always succeeds for positive amounts. The refund leg writes
  back to the same Payment row.
- **Reminders** are exposed in `NotificationService` but no scheduler is wired
  to send them (would be a small `@Scheduled` job scanning bookings with shows
  in the next N hours). Confirmation and cancellation notices *are* fired
  asynchronously.
- Single-node deployment. Distributed locking / sharding is out of scope by the brief.
- All times are stored as `LocalDateTime` in UTC (Hibernate configured with
  `jdbc.time_zone=UTC`).

Out of scope per the assignment:
- UI / frontend
- Deployment / CI
- Microservices
- OAuth / SSO / MFA
- Production observability

---

## 12. Repository Hygiene & Submission Artefacts

This repo is structured so each mandatory submission item has a clear home.

| File / Directory | Purpose |
|---|---|
| [`README.md`](README.md) | This document — overview, design, API reference, how to run, how to test. |
| [`CLAUDE.md`](CLAUDE.md) | **Agents.md / Claude.md** — the AI workflow used during development. Covers prompts, where the AI got things wrong, and how iteration converged. |
| [`SKILLS.md`](SKILLS.md) | The engineering skills exercised across the build. |
| [`docs/SMOKE_TEST.md`](docs/SMOKE_TEST.md) | Copy-paste curl walkthrough that exercises every core flow against a running instance. |
| [`docs/ai-artifacts/`](docs/ai-artifacts/) | **All raw files used during development**: verbatim problem statement, raw design notes, and any other artefact the AI session produced or consumed. |
| `src/main/java/com/booking` | Production code (entities, repos, services, controllers, scheduler, security). |
| `src/test/java/com/booking` | Unit + integration tests. |
| `pom.xml` | Maven build descriptor. |
| `.gitignore` | Excludes `target/`, IDE files, logs. |
| `spotbugs-exclude.xml` | SpotBugs filter that suppresses the JPA/Lombok mutable-reference false positives. |

Multiple incremental commits are expected per the brief; the history is preserved
in `git log` rather than squashed.
