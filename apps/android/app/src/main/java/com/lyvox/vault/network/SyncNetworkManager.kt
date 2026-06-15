package com.lyvox.vault.network

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SyncNetworkManager {
    private val secureRandom = SecureRandom()

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            gson {
                setPrettyPrinting()
                disableHtmlEscaping()
            }
        }
    }

    suspend fun testConnection(endpoint: String): Boolean {
        return try {
            val response = client.get("$endpoint/ping")
            response.status.isSuccess()
        } catch (e: Exception) {
            Log.e("SyncNetworkManager", "Test connection failed.")
            false
        }
    }

    data class SyncRequestPayload(
        val session_id: String,
        val sync_data: String,
        val auth_nonce: String,
        val auth_tag: String
    )

    data class SyncResponse(
        val success: Boolean,
        val message: String,
        val sync_data: String?
    )

    suspend fun sendSyncData(endpoint: String, sessionId: String, syncPassword: String, syncDataString: String): SyncResponse? {
        return try {
            val nonce = generateNonce()
            val requestPayload = SyncRequestPayload(
                session_id = sessionId,
                sync_data = syncDataString,
                auth_nonce = nonce,
                auth_tag = buildAuthTag(syncPassword, sessionId, nonce, syncDataString)
            )
            
            val response = client.post("$endpoint/sync") {
                contentType(ContentType.Application.Json)
                setBody(requestPayload)
            }
            if (response.status.isSuccess()) {
                response.body<SyncResponse>()
            } else {
                Log.e("SyncNetworkManager", "Sync failed with HTTP status.")
                null
            }
        } catch (e: Exception) {
            Log.e("SyncNetworkManager", "Sync failed.")
            null
        }
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun buildAuthTag(syncPassword: String, sessionId: String, nonce: String, syncData: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(syncPassword.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        mac.update(sessionId.toByteArray(Charsets.UTF_8))
        mac.update('\n'.code.toByte())
        mac.update(nonce.toByteArray(Charsets.UTF_8))
        mac.update('\n'.code.toByte())
        mac.update(syncData.toByteArray(Charsets.UTF_8))
        return mac.doFinal().joinToString("") { "%02x".format(it) }
    }
}
