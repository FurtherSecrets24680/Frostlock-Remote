package com.frostlock.remote

import com.frostlock.remote.data.PairingInvite
import org.junit.Assert.assertEquals
import org.junit.Test

class PairingCodeCryptoDeviceTest {
    @Test
    fun decryptsWindowsCompatibleCodeOnAndroid() {
        val invite = PairingInvite.parse(CROSS_PLATFORM_CODE, nowSeconds = 1_800_000_000)
        assertEquals("https://192.168.1.10:44123", invite.endpoint)
        assertEquals("Desk PC", invite.pcName)
    }

    private companion object {
        const val CROSS_PLATFORM_CODE = "FL1.2MqBON56fJD3Kl8ueeiMb-6NqzcyO60Ze0ypFPwCOy4ymfGo4xMl8WYYkB8xKmAmkIKhSQ5PsAmFyRjMFzo-7JWXiMSKJRzwDG6ztCHBnsIKoY7Q1n8NgZou8JvQRDdM1lnYqlW3nsKP0ErB5nUSl4A0qivrgzqk8FzWDkdikvSHFnMUrt4o44S7riLpbNwXvuDURrkWYvTcTT4x2DsDQ6dj6O9BUDMPMbX_KN8Zr4eNIaM1l9j8wTPQTSOQF7m9a3ReuqVquTg_PNNSDIkqUqzaDa7UaW6lxj2HFK0-J1N2p71uAuemdxLOQHF1_KwC6W4QtwKt9pTUWt-Z_sauFcZdxUGIBEIpVKf-tMTm8Vsvey62LwXTRzorfc63yU_1vOtbOWIC_JZ3qAzURy0s3izslBUyicQO-xLo154OlmY7lqtuJHkqQiR2LU0x5-4pWh0LMML3-UbJFabXxcBZPXwZ2KF70aiY7IhUY7KaFbKyN34wWNODJ2fClJURPxzy"
    }
}
