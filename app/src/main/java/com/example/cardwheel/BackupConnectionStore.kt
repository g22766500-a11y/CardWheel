package com.example.cardwheel

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class BackupConnectionStore(context: Context) {
    private val preferences = context.getSharedPreferences("backup_connection", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    fun load(): BackupConnection? {
        val saved = preferences.getString("encrypted", null) ?: return null
        val parts = saved.split(':')
        require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        val data = JSONObject(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8))
        if (data.optString("kind") != "mariadb-v1") return null
        return BackupConnection.checked(data.getString("host"), data.getInt("port").toString(), data.getString("database"), data.getString("user"), data.getString("password"), data.getBoolean("tls"), data.optString("certificate"))
    }
    fun save(connection: BackupConnection) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = JSONObject().put("kind", "mariadb-v1").put("host", connection.host).put("port", connection.port)
            .put("database", connection.database).put("user", connection.user).put("password", connection.password)
            .put("tls", connection.tls).put("certificate", connection.certificate).toString()
        val encrypted = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(data.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        check(preferences.edit().putString("encrypted", encrypted).commit())
    }
    fun clear() { check(preferences.edit().clear().commit()) }
    companion object { private const val ALIAS = "cardwheel.backup.connection.v1" }
}
