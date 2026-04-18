package com.positions.aggregator.data.kraken

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object KrakenSigner {

    fun sign(
        path: String,
        postData: String,
        apiSecretBase64: String
    ): String {
        val secret = Base64.decode(apiSecretBase64.trim(), Base64.DEFAULT)
        val sha256 = MessageDigest.getInstance("SHA-256")
        val hash = sha256.digest(postData.toByteArray(Charsets.UTF_8))
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(secret, "HmacSHA512"))
        val pathBytes = path.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(pathBytes.size + hash.size)
        System.arraycopy(pathBytes, 0, payload, 0, pathBytes.size)
        System.arraycopy(hash, 0, payload, pathBytes.size, hash.size)
        val signature = mac.doFinal(payload)
        return Base64.encodeToString(signature, Base64.NO_WRAP)
    }
}
