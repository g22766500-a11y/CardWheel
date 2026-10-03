package com.example.cardwheel

import android.database.sqlite.SQLiteDatabase
import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.ContextThemeWrapper
import android.widget.TextView
import android.widget.EditText
import androidx.room.Room
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class CardWorkflowTest {
    @Test fun dateSelectionSurvivesRecreationAndCanBeCleared() {
        val controller = Robolectric.buildActivity(AddCardActivity::class.java).setup()
        val activity = controller.get()
        activity.findViewById<TextView>(R.id.tvIssueDate).performClick()
        val picker = ShadowDialog.getLatestDialog() as DatePickerDialog
        picker.datePicker.updateDate(2026, 9, 3)
        picker.getButton(DatePickerDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.findViewById<TextView>(R.id.tvIssueDate).text.contains("2026.10.03"))
        val state = Bundle()
        controller.saveInstanceState(state).pause().stop().destroy()
        val restored = Robolectric.buildActivity(AddCardActivity::class.java).setup(state)
        val dateView = restored.get().findViewById<TextView>(R.id.tvIssueDate)
        assertTrue(dateView.text.contains("2026.10.03"))
        dateView.performClick()
        (ShadowDialog.getLatestDialog() as DatePickerDialog).getButton(DatePickerDialog.BUTTON_NEUTRAL).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(dateView.text.contains("미설정"))
        restored.pause().stop().destroy()
    }

    @Test fun walletAndDetailLayoutsInflateInLightAndDarkThemes() {
        val application = RuntimeEnvironment.getApplication()
        for (mode in listOf("notnight", "night")) {
            RuntimeEnvironment.setQualifiers(mode)
            val themed: Context = ContextThemeWrapper(application, R.style.Theme_CardWheel)
            for (layout in listOf(R.layout.activity_main, R.layout.activity_card_detail, R.layout.item_card)) {
                assertNotNull(LayoutInflater.from(themed).inflate(layout, null))
            }
        }
    }

    @Test fun emptyRequiredFieldsAndInvalidAmountCannotBeSaved() {
        val controller = Robolectric.buildActivity(AddCardActivity::class.java).setup()
        val activity = controller.get()
        activity.findViewById<MaterialButton>(R.id.btnSave).performClick()
        assertNotNull(activity.findViewById<TextInputLayout>(R.id.tilCompany).error)
        assertNotNull(activity.findViewById<TextInputLayout>(R.id.tilCardName).error)
        activity.findViewById<EditText>(R.id.etCompany).setText("테스트")
        activity.findViewById<EditText>(R.id.etCardName).setText("카드")
        activity.findViewById<EditText>(R.id.etRequiredSpend).setText("2147483648")
        activity.findViewById<MaterialButton>(R.id.btnSave).performClick()
        assertNotNull(activity.findViewById<TextInputLayout>(R.id.tilRequiredSpend).error)
        assertFalse(activity.isFinishing)
        controller.pause().stop().destroy()
    }

    @Test fun versionOneMigrationPreservesCardAndSupportsUpdateAndDelete() {
        val context = RuntimeEnvironment.getApplication()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("CREATE TABLE cards (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, company TEXT NOT NULL, cardName TEXT NOT NULL, status TEXT NOT NULL)")
            old.execSQL("INSERT INTO cards (id, company, cardName, status) VALUES (7, '기존 카드사', '기존 카드', '기존 상태')")
            old.version = 1
        }
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2).allowMainThreadQueries().build()
        try {
            val dao = database.cardDao()
            val migrated = dao.getById(7)!!
            assertEquals("기존 카드", migrated.cardName)
            assertEquals("기존 상태", migrated.status)
            assertEquals(0, migrated.requiredSpend)
            assertNull(migrated.spendDeadline)
            val updated = migrated.copy(currentSpend = 150000, rewardReceived = true, cancelled = true)
            dao.update(updated)
            assertEquals(updated, dao.getById(7))
            dao.insert(CardItem(company = "새 카드사", cardName = "새 카드"))
            assertEquals(2, dao.getAll().size)
            dao.delete(updated)
            assertNull(dao.getById(7))
            assertEquals(1, dao.getAll().size)
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
