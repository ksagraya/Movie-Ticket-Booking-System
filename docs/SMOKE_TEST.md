# End-to-End Smoke Test

A curl-only walkthrough that exercises every core flow. Run the app first:

```bash
mvn spring-boot:run
# expects port 8090
```

```bash
BASE=http://localhost:8090

# --- ADMIN catalog setup ---
curl -s -u admin:admin123 -X POST $BASE/api/admin/cities \
  -H "Content-Type: application/json" -d '{"name":"Bangalore"}'

curl -s -u admin:admin123 -X POST $BASE/api/admin/theaters \
  -H "Content-Type: application/json" \
  -d '{"name":"PVR Forum","address":"Koramangala","cityId":1}'

curl -s -u admin:admin123 -X POST $BASE/api/admin/screens \
  -H "Content-Type: application/json" -d '{
    "name":"Screen 1","theaterId":1,
    "seatLayout":[
      {"rowLabel":"A","seatsCount":5,"category":"REGULAR"},
      {"rowLabel":"B","seatsCount":3,"category":"PREMIUM"}
    ]
  }'

curl -s -u admin:admin123 -X POST $BASE/api/admin/movies \
  -H "Content-Type: application/json" \
  -d '{"title":"Dune 3","durationMinutes":160,"language":"EN","genre":"SciFi"}'

curl -s -u admin:admin123 -X POST $BASE/api/admin/shows \
  -H "Content-Type: application/json" -d '{
    "movieId":1,"screenId":1,
    "startTime":"2027-02-10T18:30:00",
    "basePriceRegular":200.00,"basePricePremium":400.00,
    "pricingTier":"WEEKEND"
  }'

curl -s -u admin:admin123 -X POST $BASE/api/admin/refund-policies \
  -H "Content-Type: application/json" \
  -d '{"name":"Standard 24h","hoursBeforeShow":24,"refundPercentage":80}'

curl -s -u admin:admin123 -X POST $BASE/api/admin/discount-codes \
  -H "Content-Type: application/json" \
  -d '{"code":"WELCOME10","percentage":10,
       "validFrom":"2025-01-01T00:00:00","validTo":"2030-01-01T00:00:00"}'

# --- CUSTOMER booking flow ---
curl -s -u customer:customer123 $BASE/api/catalog/shows/1/seats

curl -s -u customer:customer123 -X POST $BASE/api/bookings/hold \
  -H "Content-Type: application/json" \
  -d '{"showId":1,"showSeatIds":[1,2,6]}'

curl -s -u customer:customer123 -X POST $BASE/api/bookings/1/apply-discount \
  -H "Content-Type: application/json" -d '{"code":"WELCOME10"}'

curl -s -u customer:customer123 -X POST $BASE/api/bookings/1/pay

curl -s -u customer:customer123 $BASE/api/bookings/my

curl -s -u customer:customer123 -X POST $BASE/api/bookings/1/cancel
```

Expected highlights:
- Hold returns HTTP 201 with status `PENDING` and subtotal computed from
  the WEEKEND multiplier.
- Apply-discount drops 10% off subtotal.
- Pay flips booking to `CONFIRMED` and seats to `BOOKED`.
- Cancel returns 80% refund (show is > 24h away) and frees the seats.
