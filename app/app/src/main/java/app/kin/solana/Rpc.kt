package app.kin.solana

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class RpcException(message: String) : Exception(message)

class AccountInfo(val address: PublicKey, val data: ByteArray, val owner: PublicKey)

/** Minimal Solana JSON-RPC client. Only the calls Kin needs. */
class SolanaRpc(private val url: String) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json".toMediaType()

    private suspend fun call(method: String, params: JsonArray): JsonElement = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", method)
            put("params", params)
        }.toString()
        val req = Request.Builder().url(url).post(body.toRequestBody(mediaType)).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RpcException("RPC HTTP ${resp.code}")
            val root = json.parseToJsonElement(resp.body!!.string()).jsonObject
            root["error"]?.let { throw RpcException(it.jsonObject["message"]?.jsonPrimitive?.content ?: "RPC error") }
            root["result"] ?: throw RpcException("Empty RPC result")
        }
    }

    private fun decodeAccount(address: String, value: JsonElement): AccountInfo? {
        if (value is kotlinx.serialization.json.JsonNull) return null
        val o = value.jsonObject
        val b64 = o["data"]!!.jsonArray[0].jsonPrimitive.content
        return AccountInfo(
            PublicKey.fromBase58(address),
            Base64.decode(b64, Base64.DEFAULT),
            PublicKey.fromBase58(o["owner"]!!.jsonPrimitive.content),
        )
    }

    suspend fun latestBlockhash(): ByteArray {
        val r = call("getLatestBlockhash", buildJsonArray { add(buildJsonObject { put("commitment", "confirmed") }) })
        return Base58.decode(r.jsonObject["value"]!!.jsonObject["blockhash"]!!.jsonPrimitive.content)
    }

    suspend fun accountInfo(address: PublicKey): AccountInfo? {
        val r = call(
            "getAccountInfo",
            buildJsonArray {
                add(address.toBase58())
                add(buildJsonObject { put("encoding", "base64"); put("commitment", "confirmed") })
            },
        )
        return decodeAccount(address.toBase58(), r.jsonObject["value"]!!)
    }

    suspend fun multipleAccounts(addresses: List<PublicKey>): List<AccountInfo?> {
        if (addresses.isEmpty()) return emptyList()
        val r = call(
            "getMultipleAccounts",
            buildJsonArray {
                add(buildJsonArray { addresses.forEach { add(it.toBase58()) } })
                add(buildJsonObject { put("encoding", "base64"); put("commitment", "confirmed") })
            },
        )
        val values = r.jsonObject["value"]!!.jsonArray
        return addresses.mapIndexed { i, a -> decodeAccount(a.toBase58(), values[i]) }
    }

    /** Program accounts whose data contains [memcmp] bytes at the given offsets. */
    suspend fun programAccounts(programId: PublicKey, memcmp: List<Pair<Int, ByteArray>>): List<AccountInfo> {
        val r = call(
            "getProgramAccounts",
            buildJsonArray {
                add(programId.toBase58())
                add(
                    buildJsonObject {
                        put("encoding", "base64")
                        put("commitment", "confirmed")
                        put(
                            "filters",
                            buildJsonArray {
                                memcmp.forEach { (offset, bytes) ->
                                    add(
                                        buildJsonObject {
                                            put(
                                                "memcmp",
                                                buildJsonObject {
                                                    put("offset", offset)
                                                    put("bytes", Base58.encode(bytes))
                                                },
                                            )
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            },
        )
        return r.jsonArray.mapNotNull { item ->
            val o = item.jsonObject
            decodeAccount(o["pubkey"]!!.jsonPrimitive.content, o["account"]!!)
        }
    }

    /** Raw token balance (base units) of a token account, or 0 if it does not exist. */
    suspend fun tokenBalance(tokenAccount: PublicKey): Long = try {
        val r = call("getTokenAccountBalance", buildJsonArray { add(tokenAccount.toBase58()) })
        r.jsonObject["value"]!!.jsonObject["amount"]!!.jsonPrimitive.content.toLong()
    } catch (e: RpcException) {
        0L
    }

    suspend fun clusterTime(): Long {
        val slot = call("getSlot", buildJsonArray { }).jsonPrimitive.content.toLong()
        val t = call("getBlockTime", buildJsonArray { add(JsonPrimitive(slot)) })
        return t.jsonPrimitive.content.toLong()
    }
}
