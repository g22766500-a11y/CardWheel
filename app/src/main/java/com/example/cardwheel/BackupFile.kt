package com.example.cardwheel

import java.io.InputStream
import org.json.JSONObject

internal object BackupFile {
    fun encode(cards: List<CardItem>): ByteArray {
        val bytes = JSONObject(BackupCodec.encode(cards)).put("revision", 1)
            .put("savedAt", System.currentTimeMillis()).toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= BackupCodec.MAX_BYTES)
        return bytes
    }
    fun read(input: InputStream): RemoteBackup {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= BackupCodec.MAX_BYTES)
            output.write(buffer, 0, count)
        }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        val remote = BackupCodec.remote(decoder.decode(java.nio.ByteBuffer.wrap(output.toByteArray())).toString())
        require(remote.revision > 0)
        return remote
    }
}
