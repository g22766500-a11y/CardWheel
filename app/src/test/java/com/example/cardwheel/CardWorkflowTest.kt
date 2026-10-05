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
import android.view.View
import android.view.MotionEvent
import android.graphics.Rect
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
    @Test fun issuerDropdownPreservesCustomNamesAndRecycledCardsResetTheirColors() {
        val controller = Robolectric.buildActivity(AddCardActivity::class.java).setup()
        val activity = controller.get()
        val company = activity.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.etCompany)
        assertEquals(12, company.adapter.count)
        company.setText("직접 입력한 카드사", false)
        assertEquals("직접 입력한 카드사", company.text.toString())
        val adapter = CardAdapter { }
        adapter.submitItems(listOf(CardItem(id = 1, company = "KB 국민카드", cardName = "노란 카드"), CardItem(id = 2, company = "삼성카드", cardName = "파란 카드")))
        val holder = adapter.onCreateViewHolder(android.widget.FrameLayout(activity), 0)
        adapter.onBindViewHolder(holder, 0)
        assertEquals(android.graphics.Color.parseColor("#322A16"), holder.itemView.findViewById<android.widget.TextView>(R.id.tvCardName).currentTextColor)
        adapter.onBindViewHolder(holder, 1)
        assertEquals(android.graphics.Color.WHITE, holder.itemView.findViewById<android.widget.TextView>(R.id.tvCardName).currentTextColor)
        val background = holder.itemView.findViewById<View>(R.id.cardContent).background as android.graphics.drawable.GradientDrawable
        assertArrayEquals(intArrayOf(android.graphics.Color.parseColor("#17539A"), android.graphics.Color.parseColor("#142F59")), background.colors)
        adapter.submitItems(listOf(CardItem(id = 3, company = "KB국민카드", cardName = "해지한 카드", cancelled = true)))
        adapter.onBindViewHolder(holder, 0)
        assertEquals(0.88f, holder.itemView.findViewById<View>(R.id.cardContent).alpha, 0.001f)
        assertEquals("해지·탈회 완료", holder.itemView.findViewById<TextView>(R.id.tvBrand).text.toString())
        assertNotNull(holder.itemView.findViewById<TextView>(R.id.tvBrand).background)
        assertEquals(android.graphics.Color.WHITE, holder.itemView.findViewById<TextView>(R.id.tvCardName).currentTextColor)
        adapter.submitItems(listOf(CardItem(id = 3, company = "KB국민카드", cardName = "해지 취소한 카드", cancelled = false)))
        adapter.onBindViewHolder(holder, 0)
        assertEquals(1f, holder.itemView.findViewById<View>(R.id.cardContent).alpha, 0.001f)
        assertEquals(1f, holder.itemView.findViewById<View>(R.id.cardChip).alpha, 0.001f)
        assertNull(holder.itemView.findViewById<TextView>(R.id.tvBrand).background)
        assertEquals(0, holder.itemView.findViewById<TextView>(R.id.tvBrand).paddingLeft)
        assertEquals(android.graphics.Color.parseColor("#322A16"), holder.itemView.findViewById<TextView>(R.id.tvCardName).currentTextColor)
        controller.pause().stop().destroy()
    }

    @Test fun issuerAliasesUseTheSamePaletteAndUnknownCompaniesHaveAFallback() {
        assertEquals(IssuerCatalog.palette("KB국민카드"), IssuerCatalog.palette("국민카드"))
        assertEquals(IssuerCatalog.palette("BC카드"), IssuerCatalog.palette("bc"))
        assertEquals(IssuerCatalog.palette("NH농협카드"), IssuerCatalog.palette("농협"))
        assertEquals(IssuerCatalog.palette(""), IssuerCatalog.palette("나의 카드사"))
        assertFalse(IssuerCatalog.palette("신한카드").light)
        assertTrue(IssuerCatalog.palette("KB국민카드").light)
    }

    @Test fun actualPagerKeepsTheFirstCardSizeAfterForwardAndBackwardPageChanges() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start()
        val activity = controller.get()
        val root = activity.findViewById<android.view.ViewGroup>(R.id.root)
        root.findViewById<View>(R.id.emptyState).visibility = View.GONE
        root.findViewById<View>(R.id.walletContent).visibility = View.VISIBLE
        val pager = root.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
        (pager.adapter as CardAdapter).submitItems(listOf(
            CardItem(id = 1, company = "카드사", cardName = "짧은 이름"),
            CardItem(id = 2, company = "다른 카드사", cardName = "두 줄로 표시되는 아주 긴 카드 이름입니다")
        ))
        val density = activity.resources.displayMetrics.density
        fun dimensions(): Pair<Int, Int> {
            root.measure(View.MeasureSpec.makeMeasureSpec((360 * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((800 * density).toInt(), View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            val recycler = pager.getChildAt(0) as androidx.recyclerview.widget.RecyclerView
            val holder = recycler.findViewHolderForAdapterPosition(pager.currentItem)!!
            val card = (holder.itemView as android.view.ViewGroup).getChildAt(0)
            return card.width to card.height
        }
        val first = dimensions()
        assertTrue(first.first > 0 && first.second > 0)
        assertEquals(1.586, first.first.toDouble() / first.second, 0.02)
        val chip = ((pager.getChildAt(0) as androidx.recyclerview.widget.RecyclerView).findViewHolderForAdapterPosition(0)!!.itemView).findViewById<View>(R.id.cardChip)
        assertEquals(View.VISIBLE, chip.visibility)
        assertNotNull((chip as android.widget.ImageView).drawable)
        val viewport = pager.height
        pager.setCurrentItem(1, false)
        assertEquals(first, dimensions())
        assertEquals(viewport, pager.height)
        pager.setCurrentItem(0, false)
        assertEquals(first, dimensions())
        assertEquals(viewport, pager.height)
        controller.stop().destroy()
    }

    @Test fun cardBottomSpacingDoesNotChangeWhenPagerHeightChanges() {
        val themed = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_CardWheel)
        val page = LayoutInflater.from(themed).inflate(R.layout.item_card, null) as android.view.ViewGroup
        val density = themed.resources.displayMetrics.density
        for (height in listOf(300, 350)) {
            page.measure(View.MeasureSpec.makeMeasureSpec((320 * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY))
            page.layout(0, 0, page.measuredWidth, page.measuredHeight)
            val card = page.getChildAt(0)
            assertEquals(page.paddingBottom, page.height - card.bottom)
            assertEquals(page.paddingTop, card.top)
        }
    }

    @Test fun diagonalCardSwipesStayHorizontalButVerticalGesturesScrollThePage() {
        val controller = Robolectric.buildActivity(AddCardActivity::class.java).setup()
        val activity = controller.get()
        activity.setContentView(R.layout.activity_main)
        val root = activity.findViewById<android.view.ViewGroup>(R.id.root)
        root.findViewById<View>(R.id.emptyState).visibility = View.GONE
        root.findViewById<View>(R.id.walletContent).visibility = View.VISIBLE
        val density = activity.resources.displayMetrics.density
        root.measure(View.MeasureSpec.makeMeasureSpec((320 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((480 * density).toInt(), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val scroll = root.findViewById<WalletScrollView>(R.id.mainScroll)
        val pager = root.findViewById<View>(R.id.viewPager)
        val location = IntArray(2)
        pager.getLocationOnScreen(location)
        val x = location[0] + pager.width / 2f
        val y = location[1] + 40 * density
        fun touch(action: Int, dx: Float = 0f, dy: Float = 0f): Boolean {
            val event = MotionEvent.obtain(0, 20, action, x + dx * density, y + dy * density, 0)
            return try { scroll.onInterceptTouchEvent(event) } finally { event.recycle() }
        }
        assertFalse(touch(MotionEvent.ACTION_DOWN))
        assertFalse(touch(MotionEvent.ACTION_MOVE, -100f, -35f))
        assertFalse(touch(MotionEvent.ACTION_MOVE, -160f, -70f))
        touch(MotionEvent.ACTION_CANCEL)
        assertFalse(touch(MotionEvent.ACTION_DOWN))
        assertTrue(touch(MotionEvent.ACTION_MOVE, -5f, -100f))
        touch(MotionEvent.ACTION_CANCEL)
        assertFalse(touch(MotionEvent.ACTION_DOWN))
        assertFalse(touch(MotionEvent.ACTION_MOVE, 1f, 1f))
        touch(MotionEvent.ACTION_UP)
        controller.pause().stop().destroy()
    }

    @Test fun fabHasReservedSpaceOutsideScrollableContentOnSmallScreens() {
        val themed = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_CardWheel)
        val root = LayoutInflater.from(themed).inflate(R.layout.activity_main, null) as android.view.ViewGroup
        root.findViewById<View>(R.id.emptyState).visibility = View.GONE
        root.findViewById<View>(R.id.walletContent).visibility = View.VISIBLE
        val density = themed.resources.displayMetrics.density
        for (screenHeight in listOf(480, 800)) {
            root.measure(View.MeasureSpec.makeMeasureSpec((320 * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((screenHeight * density).toInt(), View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            val scroll = root.findViewById<androidx.core.widget.NestedScrollView>(R.id.mainScroll)
            val scrollBounds = Rect(0, 0, scroll.width, scroll.height)
            root.offsetDescendantRectToMyCoords(scroll, scrollBounds)
            val fab = root.findViewById<View>(R.id.fabAdd)
            val fabBounds = Rect(0, 0, fab.width, fab.height)
            root.offsetDescendantRectToMyCoords(fab, fabBounds)
            assertTrue(scrollBounds.bottom <= fabBounds.top)
        }
    }

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
                val view = LayoutInflater.from(themed).inflate(layout, null)
                assertNotNull(view)
                if (layout == R.layout.activity_card_detail) {
                    val toggle = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchReward)
                    val off = intArrayOf(android.R.attr.state_enabled, -android.R.attr.state_checked)
                    val disabled = intArrayOf(-android.R.attr.state_enabled, -android.R.attr.state_checked)
                    assertEquals(android.graphics.Color.WHITE, toggle.thumbTintList!!.getColorForState(off, 0))
                    assertEquals(android.graphics.Color.TRANSPARENT, toggle.trackDecorationTintList!!.getColorForState(off, 0))
                    assertEquals(themed.getColor(R.color.switch_track_off), toggle.trackTintList!!.getColorForState(off, 0))
                    assertEquals(android.graphics.Color.TRANSPARENT, toggle.trackDecorationTintList!!.getColorForState(disabled, 0))
                }
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
