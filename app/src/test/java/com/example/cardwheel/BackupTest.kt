package com.example.cardwheel

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.room.Room
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import java.io.File
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class BackupTest {
    private val card = CardItem(id = 7, company = "KB국민카드", cardName = "백업 카드", status = "원본 상태", requiredSpend = Int.MAX_VALUE,
        currentSpend = 1234, rewardAmount = 10000, issueDate = -1L, spendDeadline = 1735689600000L,
        rewardReceived = true, cancelled = true, memo = "한글 메모 ' \" 줄바꿈\n")

    @Test fun fileBackupPreservesFieldsAndSupportsEmptySnapshots() {
        assertEquals(listOf(card), BackupFile.read(BackupFile.encode(listOf(card)).inputStream()).cards)
        assertTrue(BackupFile.read(BackupFile.encode(emptyList()).inputStream()).cards.isEmpty())
    }
    @Test fun fileBackupRejectsOversizeTrailingContentAndMalformedEncoding() {
        val good = BackupFile.encode(listOf(card))
        for (bytes in listOf(ByteArray(BackupCodec.MAX_BYTES + 1), good + "garbage".toByteArray(), byteArrayOf(0xc3.toByte(), 0x28))) {
            try { BackupFile.read(bytes.inputStream()); fail("Invalid file accepted") } catch (expected: Exception) { }
        }
    }
    @Test fun roundTripPreservesEveryRoomFieldAndSortsIds() {
        val cards = listOf(card.copy(id = 2), card)
        val raw = BackupCodec.encode(cards)
        assertEquals(cards.sortedByDescending { it.id }, BackupCodec.decodeCards(JSONObject(raw)))
        val remote = JSONObject(raw).put("revision", 2).put("savedAt", 1735689600000L).toString()
        assertEquals(2L, BackupCodec.remote(remote).revision)
        assertEquals(cards.sortedByDescending { it.id }, BackupCodec.remote(remote).cards)
    }
    @Test fun rejectsOverflowDuplicateIdsInvalidFlagsAndUnknownVersions() {
        val raw = BackupCodec.encode(listOf(card))
        for (invalid in listOf(raw.replace(Int.MAX_VALUE.toString(), "2147483648"), raw.replace(Int.MAX_VALUE.toString(), "1.5"),
            raw.replace("\"rewardReceived\":true", "\"rewardReceived\":1"), raw.replace("\"schemaVersion\":1", "\"schemaVersion\":2"))) {
            try { BackupCodec.decodeCards(JSONObject(invalid)); fail(invalid) } catch (expected: Exception) { }
        }
        try { BackupCodec.encode(listOf(card, card)); fail() } catch (expected: Exception) { }
    }
    @Test fun emptyBackupIsDifferentFromNoServerBackup() {
        val raw = JSONObject(BackupCodec.encode(emptyList()))
        assertEquals(emptyList<CardItem>(), BackupCodec.remote(raw.put("revision", 0).put("savedAt", JSONObject.NULL).toString()).cards)
        assertEquals(1L, BackupCodec.remote(raw.put("revision", 1).put("savedAt", 1735689600000L).toString()).revision)
        try { BackupCodec.remote(raw.put("revision", 0).toString()); fail() } catch (expected: Exception) { }
    }
    @Test fun databaseConnectionValidatesHostPortAndKeepsPasswordsOutOfTheUrl() {
        val config = BackupConnection.checked(" DB.Example.com ", "3306", "cardwheel", "cardwheel_user", " password with spaces ", false)
        assertEquals("jdbc:mariadb://db.example.com:3306/cardwheel", config.jdbcUrl)
        assertEquals(" password with spaces ", config.properties().getProperty("password"))
        assertEquals("disable", config.properties().getProperty("sslMode"))
        assertFalse(config.jdbcUrl.contains("password"))
        assertFalse(config.toString().contains(config.password))
        assertEquals("verify-full", config.copy(tls = true).properties().getProperty("sslMode"))
        assertEquals("cardwheel-strict", config.copy(tls = true).properties().getProperty("tlsSocketType"))
        for (host in listOf("http://example.com", "host?sslMode=disable", "user@host", "host,other-host", "host/path")) {
            try { BackupConnection.checked(host, "3306", "cardwheel", "user", "password", false); fail(host) } catch (expected: IllegalArgumentException) { }
        }
        for (port in listOf("0", "65536", "abc")) {
            try { BackupConnection.checked("example.com", port, "cardwheel", "user", "password", false); fail(port) } catch (expected: IllegalArgumentException) { }
        }
        assertEquals("jdbc:mariadb://[::1]:3306/cardwheel", BackupConnection.checked("[::1]", "3306", "cardwheel", "user", "password", false).jdbcUrl)
    }
    @Test fun restoreKeepsAnUndoSnapshotAndRejectsConcurrentLocalChanges() {
        val context = RuntimeEnvironment.getApplication() as Context
        val directory = File(context.cacheDir, "backup-test-${UUID.randomUUID()}").apply { mkdirs() }
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            db.cardDao().insert(card)
            val restorer = BackupRestorer(db, directory)
            val next = card.copy(id = 20, cardName = "서버에서 가져온 카드")
            restorer.replace(listOf(card), listOf(next))
            assertEquals(listOf(next), db.cardDao().getAll())
            assertEquals(listOf(card), restorer.previous())
            db.cardDao().update(next.copy(memo = "새 수정"))
            try { restorer.replace(listOf(next), listOf(card)); fail() } catch (expected: BackupProblem) { }
            assertEquals("새 수정", db.cardDao().getAll().single().memo)
            assertEquals(listOf(card), restorer.previous())
            restorer.replace(db.cardDao().getAll(), restorer.previous())
            assertEquals(listOf(card), db.cardDao().getAll())
        } finally { db.close(); directory.listFiles()?.forEach { it.delete() }; directory.delete() }
    }
    @Test fun replacingCardsRollsBackIfAnyInsertFails() {
        val context = RuntimeEnvironment.getApplication() as Context
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            db.cardDao().insert(card)
            try { db.cardDao().replaceAll(listOf(card.copy(id = 9), card.copy(id = 9))); fail() } catch (expected: Exception) { }
            assertEquals(listOf(card), db.cardDao().getAll())
        } finally { db.close() }
    }
    @Test fun jdbcBackupRoundTripAndStaleRevisionsPreserveTheServerSnapshot() {
        withDatabase { client, url ->
            assertEquals(RemoteBackup(0, null, emptyList()), client.fetch())
            val saved = client.upload(listOf(card), 0)
            assertEquals(1L, saved.revision)
            assertEquals(listOf(card), client.fetch().cards)
            assertNotNull(saved.savedAt)
            try { client.upload(emptyList(), 0); fail() } catch (expected: BackupProblem) { }
            assertEquals(saved, client.fetch())
            assertEquals(emptyList<CardItem>(), client.upload(emptyList(), 1).cards)
            assertEquals(2L, client.fetch().revision)
            java.sql.DriverManager.getConnection(url, "sa", "").use { connection ->
                connection.createStatement().use { it.executeUpdate("UPDATE cardwheel_backup SET payload = '{}' WHERE backup_id = 1") }
            }
            try { client.fetch(); fail() } catch (expected: BackupProblem) { }
        }
    }
    @Test fun concurrentFirstBackupsHaveOnlyOneWinner() {
        withDatabase { client, _ ->
            val pool = Executors.newFixedThreadPool(2)
            try {
                val results = pool.invokeAll(listOf(java.util.concurrent.Callable { try { client.upload(listOf(card), 0); true } catch (expected: BackupProblem) { false } },
                    java.util.concurrent.Callable { try { client.upload(listOf(card), 0); true } catch (expected: BackupProblem) { false } }))
                assertNotEquals(results[0].get(), results[1].get())
                assertEquals(1L, client.fetch().revision)
            } finally { pool.shutdownNow() }
        }
    }
    @Test fun failedSaveRollsBackAndSqlErrorsDoNotExposeCredentials() {
        withDatabase { client, url ->
            client.upload(listOf(card), 0)
            val failingClient = BackupClient(BackupConnection("localhost", 3306, "cardwheel", "user", "secret")) {
                val delegate = java.sql.DriverManager.getConnection(url, "sa", "")
                java.lang.reflect.Proxy.newProxyInstance(java.sql.Connection::class.java.classLoader, arrayOf(java.sql.Connection::class.java)) { _, method, args ->
                    if (method.name == "prepareStatement" && (args?.get(0) as? String)?.startsWith("SELECT") == true) throw java.sql.SQLException("Simulated read error", "08000")
                    try { method.invoke(delegate, *(args ?: emptyArray())) } catch (error: java.lang.reflect.InvocationTargetException) { throw error.cause!! }
                } as java.sql.Connection
            }
            try { failingClient.upload(emptyList(), 1); fail() } catch (expected: BackupProblem) { }
            assertEquals(1L, client.fetch().revision)
            assertEquals(listOf(card), client.fetch().cards)
        }
        val rejected = BackupClient(BackupConnection("localhost", 3306, "cardwheel", "user", "secret")) { throw java.sql.SQLException("secret", "28000", 1045) }
        try { rejected.fetch(); fail() } catch (expected: BackupProblem) { assertFalse(expected.message!!.contains("secret")); assertTrue(expected.message!!.contains("비밀번호")) }
    }
    @Test fun backupScreenDoesNotSaveTheTokenInViewStateAndShowsExplicitActions() {
        val controller = Robolectric.buildActivity(BackupActivity::class.java).setup()
        val activity = controller.get()
        val toggle = activity.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchDbTls)
        val off = intArrayOf(android.R.attr.state_enabled, -android.R.attr.state_checked)
        val on = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
        assertEquals(android.graphics.Color.WHITE, toggle.thumbTintList!!.getColorForState(off, 0))
        assertEquals(android.graphics.Color.WHITE, toggle.thumbTintList!!.getColorForState(on, 0))
        assertEquals(activity.getColor(R.color.switch_track_off), toggle.trackTintList!!.getColorForState(off, 0))
        assertEquals(activity.getColor(R.color.switch_border), toggle.trackDecorationTintList!!.getColorForState(off, 0))
        assertNotEquals(activity.getColor(R.color.surface), toggle.trackTintList!!.getColorForState(off, 0))
        assertFalse(activity.findViewById<View>(R.id.etDbPassword).isSaveEnabled)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.backupPreview).visibility)
        assertEquals("서버에 백업", activity.findViewById<TextView>(R.id.btnUpload).text.toString())
        assertEquals("서버 백업 불러오기", activity.findViewById<TextView>(R.id.btnRestore).text.toString())
        controller.pause().stop().destroy()
    }
    private fun withDatabase(test: (BackupClient, String) -> Unit) {
        val url = "jdbc:h2:mem:${UUID.randomUUID()};MODE=MariaDB;DB_CLOSE_DELAY=-1"
        Class.forName("org.h2.Driver")
        java.sql.DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { it.execute("CREATE TABLE cardwheel_backup (backup_id INT PRIMARY KEY, revision BIGINT NOT NULL, saved_at BIGINT, payload LONGTEXT NOT NULL)") }
        }
        try { test(BackupClient(BackupConnection("localhost", 3306, "cardwheel", "user", "password")) { java.sql.DriverManager.getConnection(url, "sa", "") }, url) }
        finally { java.sql.DriverManager.getConnection(url, "sa", "").use { it.createStatement().use { statement -> statement.execute("SHUTDOWN") } } }
    }
}
