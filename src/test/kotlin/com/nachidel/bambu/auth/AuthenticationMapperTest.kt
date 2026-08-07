package com.nachidel.bambu.auth

import com.nachidel.bambu.dto.auth.LoginResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthenticationMapperTest {

    @Test
    fun `access token means authentication succeeded`() {

        val response =
            LoginResponse(
                accessToken = "test-access-token"
            )

        val result =
            AuthenticationMapper.map(response)

        val authenticated =
            assertIs<
                    AuthenticationResult.Authenticated
                    >(result)

        assertEquals(
            "test-access-token",
            authenticated.accessToken.value
        )
    }


    @Test
    fun `verifyCode login type requires verification code`() {

        val response =
            LoginResponse(
                accessToken = "",
                loginType = "verifyCode"
            )

        val result =
            AuthenticationMapper.map(response)

        assertIs<
                AuthenticationResult.VerificationCodeRequired
                >(result)
    }


    @Test
    fun `unknown unsuccessful response is rejected`() {

        val response =
            LoginResponse(
                code = "example-error",
                message = "Authentication rejected"
            )

        val result =
            AuthenticationMapper.map(response)

        val rejected =
            assertIs<
                    AuthenticationResult.Rejected
                    >(result)

        assertEquals(
            "example-error",
            rejected.code
        )

        assertEquals(
            "Authentication rejected",
            rejected.message
        )
    }
}