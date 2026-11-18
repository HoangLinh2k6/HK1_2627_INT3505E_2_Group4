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
