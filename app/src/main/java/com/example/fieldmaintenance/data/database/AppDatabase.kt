package com.example.fieldmaintenance.data.database

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.RoomDatabase
import com.example.fieldmaintenance.data.dao.AmplifierAdjustmentDao
import com.example.fieldmaintenance.data.dao.AssetDao
import com.example.fieldmaintenance.data.dao.MaintenanceReportDao
import com.example.fieldmaintenance.data.dao.PhotoDao
import com.example.fieldmaintenance.data.dao.PassiveItemDao
import com.example.fieldmaintenance.data.dao.ReportPhotoDao
import com.example.fieldmaintenance.data.dao.NodeAdjustmentDao
import com.example.fieldmaintenance.data.model.AmplifierAdjustment
import com.example.fieldmaintenance.data.model.Asset
import com.example.fieldmaintenance.data.model.MaintenanceReport
import com.example.fieldmaintenance.data.model.NodeAdjustment
import com.example.fieldmaintenance.data.model.Photo
import com.example.fieldmaintenance.data.model.PassiveItem
import com.example.fieldmaintenance.data.model.ReportPhoto

@Database(
    entities = [MaintenanceReport::class, Asset::class, Photo::class, AmplifierAdjustment::class, PassiveItem::class, ReportPhoto::class, NodeAdjustment::class],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun maintenanceReportDao(): MaintenanceReportDao
    abstract fun assetDao(): AssetDao
    abstract fun photoDao(): PhotoDao
    abstract fun amplifierAdjustmentDao(): AmplifierAdjustmentDao
    abstract fun passiveItemDao(): PassiveItemDao
    abstract fun reportPhotoDao(): ReportPhotoDao
    abstract fun nodeAdjustmentDao(): NodeAdjustmentDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE assets ADD COLUMN portIndex INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE maintenance_reports ADD COLUMN deletedAt INTEGER")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS amplifier_adjustments (
                        assetId TEXT NOT NULL PRIMARY KEY,
                        inputCh50Dbmv REAL,
                        inputCh116Dbmv REAL,
                        planLowFreqMHz INTEGER,
                        planLowDbmv REAL,
                        planHighFreqMHz INTEGER,
                        planHighDbmv REAL,
                        outCh50Dbmv REAL,
                        outCh70Dbmv REAL,
                        outCh110Dbmv REAL,
                        outCh116Dbmv REAL,
                        outCh136Dbmv REAL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS passive_items (
                        id TEXT NOT NULL PRIMARY KEY,
                        reportId TEXT NOT NULL,
                        address TEXT NOT NULL,
                        type TEXT NOT NULL,
                        observation TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add contractor (NOT NULL with default)
                db.execSQL("ALTER TABLE maintenance_reports ADD COLUMN contractor TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add selected high input frequency for amp input measurements (750 or 870). Default to 750.
                db.execSQL("ALTER TABLE amplifier_adjustments ADD COLUMN inputHighFreqMHz INTEGER")
                db.execSQL("UPDATE amplifier_adjustments SET inputHighFreqMHz = 750 WHERE inputHighFreqMHz IS NULL")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS report_photos (
                        id TEXT NOT NULL PRIMARY KEY,
                        reportId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        filePath TEXT NOT NULL,
                        fileName TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS node_adjustments (
                        assetId TEXT NOT NULL PRIMARY KEY,
                        reportId TEXT NOT NULL,
                        planNode TEXT,
                        planContractor TEXT,
                        planTechnology TEXT,
                        planPoDirecta TEXT,
                        planPoRetorno TEXT,
                        planDistanciaSfp TEXT,
                        tx1310Confirmed INTEGER NOT NULL DEFAULT 0,
                        tx1550Confirmed INTEGER NOT NULL DEFAULT 0,
                        poConfirmed INTEGER NOT NULL DEFAULT 0,
                        rxPadSelection TEXT,
                        measurementConfirmed INTEGER NOT NULL DEFAULT 0,
                        spectrumConfirmed INTEGER NOT NULL DEFAULT 0,
                        nonLegacyConfirmed INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add Plan snapshot columns (nullable)
                db.execSQL("ALTER TABLE node_adjustments ADD COLUMN planNode TEXT")
                db.execSQL("ALTER TABLE node_adjustments ADD COLUMN planContractor TEXT")
                db.execSQL("ALTER TABLE node_adjustments ADD COLUMN planTechnology TEXT")
                db.execSQL("ALTER TABLE node_adjustments ADD COLUMN planPoDirecta TEXT")
                db.execSQL("ALTER TABLE node_adjustments ADD COLUMN planPoRetorno TEXT")
                db.execSQL("ALTER TABLE node_adjustments ADD COLUMN planDistanciaSfp TEXT")
            }
        }
    }
}

