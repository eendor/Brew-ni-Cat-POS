package com.example.cattasticpos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.cattasticpos.data.local.dao.MenuDao
import com.example.cattasticpos.data.local.dao.OrderDao
import com.example.cattasticpos.data.local.entity.CategoryEntity
import com.example.cattasticpos.data.local.entity.ItemEntity
import com.example.cattasticpos.data.local.entity.OrderEntity
import com.example.cattasticpos.data.local.entity.OrderItemEntity
import com.example.cattasticpos.data.local.entity.ExpenseEntity
import com.example.cattasticpos.data.local.dao.ExpenseDao
import com.example.cattasticpos.data.local.entity.InventoryEntity
import com.example.cattasticpos.data.local.dao.InventoryDao
import com.example.cattasticpos.data.local.entity.RecipeMappingEntity
import com.example.cattasticpos.data.local.dao.RecipeDao
import com.example.cattasticpos.data.local.entity.AppConfigEntity
import com.example.cattasticpos.data.local.dao.AppConfigDao
import com.example.cattasticpos.data.local.dao.VoidDao
import com.example.cattasticpos.data.local.entity.VoidRecordEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CategoryEntity::class,
        ItemEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        ExpenseEntity::class,
        InventoryEntity::class,
        RecipeMappingEntity::class,
        AppConfigEntity::class,
        VoidRecordEntity::class
    ],
    version = 20,
    exportSchema = false
)
abstract class PosDatabase : RoomDatabase() {
    abstract fun menuDao(): MenuDao
    abstract fun orderDao(): OrderDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun recipeDao(): RecipeDao
    abstract fun appConfigDao(): AppConfigDao
    abstract fun voidDao(): VoidDao

