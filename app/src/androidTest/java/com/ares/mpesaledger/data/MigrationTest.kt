package com.ares.mpesaledger.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every migration is replayed against the exported schema JSON in app/schemas/. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun migrate1To2KeepsDataAndAddsParserVersion() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL("INSERT INTO import_state (id, lastInboxDate) VALUES (0, 12345)")
            db.execSQL(
                "INSERT INTO transactions (code, kind, direction, amountCents, counterparty, category, raw) " +
                    "VALUES ('TJA1B2C3D4', 'received', 'in', 300000, 'JOHN', 'Income', 'raw')"
            )
        }
        helper.runMigrationsAndValidate(dbName, 2, true, *AppDatabase.MIGRATIONS).use { db ->
            db.query("SELECT lastInboxDate, parserVersion FROM import_state WHERE id = 0").use { c ->
                c.moveToFirst()
                assertEquals(12345L, c.getLong(0))
                assertEquals(0, c.getInt(1)) // 0 < MpesaParser.VERSION, so the next sync rescans
            }
            db.query("SELECT amountCents FROM transactions").use { c ->
                c.moveToFirst()
                assertEquals(300000L, c.getLong(0))
            }
        }
    }
}
