package com.example.cardwheel

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CardItem::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cardDao(): CardDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        internal val MIGRATION_1_2 = object : Migration(1, 2) {

            override fun migrate(db: SupportSQLiteDatabase) {

                db.execSQL(
                    "ALTER TABLE cards ADD COLUMN requiredSpend INTEGER NOT NULL DEFAULT 0"
                )

                db.execSQL(
                    "ALTER TABLE cards ADD COLUMN currentSpend INTEGER NOT NULL DEFAULT 0"
                )

                db.execSQL(
                    "ALTER TABLE cards ADD COLUMN rewardAmount INTEGER NOT NULL DEFAULT 0"
                )

                db.execSQL(
                    "ALTER TABLE cards ADD COLUMN issueDate INTEGER DEFAULT NULL"
                )

                db.execSQL(
                    "ALTER TABLE cards ADD COLUMN spendDeadline INTEGER DEFAULT NULL"
                )

                db.execSQL(
                    "ALTER TABLE cards ADD COLUMN rewardDate INTEGER DEFAULT NULL"
                )

                db.execSQL(
                    "ALTER TABLE cards ADD COLUMN cancelDate INTEGER DEFAULT NULL"
                )

                db.execSQL(
                    "ALTER TABLE cards ADD COLUMN nextEligibleDate INTEGER DEFAULT NULL"
                )

                db.execSQL(
                    "ALTER TABLE cards ADD COLUMN rewardReceived INTEGER NOT NULL DEFAULT 0"
                )

                db.execSQL(
                    "ALTER TABLE cards ADD COLUMN cancelled INTEGER NOT NULL DEFAULT 0"
                )

                db.execSQL(
                    "ALTER TABLE cards ADD COLUMN memo TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cardwheel_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    
                    .build()

                INSTANCE = instance

                instance
            }
        }
    }
}
