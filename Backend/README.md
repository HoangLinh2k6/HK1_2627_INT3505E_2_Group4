# Backend authentication

The backend serves these routes at its existing base URL.

| Method and path | Behavior |
| --- | --- |
| `POST /auth/register` | Accepts `email`, `password`, `fullName`, and `phone`. Creates a customer and returns `201` with the profile. |
| `POST /auth/login` | Accepts `email` and `password`. Returns `{ data: { accessToken, tokenType, expiresIn, user } }` and sets the `keosan_refresh` cookie. |
| `POST /auth/refresh` | Reads the refresh cookie, replaces both tokens and returns the same response shape as login. The access token lasts 15 minutes and the refresh cookie lasts 7 days after login or refresh. |
| `POST /auth/logout` | Revokes the current session using the bearer token or refresh cookie, clears the cookie and returns `204`. |
| `GET /users/me`, `PUT /users/me` | Gets or updates the authenticated profile. The update accepts `fullName` and `phone`. |

Send `Authorization: Bearer <accessToken>` on private API calls and include credentials on login, refresh and logout so the browser can receive or send the refresh cookie. Use HTTPS and expose the API through the same browser origin as the app. The refresh cookie is `HttpOnly`, `Secure` and `SameSite=Strict` by default.

For local HTTP behind a Vite proxy, set `APP_AUTH_SECURE_COOKIE=false` and `APP_AUTH_ALLOWED_ORIGINS` to the exact browser origin, such as `http://localhost:5173`. The latter accepts comma-separated origins. `APP_AUTH_COOKIE_PATH` defaults to `/` and can be narrowed to the browser-visible auth path, such as `/api/v1/auth`. The current frontend adapter still handles auth locally and needs a separate update to call these routes and forward credentials.

The existing Spring JPA schema update creates the `auth_sessions` table in the configured MySQL database. It holds token hashes and expiry times. Global booking and payment lists and hard deletion of bookings are closed. Use the authenticated booking and payment routes, and cancel bookings through the cancel action.

## Create an owner or admin, or reset a legacy account

Run against the intended backend database from `Backend/`. For an existing email, supply its current role. The command updates the password, revokes its sessions and retains the account ID, role and existing records. For a new account, also supply a name. Use `CUSTOMER` for a legacy customer.

```powershell
$env:AUTH_ACCOUNT_EMAIL = 'owner@example.test'
$env:AUTH_ACCOUNT_NAME = 'Venue Owner'
$env:AUTH_ACCOUNT_ROLE = 'OWNER'
$keosanPassword = Read-Host 'Account password' -AsSecureString
$env:AUTH_ACCOUNT_PASSWORD = [System.Net.NetworkCredential]::new('', $keosanPassword).Password
try {
    .\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--provision-auth-account'
} finally {
    Remove-Item Env:AUTH_ACCOUNT_PASSWORD
    $keosanPassword.Dispose()
}
```

Run the auth HTTP tests without a database using `./mvnw.cmd -Dtest=AuthApiTests test`. The tests exercise the security filter, controller and token service with mocked persistence.

## Court prices, availability and search

Set `basePricePerHour` in VND when creating or updating a court. Courts without a configured price remain visible in the catalog and have no bookable slots. Existing booking totals are preserved. For a managed schema, apply [migration V001](migrations/V001__court_pricing_and_booking_idempotency.sql) once to the existing tables before deploying. Local setups using Hibernate schema update get the new columns and constraint at startup.

Availability, quote and booking use 30-minute slots starting at the venue's opening time in `Asia/Ho_Chi_Minh`. Each slot starting from 17:00 through 21:59 adds 100,000 VND to the hourly rate. A new booking uses the server quote and holds the interval for up to 30 minutes, ending sooner if play starts first. The optional `Idempotency-Key` can be reused by the same customer to replay the same request. Use a new key for a different court or interval.

`GET /venues` accepts `q`, `city`, `district`, `sportTypeId` and `maxPrice`. Provide `start` and `end` for an exact interval, or `date` with optional `from` and `to` for any available slot within that window. `maxPrice` compares against the base hourly rate without a time filter and the effective hourly rate with one. Court criteria apply to a single court. Pagination remains zero-based. See the [booking OpenAPI contract](openapi-booking.yaml) for query and response shapes.

### MySQL integration tests

Create an empty MySQL database named `booking_test_local` and give a test account schema privileges. The suite uses Hibernate `create-drop` in that database and rejects database names outside the `booking_test_` prefix. From `Backend/`, run:

```powershell
$env:BOOKING_TEST_MYSQL_URL = 'jdbc:mysql://127.0.0.1:3306/booking_test_local'
$env:BOOKING_TEST_MYSQL_USER = 'booking_test'
$env:BOOKING_TEST_MYSQL_PASSWORD = '<test database password>'
.\mvnw.cmd '-Dtest=BookingMysqlTests,CourtScheduleTests,AuthApiTests' test
```

`BookingMysqlTests` runs the real Spring MVC and JPA code for booking, availability, quote and venue search. Set the test URL to enable the suite.
