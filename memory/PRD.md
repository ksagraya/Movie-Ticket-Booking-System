# Movie Ticket Booking System – PRD

## Original problem statement
SDE-2 Take-Home — Build a movie ticket booking system in **Spring Boot** with multi-city,
multi-theater, multi-show, seat-level booking. Must support timebound seat holds with
auto-release, pricing tiers (regular/premium/weekend), discount codes, payments, refund
policies on cancellation, concurrent booking serialization without double-allocation,
and non-blocking async confirmation/reminder notifications. Roles: admin and customer.
REST APIs, persistence, basic RBAC, validation, README + unit/integration tests are in
scope. UI, deployment, advanced auth, microservices, and observability are out of scope.

## User personas
- **Admin** — manages catalog (cities, theaters, screens & seat layouts, movies, shows),
  pricing tiers on shows, discount codes, refund policies.
- **Customer** — registers, browses cities → shows → seats, places a hold, optionally
  applies a discount, pays, sees booking history, and may cancel for a policy-based refund.

## Core requirements (static)
1. Multi-tenant catalog (City → Theater → Screen → Seat).
2. Show creation materializes a `ShowSeat` per seat with tier-derived price.
3. Concurrent hold attempts on the same seat must serialize correctly — exactly one wins.
4. Holds expire after a configurable window; expired holds become available again
   automatically.
5. Discount codes (percentage with optional cap, validity window, usage cap).
6. Configurable refund policies — most generous applicable tier wins.
7. RBAC: admin endpoints locked to `ROLE_ADMIN`; customers can only touch their own bookings.
8. Notifications dispatched **off** the booking critical path (async).
9. Input validation and structured error responses.

## Architecture
- Spring Boot 3.2, Java 17, Maven build.
- MySQL 8 (MariaDB-compatible) in production; H2 in MySQL mode for tests.
- Spring Data JPA with `@Lock(PESSIMISTIC_WRITE)` for seat-row serialization.
- Spring Security HTTP Basic + bcrypt.
- `@EnableScheduling` for hold-expiry; `@EnableAsync` for notifications.
- Mock `PaymentService` (immediate success, refund leg on cancellation).

## Tasks done (2026-06-24)
- Bootstrapped Maven project, Spring Boot deps, MySQL+H2 config, application properties.
- Modelled 12 JPA entities + 12 Spring Data repositories.
- Implemented role-based Spring Security with HTTP Basic + bcrypt and seeded users.
- Built `AdminService`, `BookingService`, `PaymentService`, `NotificationService`,
  `PricingCalculator`, `HoldExpiryScheduler`.
- Exposed REST controllers for auth, admin catalog, customer catalog, and booking flows.
- Wrote 11 tests (3 unit + 5 RBAC + 3 booking integration including concurrent contention
  and hold-expiry); all green.
- Smoke-tested end-to-end against real MySQL.
- Authored README.md, CLAUDE.md (AI workflow), SKILLS.md, design notes, smoke-test doc.
- Initialised local git repo with 8 incremental commits.

## What's been implemented
- Catalog CRUD (create endpoints; GET endpoints for browse).
- Seat layout creation with per-row seat counts and category.
- Show creation with pricing tier; auto-materialization of `ShowSeat` rows.
- Hold → Apply discount → Pay → Confirm flow.
- Cancellation with refund computation via active `RefundPolicy` rows.
- Async confirmation/cancellation notifications (logged).
- Scheduled releaser for expired holds + in-line expiry handling on hold path.
- Concurrent contention guarded by `findAllByIdsForUpdate` + `@Version`.

## Prioritized backlog (P0/P1/P2)
- **P1** Update/Delete endpoints for admin catalog (current scope is create-only).
- **P1** Reminder scheduler that scans bookings whose `show.startTime` is within the next
  N hours and fires `NotificationService.sendReminder`.
- **P2** Pagination/sorting on catalog and bookings.
- **P2** Real email/SMS providers behind the existing async `NotificationService`.
- **P2** Multi-tenant admin scoping (admin per city/theater).
- **P2** Idempotency-key support on payment endpoint.

## Next tasks
- Add `PUT/DELETE` endpoints for cities/theaters/movies/shows/screens.
- Add reminder scheduler.
- Pagination on `/api/catalog/shows` and `/api/bookings/my`.
