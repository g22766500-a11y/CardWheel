package com.example.cardwheel

import java.net.IDN
import java.net.URI
import java.sql.Connection
import java.sql.SQLException
import java.util.Locale
import java.util.Properties

internal data class BackupConnection(val host: String, val port: Int, val database: String, val user: String, val password: String, val tls: Boolean = false, val certificate: String = "") {
    override fun toString() = "BackupConnection($address, user=$user, tls=$tls)"
    val address get() = "${if (host.contains(':')) "[$host]" else host}:$port/$database"
    val jdbcUrl get() = "jdbc:mariadb://$address"
    fun sameAccount(host: String, port: Int, database: String, user: String) = this.host == host && this.port == port && this.database == database && this.user == user
    fun properties() = Properties().apply {
        setProperty("user", user); setProperty("password", password)
        setProperty("connectTimeout", "10000"); setProperty("socketTimeout", "15000")
        setProperty("sslMode", if (tls) "verify-full" else "disable")
        setProperty("restrictedAuth", "mysql_native_password,client_ed25519,caching_sha2_password")
        setProperty("allowLocalInfile", "false")
        if (tls) {
            setProperty("tlsSocketType", "cardwheel-strict")
            if (certificate.isNotEmpty()) setProperty("serverSslCert", certificate)
        }
    }
    companion object {
        fun normalizedHost(raw: String): String {
            val host = raw.trim().removeSurrounding("[", "]")
            require(host.isNotEmpty() && host.length <= 253 && !host.any { it.isWhitespace() || it in "/\\@?#%&,;=" }) { "도메인 또는 IP 주소만 입력해 주세요. http://는 넣지 않습니다." }
            return try {
                if (host.contains(':')) {
                    require(host.matches(Regex("[0-9a-fA-F:]+")))
                    URI("mariadb", null, host, 3306, "/", null, null)
                    host.lowercase(Locale.ROOT)
                } else IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
            } catch (error: Exception) { throw IllegalArgumentException("DB 서버 주소가 올바르지 않습니다.") }
        }
        fun checked(host: String, port: String, database: String, user: String, password: String, tls: Boolean, certificate: String = ""): BackupConnection {
            val normalized = normalizedHost(host)
            val number = port.trim().toIntOrNull()
            require(number != null && number in 1..65535) { "포트는 1~65535 사이로 입력해 주세요." }
            val name = database.trim()
            require(name.matches(Regex("[A-Za-z0-9_]{1,64}"))) { "DB 이름은 영문·숫자·밑줄로 입력해 주세요." }
            val account = user.trim()
            require(account.isNotEmpty() && account.length <= 128 && account.none { it.isISOControl() }) { "DB 계정을 입력해 주세요." }
            require(password.isNotEmpty() && password.length <= 512 && '\u0000' !in password) { "DB 비밀번호를 입력해 주세요." }
            require(certificate.toByteArray(Charsets.UTF_8).size <= 65536) { "인증서 파일은 64KiB 이하로 선택해 주세요." }
            if (certificate.isNotBlank()) {
                try { java.security.cert.CertificateFactory.getInstance("X.509").generateCertificates(certificate.byteInputStream()).also { require(it.isNotEmpty()) } }
                catch (error: Exception) { throw IllegalArgumentException("인증서 형식이 올바르지 않습니다. PEM 인증서를 선택해 주세요.") }
            }
            return BackupConnection(normalized, number, name, account, password, tls, certificate)
        }
    }
}
internal class BackupProblem(message: String) : Exception(message)
internal class BackupClient(private val configuration: BackupConnection, private val openConnection: () -> Connection = {
    org.mariadb.jdbc.Driver().connect(configuration.jdbcUrl, configuration.properties()) ?: throw SQLException("Connection unavailable")
}) {
    fun fetch(): RemoteBackup = safely { openConnection().use { read(it) } }
    fun upload(cards: List<CardItem>, expected: Long): RemoteBackup {
        val payload = BackupCodec.encode(cards)
        require(expected in 0 until Long.MAX_VALUE)
        return safely {
            openConnection().use { connection ->
                connection.autoCommit = false
                try {
                    val updated = connection.prepareStatement("UPDATE cardwheel_backup SET revision = revision + 1, saved_at = ?, payload = ? WHERE backup_id = 1 AND revision = ?").use { statement ->
                        statement.setLong(1, System.currentTimeMillis()); statement.setString(2, payload); statement.setLong(3, expected)
                        statement.executeUpdate()
                    }
                    if (updated == 0) {
                        if (expected != 0L) throw BackupProblem("다른 기기에서 서버 백업을 변경했습니다. 목록을 다시 확인해 주세요.")
                        try {
                            connection.prepareStatement("INSERT INTO cardwheel_backup (backup_id, revision, saved_at, payload) VALUES (1, 1, ?, ?)").use { statement ->
                                statement.setLong(1, System.currentTimeMillis()); statement.setString(2, payload); statement.executeUpdate()
                            }
                        } catch (error: SQLException) {
                            if (error.sqlState?.startsWith("23") == true) throw BackupProblem("다른 기기에서 서버 백업을 변경했습니다. 목록을 다시 확인해 주세요.")
                            throw error
                        }
                    }
                    val saved = read(connection)
                    if (saved.cards != cards.sortedByDescending { it.id } || saved.revision != expected + 1) throw BackupProblem("서버 저장 결과를 확인하지 못했습니다. 서버 백업을 다시 조회해 주세요.")
                    connection.commit()
                    saved
                } catch (error: Exception) {
                    runCatching { connection.rollback() }
                    throw error
                }
            }
        }
    }
    private fun read(connection: Connection): RemoteBackup {
        return connection.prepareStatement("SELECT revision, saved_at, payload, OCTET_LENGTH(payload) FROM cardwheel_backup WHERE backup_id = 1").use { statement ->
            statement.executeQuery().use { result ->
                if (!result.next()) return@use RemoteBackup(0, null, emptyList())
                val revision = result.getLong(1)
                val stamp = result.getLong(2); val savedAt = if (result.wasNull()) null else stamp
                if (result.getLong(4) > BackupCodec.MAX_BYTES) throw BackupProblem("서버 백업 데이터가 너무 큽니다.")
                val root = org.json.JSONObject(result.getString(3)).put("revision", revision).put("savedAt", savedAt ?: org.json.JSONObject.NULL)
                try { BackupCodec.remote(root.toString()) }
                catch (error: Exception) { throw BackupProblem("서버 백업 형식이 올바르지 않습니다. 기기 데이터는 유지됩니다.") }
            }
        }
    }
    private fun <T> safely(action: () -> T): T {
        try { return action() }
        catch (error: LinkageError) { throw BackupProblem("이 Android 환경에서 DB 드라이버를 실행하지 못했습니다. 기기 데이터는 유지됩니다.") }
        catch (error: SQLException) {
            val causes = generateSequence<Throwable>(error) { it.cause }.take(12).toList()
            if (causes.any { it is javax.net.ssl.SSLException || it is java.security.cert.CertificateException }) throw BackupProblem("TLS 인증서를 확인하지 못했습니다. DB 서버 인증서와 접속 주소를 확인해 주세요.")
            if (causes.any { it is java.net.SocketTimeoutException }) throw BackupProblem("서버 응답 시간이 초과되었습니다. 백업 중이었다면 서버 목록을 다시 확인해 주세요.")
            when {
                error.sqlState?.startsWith("28") == true -> throw BackupProblem("DB 계정 또는 비밀번호가 맞지 않습니다. 접속 권한도 확인해 주세요.")
                error.errorCode == 1146 -> throw BackupProblem("백업 테이블이 없습니다. 안내문의 setup.sql을 먼저 실행해 주세요.")
                error.errorCode in listOf(1044, 1142, 1143) -> throw BackupProblem("DB 계정에 백업 테이블 접근 권한이 없습니다.")
                error.errorCode == 1049 -> throw BackupProblem("DB 이름을 확인해 주세요.")
                configuration.tls && error.message?.contains("SSL", ignoreCase = true) == true -> throw BackupProblem("DB 서버의 TLS 설정을 확인해 주세요. 일반 연결은 TLS 옵션을 끄고 사용합니다.")
                else -> throw BackupProblem("DB에 연결하지 못했습니다. 서버 주소·포트·네트워크를 확인해 주세요. 기기 데이터는 유지됩니다.")
            }
        }
    }
}