    companion object {
        @Volatile
        private var INSTANCE: PosDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): PosDatabase {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                DatabaseSafetyBackup.prepareForUpgrade(appContext)
                val instance = buildDatabase(appContext, scope)
                INSTANCE = instance
                try {
                    instance.openHelper.writableDatabase
                } catch (e: Exception) {
                    INSTANCE = null
                    DatabaseSafetyBackup.markMigrationFailed(appContext)
                    android.util.Log.e("PosDatabase", "Database open/migration failed", e)
                    throw e
                }
                instance
            }
        }

        private fun buildDatabase(appContext: Context, scope: CoroutineScope): PosDatabase {
            return Room.databaseBuilder(
                appContext,
                PosDatabase::class.java,
                "pos_database"
            )
                .addMigrations(
                    MIGRATION_6_10,
                    MIGRATION_7_10,
                    MIGRATION_8_10,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_10_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20
                )
                .addCallback(PosDatabaseCallback(appContext, scope))
                .addCallback(MigrationSuccessCallback(appContext))
                .build()
        }

        val MIGRATION_6_10 = object : androidx.room.migration.Migration(6, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create app_config table
                db.execSQL("CREATE TABLE IF NOT EXISTS app_config (id INTEGER PRIMARY KEY NOT NULL, targetSales REAL NOT NULL, startingCashFloat REAL NOT NULL, pinHash TEXT NOT NULL DEFAULT 'otCBSIxSZkk6vcF7SKwqCw==:Seyex1KVzCA7gLC3+1Vi8AHYtjU7A168GCGRihADbp0=')")
                db.execSQL("INSERT OR IGNORE INTO app_config (id, targetSales, startingCashFloat, pinHash) VALUES (1, 5000.0, 500.0, 'otCBSIxSZkk6vcF7SKwqCw==:Seyex1KVzCA7gLC3+1Vi8AHYtjU7A168GCGRihADbp0=')")

                // Migrate inventory schema to Double types
                db.execSQL("CREATE TABLE IF NOT EXISTS inventory_new (id TEXT NOT NULL PRIMARY KEY, itemName TEXT NOT NULL, unit TEXT NOT NULL, currentStock REAL NOT NULL, reorderThreshold REAL NOT NULL)")
                db.execSQL("INSERT INTO inventory_new (id, itemName, unit, currentStock, reorderThreshold) SELECT id, itemName, unit, CAST(currentStock AS REAL), CAST(reorderThreshold AS REAL) FROM inventory")
                db.execSQL("DROP TABLE inventory")
                db.execSQL("ALTER TABLE inventory_new RENAME TO inventory")
            }
        }

        val MIGRATION_7_10 = object : androidx.room.migration.Migration(7, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_config ADD COLUMN pinHash TEXT NOT NULL DEFAULT 'otCBSIxSZkk6vcF7SKwqCw==:Seyex1KVzCA7gLC3+1Vi8AHYtjU7A168GCGRihADbp0='")

                // Migrate inventory schema to Double types
                db.execSQL("CREATE TABLE IF NOT EXISTS inventory_new (id TEXT NOT NULL PRIMARY KEY, itemName TEXT NOT NULL, unit TEXT NOT NULL, currentStock REAL NOT NULL, reorderThreshold REAL NOT NULL)")
                db.execSQL("INSERT INTO inventory_new (id, itemName, unit, currentStock, reorderThreshold) SELECT id, itemName, unit, CAST(currentStock AS REAL), CAST(reorderThreshold AS REAL) FROM inventory")
                db.execSQL("DROP TABLE inventory")
                db.execSQL("ALTER TABLE inventory_new RENAME TO inventory")
            }
        }

        val MIGRATION_8_10 = object : androidx.room.migration.Migration(8, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_config ADD COLUMN pinHash TEXT NOT NULL DEFAULT 'otCBSIxSZkk6vcF7SKwqCw==:Seyex1KVzCA7gLC3+1Vi8AHYtjU7A168GCGRihADbp0='")

                // Migrate inventory schema to Double types
                db.execSQL("CREATE TABLE IF NOT EXISTS inventory_new (id TEXT NOT NULL PRIMARY KEY, itemName TEXT NOT NULL, unit TEXT NOT NULL, currentStock REAL NOT NULL, reorderThreshold REAL NOT NULL)")
                db.execSQL("INSERT INTO inventory_new (id, itemName, unit, currentStock, reorderThreshold) SELECT id, itemName, unit, CAST(currentStock AS REAL), CAST(reorderThreshold AS REAL) FROM inventory")
                db.execSQL("DROP TABLE inventory")
                db.execSQL("ALTER TABLE inventory_new RENAME TO inventory")
            }
        }

        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_config ADD COLUMN pinHash TEXT NOT NULL DEFAULT 'otCBSIxSZkk6vcF7SKwqCw==:Seyex1KVzCA7gLC3+1Vi8AHYtjU7A168GCGRihADbp0='")
                db.execSQL("CREATE TABLE inventory_new (id TEXT NOT NULL PRIMARY KEY, itemName TEXT NOT NULL, unit TEXT NOT NULL, currentStock REAL NOT NULL, reorderThreshold REAL NOT NULL)")
                db.execSQL("INSERT INTO inventory_new (id, itemName, unit, currentStock, reorderThreshold) SELECT id, itemName, unit, CAST(currentStock AS REAL), CAST(reorderThreshold AS REAL) FROM inventory")
                db.execSQL("DROP TABLE inventory")
                db.execSQL("ALTER TABLE inventory_new RENAME TO inventory")
            }
        }

        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                DatabaseMigrationUtils.migrate10To11(db)
            }
        }

        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                DatabaseMigrationUtils.migrate11To12(db)
            }
        }

        /** Single-hop path for users still on v10 — preserves all orders in one migration. */
        val MIGRATION_10_12 = object : androidx.room.migration.Migration(10, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                DatabaseMigrationUtils.migrate10To11(db)
                DatabaseMigrationUtils.migrate11To12(db)
            }
        }

        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_orders_timestamp ON orders(timestamp)")
            }
        }

        val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN isServed INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN deviceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE orders ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE app_config ADD COLUMN supabaseUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_config ADD COLUMN supabaseAnonKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_config ADD COLUMN deviceId TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN isVoided INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE orders ADD COLUMN lastSyncedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN remoteId INTEGER")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_orders_remoteId ON orders(remoteId)")
            }
        }

        // Standardize the cash-drawer starting float to PHP 1500 (owner's operating standard).
        val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE app_config SET startingCashFloat = 1500.0")
            }
        }

        // Recover add-on surcharges that were rung up but never charged. See
        // [AddOnSurchargeRepair] for the encoding bug behind it. No schema change — the version
        // bump exists purely so the data repair runs exactly once per device.
        val MIGRATION_18_19 = object : androidx.room.migration.Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AddOnSurchargeRepair.run(db)
            }
        }

        val MIGRATION_19_20 = object : androidx.room.migration.Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN isAvailable INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE items ADD COLUMN icon TEXT")
                db.execSQL("ALTER TABLE app_config ADD COLUMN catalogVersion INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

    private class MigrationSuccessCallback(
        private val appContext: Context
    ) : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            DatabaseSafetyBackup.markMigrationSucceeded(appContext)
        }
    }

    private class PosDatabaseCallback(
        private val appContext: Context,
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            seedIfNeeded()
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            seedIfNeeded()
        }

        private fun seedIfNeeded() {
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    DatabaseSeeder.seedDefaultsIfMissing(
                        database.menuDao(),
                        database.inventoryDao(),
                        database.recipeDao(),
                        database.appConfigDao()
                    )
                    MenuContentUpdater.applyPendingUpdates(
                        database.menuDao(),
                        database.inventoryDao(),
                        database.recipeDao(),
                        database.appConfigDao()
                    )
                    com.example.cattasticpos.worker.SyncWorker.triggerImmediateSync(appContext)
                }
            }
        }
    }
}
