# Đặt Sân backend

## OpenAPI and Swagger UI

The complete backend contract is [openapi.yaml](src/main/resources/static/openapi.yaml). It covers 59 operations across authentication, profiles, sports, venues, courts, bookings, payments, reviews, venue services and rental photos. Edit this file when backend request or response shapes change. The earlier `openapi-booking.yaml` and `openapi-services.yaml` describe individual parts of the API.

Start the backend from `Backend/` with the intended MySQL database configured:

```powershell
.\mvnw.cmd spring-boot:run
```

Open [Swagger UI](http://localhost:8080/api/v1/swagger-ui.html) or download [OpenAPI YAML](http://localhost:8080/api/v1/openapi.yaml). Replace port 8080 if `PORT` is configured. Swagger UI 5.36.0 is bundled locally with the application and sends requests to `/api/v1` on the backend serving the specification.

To call a protected endpoint, register a CUSTOMER using `POST /auth/register`, log in using `POST /auth/login`, copy `data.accessToken`, then click **Authorize** and paste the token without a `Bearer ` prefix. Use an owner account created through the provisioning command below for owner operations. Use a new request key for a distinct booking attempt. Replaying the same booking with the same customer, key and interval returns the existing booking.

For local HTTP refresh-cookie testing, set `APP_AUTH_SECURE_COOKIE=false` before starting the backend. Swagger UI sends same-origin cookies. Refresh rotates the access token and cookie, so update **Authorize** with the new access token. After logout, also clear the token in **Authorize**. Authorization is kept in memory while the documentation page is open.

A booking walkthrough uses actual IDs from `GET /venues` and `GET /venues/{venueId}/courts`, then calls availability, quote and `POST /bookings` with a future interval. The OpenAPI examples use illustrative IDs and dates that should be adjusted to the database and day of testing. Requests executed through Swagger UI affect the configured database.

The backend uses URI versioning with the application context path `/api/v1`. The paths shown in OpenAPI, such as `/auth/login` and `/bookings`, are relative to that base. Full URLs include `/api/v1/auth/login`, `/api/v1/venues` and `/api/v1/bookings`. The frontend development proxy forwards `/api/v1` intact and defaults to `http://127.0.0.1:8080`. Set `API_PROXY_TARGET` for a different backend port.

The versioning policy keeps the v1 contract stable for existing clients. Future changes that break compatibility should use a new major API version. This implementation exposes the initial v1 application. Spring Security matchers and controller mappings are relative to the context path. [Spring Boot context path](https://docs.spring.io/spring-boot/appendix/application-properties/index.html), [Spring Security path matching](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html)

Booking requests use `court: {id}`. The contract describes current MVC and security error formats, local versus offset date-times, and public and role-restricted operations. Payment APIs currently create database records. Provider callbacks and real refunds are still future work.

The documentation and its assets allow anonymous GET requests. Existing API authorization continues to apply when using **Try it out**. Run `./mvnw.cmd -Dtest=AuthApiTests test` to check static documentation access and authentication behavior.

## Backend authentication

The backend serves these routes relative to its `/api/v1` base URL. For example, `POST /auth/login` is reached at `POST /api/v1/auth/login`.

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

`BookingMysqlTests` runs the real Spring MVC and JPA code for booking, availability, quote, venue search and venue services. Set the test URL to enable the suite.

## Venue services and equipment

Apply [migration V002](migrations/V002__venue_services_and_rentals.sql) once after V001 for a managed schema. Hibernate creates the additional tables and columns on local setups using schema update.

1. The venue owner uses `POST /venues/{venueId}/services` to create a `CONSUMABLE` with `stockOnHand` or a `RENTAL` with separately registered assets. Specify `unitPrice` in VND per item or per rental for the booking. `GET /venues/{venueId}/services` shows the catalog. Use `PUT /venues/{venueId}/services/{serviceId}` to update the price, inventory and availability.
2. The owner uses `POST /venues/{venueId}/services/{serviceId}/assets` with a code such as `VOT-007`. The same collection supports listing the assets. Use `PUT` on an asset ID to set `AVAILABLE`, `MAINTENANCE` or `RETIRED`.
3. The booking customer uses `POST /bookings/{bookingId}/services` with `{ "serviceId": 1, "quantity": 2 }` and an `Idempotency-Key` header. This holds stock and copies the price to the order. The customer can order before arrival and after check-in until the slot ends. For an undelivered order, `DELETE /bookings/{bookingId}/services/{orderId}` releases the hold. Booking cancellation and expiry also release inventory.
4. The owner uses `POST /bookings/{bookingId}/check-in` during the scheduled interval, then `POST /bookings/{bookingId}/services/{orderId}/deliver`. Send `{}` to confirm delivery of a consumable or include `assetIds` matching the reserved equipment. Delivery decrements consumable stock or marks the equipment as handed out.
5. The customer uploads photos with `POST /rentals/{rentalId}/photos` using multipart fields `phase`, `angle` and `file`. Send `OVERVIEW` and `DETAIL` for both `CHECK_IN` and `CHECK_OUT`. Files accept JPEG or PNG up to 5 MiB per image. The customer and venue owner can list photos, download them through the existing bearer-authenticated route, or get a five-minute viewing link at `GET /rentals/{rentalId}/photos/{photoId}/link`. Use the rental ID from the order response.
6. Either party can call `POST /bookings/{bookingId}/check-out` once each handed-out asset has all four photos. Bookings with only consumables or court time can check out directly after check-in. Check-out records the return, releases undelivered reservations and completes the booking. `GET /bookings/{bookingId}/services` returns the history and the total for the court and delivered items.

See [the services OpenAPI contract](openapi-services.yaml). Run `./mvnw.cmd '-Dtest=AddonApiTests,AddonServiceTests,BookingMysqlTests' test` to exercise the API, service logic and MySQL scenarios.

### Cloudinary rental photos

Set `CLOUDINARY_URL` in the backend process environment using the format `cloudinary://<API_KEY>:<API_SECRET>@<CLOUD_NAME>`. New photos are uploaded as `authenticated` assets to the `rental-photos` folder in a Cloudinary environment using dynamic folders. Set `APP_CLOUDINARY_ASSET_FOLDER` to use a different folder. For a **Media Library User** key, grant **Editor** on that folder through the Cloudinary Admin API. [Cloudinary folder permissions for API keys](https://cloudinary.com/documentation/permissions_assign_roles_api#assign_folder_roles_via_the_admin_api)

Apply [migration V003](migrations/V003__cloudinary_rental_photos.sql) before deploying against a database that already has the `rental_photos` table. New records store the Cloudinary public ID and checksum. Existing photos remain readable through the download route until migrated. A retake gets its own Cloudinary asset, and the order history links to the latest accepted photo for that angle.

To migrate existing photos, run this from `Backend/` with the intended database and Cloudinary environment configured:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--migrate-rental-photos'
```

The command verifies each uploaded copy before clearing that row's legacy bytes. It can be rerun after a failure. The schema keeps the nullable legacy column during the transition.
