# CLAUDE.md / Agents.md — AI Workflow Notes

This file documents how an AI coding assistant (Claude / "E1") was used to
scaffold and iterate on this Spring Boot service. It serves as the
`Agents.md` requested by the brief.

## 1. The model and tools used

- **Primary model**: Claude (Anthropic) acting as the lead pair-programmer.
- **Tools the model could call**: filesystem read/write, shell, Maven, MySQL
  CLI. No human-side IDE plugin — all interactions happened through plain
  tool calls in a controlled sandbox.
- **Why this stack vs an autocomplete tool**: a multi-file project with a
  non-trivial concurrency story benefits from an agent that can compile,
  run tests, read failures, and iterate. Inline autocomplete would have
  shipped subtly broken concurrency code.

## 2. Workflow

```
Brief ────► Clarify ────► Design ────► Scaffold ────► Iterate ──┐
                                                                │
            ┌───────────────────────────────────────────────────┘
            ▼
   compile → unit tests → integration tests → fix red → repeat
            ▼
   smoke-test against MySQL  ─────► docs (README, CLAUDE.md, SKILLS.md)
```

### 2.1 Clarify
Before any code, the assistant elicited five explicit decisions from the
user:

1. Database → **MySQL**
2. Auth → **HTTP Basic + bcrypt**
3. Seat hold strategy → **pessimistic DB locking + scheduled releaser**
4. Notifications → **in-process async**
5. Payments → **mock service**

These constraints were carried through every later design choice.

### 2.2 Design (mental model, not a doc dump)
The assistant decided on the entity model in one pass, with two
non-obvious calls:

- **Materialize `ShowSeat`** (one row per seat × show) instead of computing
  availability on the fly. This makes seat-level locking a single SQL
  `SELECT … FOR UPDATE` against the row that actually represents
  contention.
- **Sort the requested seat IDs before locking** to guarantee a global lock
  order and prevent deadlocks when two transactions request overlapping
  seat sets.

### 2.3 Scaffold
Files were created in roughly this order, leveraging parallel file
creation where they were independent:

1. `pom.xml` + `application.properties` (main + test)
2. All entities and enums in parallel
3. Repositories in parallel
4. Security config + `UserDetailsService`
5. DTO request/response records
6. Services (`AdminService`, `BookingService`, `PaymentService`,
   `NotificationService`, `PricingCalculator`)
7. `HoldExpiryScheduler`
8. Controllers
9. `DataInitializer` (seed admin + customer)
10. Tests
11. Docs

### 2.4 Iterate
- First compile → green.
- First test run → **one failure**: `expired_hold_releases_seat_for_new_hold`
  was hitting a unique-constraint violation on `booking_seats.show_seat_id`.
  Root cause: a JPA `@OneToMany` with `@JoinTable` adds an implicit `UNIQUE`
  on the inverse FK; once a hold expired and a new booking was created for
  the same seat, the join insert failed. Fix: change the relationship to
  `@ManyToMany`, since a seat can historically belong to multiple bookings
  (cancelled, expired, then re-booked).
- Second test run → 11/11 green.
- Smoke test against real MySQL → full flow OK end-to-end.

### 2.5 Doc generation
README.md, CLAUDE.md (this file), and SKILLS.md were written in a single
pass at the end, after the implementation stabilised, so they would not
drift from the code.

## 3. Prompts and conversation pattern

The driver-prompts that kept the model on rails were:

- "Plan before coding. Don't add error handling, fallbacks, or
  validation for scenarios that can't happen."
- "Use search_replace for existing files, create_file only for new ones."
- "Sort the seat IDs before locking" — single-sentence design guidance
  inserted by the engineer when reviewing the proposed locking strategy.
- "Run mvn test and report only the failures" — keeps output focused.

The assistant was *not* allowed to silently move past a failing test;
every red bar was inspected and addressed before continuing.

## 4. Things the AI got wrong / where humans intervened

- First test draft used helper methods named `post()` and `get()` that
  collided with the static `MockMvcRequestBuilders.post/get` imports —
  human caught the compile error and the assistant renamed them to
  `doPost`/`doGet`.
- The `@OneToMany` → `@ManyToMany` fix above only became obvious once a
  test reproduced the join-table constraint violation.

## 5. What stayed human

- Choice of which features to scope in (refund policies, discounts,
  pricing tiers, async notifications) for breadth.
- Naming conventions (`ShowSeat` vs `BookableSeat`, `pricingTier` field
  on `Show` instead of a per-seat tier table).
- The decision to keep tests JVM-only (H2) for portability rather than
  spinning up MySQL in CI.

## 6. Raw artifacts

See `docs/ai-artifacts/` for the verbatim files the assistant produced or
read during the session (problem statement, design notes, smoke-test
script).
