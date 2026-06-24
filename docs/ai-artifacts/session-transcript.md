# AI Session Transcript (reconstructed)

This file is a chronological, faithful reconstruction of the AI-assisted
development session that produced this project. It is **not** a polished
recap — it is the raw flow, including the failures, so reviewers can audit
how the work actually happened.

---

## 0. Setup

- Java 17 + Maven + MariaDB installed into the sandbox.
- Local DB `movie_booking` and user `movieapp` created.

## 1. Clarification round (before writing any code)

Five explicit user decisions were captured up front:

1. **Database**: MySQL
2. **Auth**: Spring Security with HTTP Basic
3. **Seat hold strategy**: DB-based pessimistic lock + scheduled releaser
4. **Notifications**: in-process async (no real email provider)
5. **Payments**: mock service

These choices were *not* re-litigated later; every subsequent design decision
flowed from them.

## 2. Scaffold (file creation order)

1. `pom.xml`, `application.properties` (main + test)
2. `MovieBookingApplication.java` (`@EnableScheduling`, `@EnableAsync`)
3. All entity classes in parallel: `User`, `City`, `Theater`, `Screen`, `Seat`,
   `Movie`, `Show`, `ShowSeat`, `Booking`, `Payment`, `DiscountCode`,
   `RefundPolicy` + enums (`Role`, `SeatCategory`, `PricingTier`,
   `ShowSeatStatus`, `BookingStatus`, `PaymentStatus`)
4. All repositories in parallel
5. `SecurityConfig`, `AppUserDetailsService`, `DataInitializer` (seed admin/customer)
6. `Requests` and `Responses` DTO records
7. `ApiException`, `GlobalExceptionHandler`
8. Services: `AuthHelper`, `AdminService`, `BookingService`, `PaymentService`,
   `NotificationService`, `PricingCalculator`
9. `HoldExpiryScheduler`
10. Controllers: `AuthController`, `AdminController`, `CatalogController`,
    `BookingController`

`mvn compile` was run after step 10 — clean.

## 3. Tests + failures

Wrote two integration test classes and one unit test (`PricingCalculatorTest`).

### Failure #1 — compile error
The first integration test used helper methods named `post()` / `get()`,
which collided with `MockMvcRequestBuilders.post/get` static imports.
**Fix**: rename helpers to `doPost` / `doGet`.

### Failure #2 — unique-constraint violation
`expired_hold_releases_seat_for_new_hold` failed at the DB layer with a
unique constraint on `booking_seats.show_seat_id`. Root cause: a JPA
`@OneToMany` with `@JoinTable` adds an implicit `UNIQUE` on the inverse
FK; once a hold expired and the same seat was re-booked, the join insert
failed.
**Fix**: change the relationship to `@ManyToMany`, because a seat can
historically belong to multiple bookings (cancelled → re-booked).

After these two fixes: **11/11 green**, including the 8-thread concurrent
hold race that asserts exactly one client gets 201 and the other seven
get 409.

## 4. Real smoke test

The app was started against the real local MySQL on port 8090 and a curl
walkthrough (`docs/SMOKE_TEST.md`) exercised admin → catalog → hold →
discount → pay → cancel. Double-booking a `BOOKED` seat returned the
expected 409.

## 5. Documentation pass

`README.md`, `CLAUDE.md`, `SKILLS.md`, `docs/SMOKE_TEST.md`, this transcript,
and `design-notes.md` were authored *after* the build was green so they
describe the shipped code, not a plan.

## 6. Code-review pass (static analysis)

SpotBugs was added to the build at `Max` effort / `Low` threshold. It
surfaced:

- **~50 EI_EXPOSE_REP / EI_EXPOSE_REP2** findings — all on JPA entities
  and DTO records that intentionally share references with Hibernate /
  the HTTP layer. These were excluded via `spotbugs-exclude.xml`
  scoped to the `com.booking.entity` and `com.booking.dto` packages
  (false positives for this domain).
- **2× `BX_UNBOXING_IMMEDIATELY_REBOXED`** in `AdminService` —
  `req.active() == null ? true : req.active()` autoboxes twice.
  **Fix**: use `Boolean.TRUE` for the null branch.
- **2× `DM_CONVERT_CASE`** — `String.toUpperCase()` without a locale.
  **Fix**: `toUpperCase(Locale.ROOT)`.

After the fixes `mvn spotbugs:check` is clean.

## 7. Targeted refactor

`BookingService` was split into focused private helpers
(`lockSeatsInOrder`, `validateSeatsAvailableForShow`, `assertSeatBookable`,
`sumSeatPrices`, `applyHold`, `persistPendingBooking`,
`assertDiscountUsable`, `computeDiscount`, `relockOwnedHeldSeats`,
`markSeatsBooked`, `incrementDiscountUsageIfAny`, `processRefund`,
`pickRefundPercentage`, `releaseSeats`). The three public methods
(`holdSeats`, `confirmAndPay`, `cancel`) now read top-to-bottom like the
business spec. Magic numbers (`100`, scale `2`, default hold seconds
`300`) were promoted to named constants.

All 11 tests still green after the refactor.
