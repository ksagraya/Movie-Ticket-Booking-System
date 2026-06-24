# Design Notes (captured during the AI session)

These are the design decisions the assistant and the engineer agreed on
before any code was written. They are kept here verbatim because the
brief asks for "all raw files used during development".

## Entities

- `User` — `(id, username[unique], password, email, role[ADMIN|CUSTOMER])`
- `City` — `(id, name[unique])`
- `Theater` — `(id, name, address, cityId)`
- `Screen` — `(id, name, theaterId)`
- `Seat` — `(id, screenId, rowLabel, seatNumber, category[REGULAR|PREMIUM])`,
  unique `(screen, rowLabel, seatNumber)`.
- `Movie` — `(id, title, durationMinutes, language, genre)`
- `Show` — `(id, movieId, screenId, startTime, basePriceRegular,
   basePricePremium, pricingTier[REGULAR|PREMIUM|WEEKEND])`
- `ShowSeat` — the *materialized* row for one seat × one show:
  `(id, showId, seatId, status[AVAILABLE|HELD|BOOKED],
    holdExpiresAt, heldByUserId, price, version)`
- `Booking` — `(id, userId, showId, status[PENDING|CONFIRMED|CANCELLED|EXPIRED],
   seats[*ShowSeat], subtotal, discountCode, discountAmount, totalAmount,
   createdAt, confirmedAt, cancelledAt, refundAmount)`
- `Payment` — `(id, bookingId[unique], amount, status[SUCCESS|FAILED|REFUNDED],
   reference, createdAt, refundedAt, refundedAmount)`
- `DiscountCode` — `(id, code[unique], percentage, maxDiscount?, validFrom,
   validTo, maxUses?, usedCount, active)`
- `RefundPolicy` — `(id, name, hoursBeforeShow, refundPercentage, active)`

## Pricing

```
priceFor(seatCategory, basePriceRegular, basePricePremium, pricingTier)
  = (seatCategory == PREMIUM ? basePricePremium : basePriceRegular)
  × (pricingTier == WEEKEND ? 1.25
   : pricingTier == PREMIUM ? 1.15
   : 1.0)
```

## Booking state machine

```
        hold()                pay()                cancel()
AVAILABLE ────► HELD ────► PENDING ────► CONFIRMED ────► CANCELLED
                  │                                          ▲
                  └──── (expiry) ──► AVAILABLE               │
                                                             │
                            cancel(PENDING) ─────────────────┘
```

## Locking strategy

- Acquire pessimistic write lock on the requested ShowSeat rows in
  sorted ID order.
- Validate within the locked transaction; flip to HELD.
- The `version` column on ShowSeat is a defence-in-depth for any path
  that bypasses the lock (e.g. JPA flush after entity edit).
- A `@Scheduled` job (`HoldExpiryScheduler`) runs every 30s and
  releases stale HELD rows, but the hold path itself also treats
  expired-HELD as available so a customer never has to wait for the
  scheduler.

## RBAC

- `/api/auth/register`, `/api/auth/me` — public for register, any auth
  for `/me`.
- `/api/admin/**` — `ROLE_ADMIN` only.
- `/api/catalog/**` and `/api/bookings/**` — any authenticated user.
- A customer can only act on their own bookings; admins can act on any
  booking (the brief is silent so we chose the conservative
  interpretation).

## Notifications

- `NotificationService` is `@Async`-annotated; the booking transaction
  commits before the notification is fired, so even if the notification
  fails the booking is durable.
- Today they `log.info`. Swapping in SES / SendGrid / Twilio is a
  one-method change.
