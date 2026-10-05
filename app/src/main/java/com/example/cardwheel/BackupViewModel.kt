package com.example.cardwheel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors

internal enum class BackupAction { UPLOAD, RESTORE, UNDO, FILE_RESTORE }
internal data class BackupPreview(val action: BackupAction, val local: List<CardItem>, val remote: RemoteBackup)
internal data class BackupState(val busy: Boolean = false, val message: String = "백업 파일을 선택하면 카드 목록을 먼저 확인할 수 있어요.", val preview: BackupPreview? = null)

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val store = BackupConnectionStore(application)
    private var connection = runCatching { store.load() }.getOrNull()
    internal val configured get() = connection != null
    internal val savedConnection get() = connection
    internal var draftCertificate = connection?.certificate.orEmpty()
    internal val state = MutableLiveData(BackupState())
    private val database by lazy { AppDatabase.getDatabase(getApplication()) }
    private val restorer by lazy { BackupRestorer(database, getApplication<Application>().noBackupFilesDir) }
    internal fun saveFile(uri: android.net.Uri) {
        work("백업 파일을 저장하고 있어요…") {
            val cards = database.cardDao().getAll()
            val bytes = BackupFile.encode(cards)
            val resolver = getApplication<Application>().contentResolver
            val output = resolver.openOutputStream(uri, "wt") ?: throw java.io.IOException()
            output.use { it.write(bytes); it.flush() }
            BackupState(message = "카드 ${cards.size}개를 백업 파일로 저장했어요.")
        }
    }
    internal fun previewFile(uri: android.net.Uri) {
        work("백업 파일을 확인하고 있어요…") {
            val resolver = getApplication<Application>().contentResolver
            val input = resolver.openInputStream(uri) ?: throw java.io.IOException()
            val remote = input.use { BackupFile.read(it) }
            val local = database.cardDao().getAll()
            BackupCodec.encode(local)
            BackupState(message = "파일의 카드 목록을 확인한 뒤 복원해 주세요.",
                preview = BackupPreview(BackupAction.FILE_RESTORE, local, remote))
        }
    }
    internal fun configure(host: String, port: String, database: String, user: String, password: String, tls: Boolean) {
        if (state.value?.busy == true) return
        val config = try {
            val normalized = BackupConnection.normalizedHost(host)
            val actualPassword = password.ifEmpty {
                val number = port.trim().toIntOrNull()
                if (number != null && connection?.sameAccount(normalized, number, database.trim(), user.trim()) == true) connection!!.password
                else throw IllegalArgumentException("새 접속 정보에는 DB 비밀번호를 다시 입력해 주세요.")
            }
            BackupConnection.checked(host, port, database, user, actualPassword, tls, draftCertificate)
        } catch (error: IllegalArgumentException) { state.value = BackupState(message = error.message.orEmpty()); return }
        work("서버 연결을 확인하고 있어요…") {
            val remote = BackupClient(config).fetch()
            store.save(config); connection = config
            BackupState(message = "접속 정보를 저장했어요. " + description(remote))
        }
    }
    internal fun disconnect() {
        if (state.value?.busy == true) return
        work("접속 정보를 지우고 있어요…") {
            store.clear(); connection = null; draftCertificate = ""
            BackupState(message = "이 기기의 접속 정보를 지웠어요. 서버 백업은 유지됩니다.")
        }
    }
    internal fun preview(action: BackupAction) {
        if (state.value?.busy == true) return
        val config = connection
        if (action != BackupAction.UNDO && config == null) { state.value = BackupState(message = "DB 접속 정보를 먼저 저장해 주세요."); return }
        work("카드 목록을 확인하고 있어요…") {
            val local = database.cardDao().getAll()
            BackupCodec.encode(local)
            val remote = if (action == BackupAction.UNDO) RemoteBackup(1, null, restorer.previous()) else BackupClient(config!!).fetch()
            if (action == BackupAction.RESTORE && remote.revision == 0L) throw BackupProblem("서버에 저장된 백업이 없어요. 먼저 백업해 주세요.")
            BackupState(message = "아래 목록을 확인한 뒤 적용해 주세요.", preview = BackupPreview(action, local, remote))
        }
    }
    internal fun cancelPreview() { if (state.value?.busy != true) state.value = BackupState(message = "적용하지 않았어요. 카드 데이터는 유지됩니다.") }
    internal fun confirm() {
        if (state.value?.busy == true) return
        val preview = state.value?.preview ?: return
        val config = connection
        work("${if (preview.action == BackupAction.UPLOAD) "서버에 저장" else "기기에 복원"}하고 있어요…") {
            if (preview.action == BackupAction.UPLOAD) {
                val current = database.cardDao().getAll()
                if (current != preview.local) throw BackupProblem("기기의 카드가 변경되었습니다. 목록을 다시 확인해 주세요.")
                val uploaded = BackupClient(config ?: throw BackupProblem("접속 정보를 다시 저장해 주세요.")).upload(current, preview.remote.revision)
                if (uploaded.cards != current || uploaded.revision <= preview.remote.revision) throw BackupProblem("서버 저장 결과를 확인하지 못했습니다. 서버 백업을 다시 조회해 주세요.")
                BackupState(message = "카드 ${current.size}개를 서버에 백업했어요. ${CardDisplay.date(uploaded.savedAt)}")
            } else {
                restorer.replace(preview.local, preview.remote.cards)
                BackupState(message = "카드 ${preview.remote.cards.size}개를 ${if (preview.action == BackupAction.UNDO) "되돌렸어요" else "불러왔어요"}. 복원 직전 목록은 ‘이전 카드로 되돌리기’에서 확인할 수 있어요.")
            }
        }
    }
    private fun work(message: String, action: () -> BackupState) {
        if (state.value?.busy == true) return
        state.value = BackupState(busy = true, message = message)
        worker.execute {
            val result = try { action() }
            catch (error: BackupProblem) { BackupState(message = error.message.orEmpty()) }
            catch (error: java.io.FileNotFoundException) { BackupState(message = "파일을 찾지 못했어요. 파일이나 되돌릴 목록이 있는지 확인해 주세요.") }
            catch (error: java.net.SocketTimeoutException) { BackupState(message = "응답 시간이 초과되었습니다. 백업 중이었다면 서버 목록을 다시 확인해 주세요.") }
            catch (error: java.io.IOException) { BackupState(message = "파일을 읽거나 저장하지 못했어요. 파일 형식, 저장 공간과 접근 권한을 확인해 주세요.") }
            catch (error: IllegalArgumentException) { BackupState(message = "백업 형식이나 카드 수를 확인해 주세요. 최대 500개·2MiB이며 기기 데이터는 유지됩니다.") }
            catch (error: org.json.JSONException) { BackupState(message = "백업 데이터를 읽지 못했습니다. 기기 데이터는 유지됩니다.") }
            catch (error: Exception) { BackupState(message = "작업을 완료하지 못했습니다. 접속 정보와 네트워크를 확인해 주세요. 기기 데이터는 유지됩니다.") }
            state.postValue(result)
        }
    }
    private fun description(remote: RemoteBackup) = if (remote.revision == 0L) "서버 백업은 아직 없어요." else "서버에 카드 ${remote.cards.size}개 · ${CardDisplay.date(remote.savedAt)} 백업이 있어요."
    companion object { private val worker = Executors.newSingleThreadExecutor() }
}
