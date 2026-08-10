# Authentication

`bambu-cloud-kotlin` supports an existing Bambu Cloud access token and the observed email/password
authentication flow.

> The authentication endpoints and response behaviour used here are unofficial and may change.

## Never hard-code credentials

Recommended environment variables for local development:

```text
BAMBU_EMAIL
BAMBU_PASSWORD
BAMBU_TOKEN
```

Do not commit their values.

## Existing access token

If an access token is already available:

```kotlin
import com.nachidel.bambu.api.BambuCloudClient
import com.nachidel.bambu.value.AccessToken

val token =
    System.getenv("BAMBU_TOKEN")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: error("BAMBU_TOKEN missing")

val bambu =
    BambuCloudClient {
        accessToken =
            AccessToken(token)
    }
```

The token should be treated as a secret.

## Email/password login

```kotlin
when (
    val result =
        bambu.login(
            email = email,
            password = password
        )
) {
    is AuthenticationResult.Authenticated -> {
        val token =
            result.accessToken
    }

    AuthenticationResult.VerificationCodeRequired -> {
        // Ask the user for the code sent by email.
    }

    is AuthenticationResult.Rejected -> {
        error(
            "Authentication rejected: " +
                "${result.code} - ${result.message}"
        )
    }
}
```

## Verification code

A password login can return `VerificationCodeRequired` instead of an access token.

Call:

```kotlin
val verification =
    bambu.verifyCode(
        email = email,
        code = codeFromEmail
    )
```

Then handle the result again:

```kotlin
when (verification) {

    is AuthenticationResult.Authenticated -> {
        // Authentication completed.
    }

    AuthenticationResult.VerificationCodeRequired -> {
        // The current code was not sufficient / a new code is required.
    }

    is AuthenticationResult.Rejected -> {
        // Authentication rejected.
    }
}
```

Do not automatically retry password authentication in a loop after a 401.

## Expired or invalid token

Cloud operations can fail with `BambuAuthenticationException` when the current token is no longer accepted.

Example:

```kotlin
try {
    bambu.connect()
} catch (e: BambuAuthenticationException) {
    // Re-authentication is required.
}
```

## Refresh-token behaviour

Refresh-token lifecycle semantics have not been established sufficiently by this project and should not be
assumed.

Applications should therefore be prepared to re-authenticate when the cloud rejects the current access
token.

## Observed HTTP surface

During implementation, the following Bambu Cloud HTTP paths have been observed:

```text
POST /v1/user-service/user/login
GET  /v1/design-user-service/my/preference
GET  /v1/iot-service/api/user/bind
```

These paths are implementation observations, not a compatibility guarantee.

## Secret-handling rules

Never publish or log:

```text
account password
access token
refresh token
verification code
tfa-related key material
device access code
live-view credentials
signed download/live URLs
```

When sharing diagnostic logs, anonymize account IDs, printer serials and other device identifiers.
