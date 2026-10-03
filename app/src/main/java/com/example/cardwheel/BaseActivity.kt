package com.example.cardwheel

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.Executors

abstract class BaseActivity : AppCompatActivity() {
    protected val dao by lazy { AppDatabase.getDatabase(this).cardDao() }
    protected fun setup(layout: Int) {
        enableEdgeToEdge()
        setContentView(layout)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }
    protected fun <T> databaseWork(action: () -> T, failed: () -> Unit = {}, done: (T) -> Unit) {
        worker.execute {
            try {
                val value = action()
                runOnUiThread { if (!isFinishing && !isDestroyed) done(value) }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        failed()
                        MaterialAlertDialogBuilder(this).setTitle("저장소 오류")
                            .setMessage("데이터를 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.")
                            .setPositiveButton("확인", null).show()
                    }
                }
            }
        }
    }
    companion object { private val worker = Executors.newSingleThreadExecutor() }
}
