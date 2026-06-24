# Problem Statement (verbatim from the brief)

SDE-2 Take-Home — Movie Ticket Booking System

Movie Ticket Booking System

**Time limit**: 48 hours from assignment.
**Stack**: Spring Boot.

**The Project**

A movie ticket booking system at scale with multiple cities, multiple
theaters per city, multiple shows per theater, and seat-level booking.
The system should support seat selection with timebound holds that
release automatically on expiry, multiple pricing tiers (regular,
premium, weekend) and discount codes, payment, booking confirmation,
and refunds on cancellation under configurable refund policies.
Multiple users may attempt to book the same seat at the same time, and
the system must correctly serialize bookings without double-allocation.
Confirmation and reminder notifications should be delivered without
blocking the booking flow.

**Roles**: admin (manage cities, theaters, shows, seat layouts,
pricing tiers, and refund policies) and customer (browse shows, book
and cancel seats, view booking history).

**In Scope**

- REST APIs covering the core flows
- Persistence to a database of your choice
- Basic role-based access control for the roles defined in the
  requirement
- Input validation and error handling
- README.md and Unit & Integration Tests for the core flows

**Out of Scope**

- UI or frontend
- Deployment, containerization, or CI/CD
- Distributed systems or microservices
- Advanced authentication (OAuth, SSO, MFA)
- Production-grade observability, monitoring, or alerting

**What to Submit**

- GitHub repository link (multiple commits expected)
- README.md
- Agents.md / Claude.md file used during development
- Skills used during development
- All raw files used during development
- A ≤10 minute video covering: approach + high-level solution, the
  tech stack and why, AI workflow used, testing approach.
