package com.example.cardwheel

import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

internal class BackupRestorer(private val database: AppDatabase, directory: File) {
    private val recovery = AtomicFile(File(directory, "before-server-restore.json"))
    fun previous(): List<CardItem> = recovery.openRead().use { stream ->
        val bytes = stream.readBytes()
        require(bytes.size <= BackupCodec.MAX_BYTES)
        BackupCodec.decodeCards(JSONObject(String(bytes, Charsets.UTF_8)))
    }
    fun replace(expected: List<CardItem>, replacement: List<CardItem>) {
        val encoded = BackupCodec.encode(replacement)
        val validated = BackupCodec.decodeCards(JSONObject(encoded))
        database.runInTransaction {
            val current = database.cardDao().getAll()
            if (current != expected) throw BackupProblem("기기의 카드가 변경되었습니다. 목록을 다시 확인해 주세요.")
            val previous = BackupCodec.encode(current).toByteArray(Charsets.UTF_8)
            val output = recovery.startWrite()
            try { output.write(previous); recovery.finishWrite(output) }
            catch (error: Exception) { recovery.failWrite(output); throw error }
            database.cardDao().replaceAll(validated)
        }
    }
}
