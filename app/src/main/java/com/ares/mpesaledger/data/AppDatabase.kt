package com.ares.mpesaledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ares.mpesaledger.domain.DefaultRules

@Database(
    entities = [
        TransactionEntity::class,
        UnparsedEntity::class,
        CategoryRuleEntity::class,
        BudgetEntity::class,
        ImportStateEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun unparsed(): UnparsedDao
    abstract fun rules(): RuleDao
    abstract fun importState(): ImportStateDao
    abstract fun stats(): StatsDao
    abstract fun budgets(): BudgetDao

    companion object {
        const val REAL_NAME = "mpesa.db"
        const val DEMO_NAME = "mpesa_demo.db"

        /**
         * Every schema change bumps `version` and adds a hand-written Migration here, checked
         * against the exported JSON in app/schemas/. There is deliberately no destructive
         * fallback: a missing migration must crash in testing, not wipe someone's ledger.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun build(context: Context, name: String): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(*MIGRATIONS)
                .addCallback(SeedDefaultRules)
                .build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .addCallback(SeedDefaultRules)
                .build()
    }
}

/** v2: import_state.parserVersion, so a parser upgrade can trigger a full inbox rescan. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE import_state ADD COLUMN parserVersion INTEGER NOT NULL DEFAULT 0")
    }
}

/** Seeds the built-in keyword rules once, when the database file is first created. */
private object SeedDefaultRules : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        DefaultRules.all.forEach { rule ->
            db.execSQL(
                "INSERT INTO category_rules (keyword, category, isUser) VALUES (?, ?, 0)",
                arrayOf<Any>(rule.keyword.uppercase(), rule.category),
            )
        }
    }
}
