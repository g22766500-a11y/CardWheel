package com.example.cardwheel

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class BackupActivity : BaseActivity() {
    private lateinit var model: BackupViewModel
    private val saveBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && model.state.value?.busy != true) model.saveFile(uri)
    }
    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && model.state.value?.busy != true) model.previewFile(uri)
    }
    private val chooseCertificate = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && model.state.value?.busy != true) {
            try {
                val bytes = contentResolver.openInputStream(uri)!!.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) { val count = input.read(buffer); if (count < 0) break; require(output.size() + count <= 65536); output.write(buffer, 0, count) }
                    output.toByteArray()
                }
                val pem = String(bytes, Charsets.UTF_8)
                require(pem.contains("-----BEGIN CERTIFICATE-----"))
                val certificates = java.security.cert.CertificateFactory.getInstance("X.509").generateCertificates(bytes.inputStream())
                require(certificates.isNotEmpty())
                model.draftCertificate = pem
                renderCertificate()
            } catch (error: Exception) { findViewById<TextView>(R.id.tvBackupMessage).text = "PEM 인증서 파일을 읽지 못했어요. 64KiB 이하의 인증서를 선택해 주세요." }
        }
    }
    private fun renderCertificate() {
        findViewById<TextView>(R.id.tvDbCertificate).text = if (model.draftCertificate.isEmpty()) getString(R.string.backup_cert_none) else "별도 서버 인증서가 선택되었습니다."
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setup(R.layout.activity_backup)
        model = ViewModelProvider(this)[BackupViewModel::class.java]
        findViewById<View>(R.id.btnSaveBackupFile).setOnClickListener {
            model.cancelPreview()
            saveBackup.launch("CardWheel-backup-${java.time.LocalDate.now()}.json")
        }
        findViewById<View>(R.id.btnOpenBackupFile).setOnClickListener {
            model.cancelPreview()
            openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
        val host = findViewById<TextInputEditText>(R.id.etDbHost)
        val port = findViewById<TextInputEditText>(R.id.etDbPort)
        val database = findViewById<TextInputEditText>(R.id.etDbName)
        val user = findViewById<TextInputEditText>(R.id.etDbUser)
        val password = findViewById<TextInputEditText>(R.id.etDbPassword)
        val tls = findViewById<MaterialSwitch>(R.id.switchDbTls)
        if (savedInstanceState == null) model.savedConnection?.let {
            host.setText(it.host); port.setText(it.port.toString()); database.setText(it.database); user.setText(it.user); tls.isChecked = it.tls
        }
        findViewById<View>(R.id.tlsCertificateArea).visibility = if (tls.isChecked) View.VISIBLE else View.GONE
        tls.setOnCheckedChangeListener { _, checked -> findViewById<View>(R.id.tlsCertificateArea).visibility = if (checked) View.VISIBLE else View.GONE }
        renderCertificate()
        findViewById<View>(R.id.btnChooseDbCertificate).setOnClickListener { chooseCertificate.launch("*/*") }
        findViewById<View>(R.id.btnClearDbCertificate).setOnClickListener { model.draftCertificate = ""; renderCertificate() }
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { if (model.state.value?.busy != true) finish() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (model.state.value?.busy != true) finish() }
        })
        findViewById<View>(R.id.btnConnection).setOnClickListener { model.configure(host.text.toString(), port.text.toString(), database.text.toString(), user.text.toString(), password.text.toString(), tls.isChecked) }
        findViewById<View>(R.id.btnDisconnect).setOnClickListener { password.text?.clear(); model.disconnect() }
        findViewById<View>(R.id.btnUpload).setOnClickListener { model.preview(BackupAction.UPLOAD) }
        findViewById<View>(R.id.btnRestore).setOnClickListener { model.preview(BackupAction.RESTORE) }
        findViewById<View>(R.id.btnUndoRestore).setOnClickListener { model.preview(BackupAction.UNDO) }
        findViewById<View>(R.id.btnApplyBackup).setOnClickListener { model.confirm() }
        findViewById<View>(R.id.btnCancelBackup).setOnClickListener { model.cancelPreview() }
        model.state.observe(this) { state ->
            val controls = listOf(R.id.btnSaveBackupFile, R.id.btnOpenBackupFile, R.id.etDbHost, R.id.etDbPort, R.id.etDbName, R.id.etDbUser, R.id.etDbPassword, R.id.tilDbPassword, R.id.switchDbTls, R.id.btnChooseDbCertificate, R.id.btnClearDbCertificate, R.id.btnConnection, R.id.btnDisconnect, R.id.btnUpload, R.id.btnRestore, R.id.btnUndoRestore, R.id.btnApplyBackup, R.id.btnCancelBackup)
            controls.forEach { findViewById<View>(it).isEnabled = !state.busy }
            findViewById<View>(R.id.backupBusy).visibility = if (state.busy) View.VISIBLE else View.GONE
            findViewById<TextView>(R.id.tvBackupMessage).text = state.message
            findViewById<TextView>(R.id.tvSavedConnection).text = model.savedConnection?.let { "저장된 DB: ${it.address} · ${it.user}\n${if (it.tls) "TLS 인증서 검증 사용" else "일반 연결 · 통신 암호화 없음"}\n비밀번호를 비워두면 같은 계정의 저장된 비밀번호를 사용합니다." } ?: "저장된 접속 정보 없음"
            if (state.message.startsWith("접속 정보를 저장했어요.")) password.text?.clear()
            findViewById<View>(R.id.backupPreview).visibility = if (state.preview == null) View.GONE else View.VISIBLE
            renderCertificate()
            state.preview?.let { preview ->
                val upload = preview.action == BackupAction.UPLOAD
                val cards = if (upload) preview.local else preview.remote.cards
                val target = if (upload) "서버 백업" else "이 기기의 카드 목록"
                val replacedCount = if (upload) preview.remote.cards.size else preview.local.size
                val date = if (preview.remote.savedAt == null) "" else "\n백업일: ${CardDisplay.date(preview.remote.savedAt)}"
                val names = cards.take(12).joinToString("\n") { "• ${it.company} · ${it.cardName}" }
                findViewById<TextView>(R.id.tvBackupPreview).text = "$target ${replacedCount}개를 아래 ${cards.size}개로 교체합니다.$date\n\n${if (cards.isEmpty()) "카드가 없는 빈 목록입니다." else names}${if (cards.size > 12) "\n외 ${cards.size - 12}개" else ""}\n\n${if (upload) "기존 서버 백업은 덮어씁니다." else "현재 기기의 카드 목록은 복원 직전 목록으로 따로 보관합니다."}"
                findViewById<MaterialButton>(R.id.btnApplyBackup).text = if (upload) "이 목록으로 서버 백업" else "이 목록으로 기기 복원"
            }
        }
    }
}
