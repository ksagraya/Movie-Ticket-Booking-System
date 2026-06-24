# Movie Ticket Booking System — Workspace

The actual Spring Boot deliverable for this take-home lives in:

### **[`movie-booking-system/`](movie-booking-system/)**

That directory is a self-contained Maven project. All submission artefacts
(README, CLAUDE.md, SKILLS.md, tests, raw AI artefacts) are inside it.

## Quick links

- [`movie-booking-system/README.md`](movie-booking-system/README.md) — full project README
- [`movie-booking-system/CLAUDE.md`](movie-booking-system/CLAUDE.md) — AI workflow used during development
- [`movie-booking-system/SKILLS.md`](movie-booking-system/SKILLS.md) — engineering skills exercised
- [`movie-booking-system/docs/ai-artifacts/`](movie-booking-system/docs/ai-artifacts/) — raw AI artefacts
- [`movie-booking-system/docs/SMOKE_TEST.md`](movie-booking-system/docs/SMOKE_TEST.md) — runnable end-to-end curl walkthrough

## Run

```bash
cd movie-booking-system
mvn test           # 11/11 green
mvn spring-boot:run
```

Seeded users:

| Username   | Password      | Role     |
|------------|---------------|----------|
| `admin`    | `admin123`    | ADMIN    |
| `customer` | `customer123` | CUSTOMER |

App listens on `http://localhost:8090`. See
[`movie-booking-system/README.md`](movie-booking-system/README.md) for the
full REST API, concurrency design, pricing/refund logic, and architecture
deep-dive.
