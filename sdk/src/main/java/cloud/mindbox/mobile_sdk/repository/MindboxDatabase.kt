package cloud.mindbox.mobile_sdk.repository

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cloud.mindbox.mobile_sdk.converters.MindboxRoomConverter
import cloud.mindbox.mobile_sdk.data.ConfigurationsDao
import cloud.mindbox.mobile_sdk.data.EventsDao
import cloud.mindbox.mobile_sdk.data.PushDeliveriesDao
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.managers.DbManager.CONFIGURATION_TABLE_NAME
import cloud.mindbox.mobile_sdk.managers.DbManager.PUSH_DELIVERIES_TABLE_NAME
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.models.PushDelivery

@Database(entities = [Configuration::class, Event::class, PushDelivery::class], exportSchema = false, version = 5)
@TypeConverters(MindboxRoomConverter::class)
internal abstract class MindboxDatabase : RoomDatabase() {

    companion object {

        private const val DATABASE_NAME = "mindbox_db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {

            override fun migrate(db: SupportSQLiteDatabase) {
                val query = "ALTER TABLE $CONFIGURATION_TABLE_NAME " +
                    "ADD COLUMN shouldCreateCustomer INTEGER NOT NULL DEFAULT 1"
                db.execSQL(query)
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE $CONFIGURATION_TABLE_NAME ADD COLUMN operationsDomain TEXT"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {

            override fun migrate(db: SupportSQLiteDatabase) {
                mindboxLogI("DB migration 3->4 started")
                db.execSQL(
                    "ALTER TABLE $CONFIGURATION_TABLE_NAME " +
                        "ADD COLUMN shouldIncludeVersionCode INTEGER NOT NULL DEFAULT 1"
                )
                mindboxLogI("DB migration 3->4 finished")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {

            override fun migrate(db: SupportSQLiteDatabase) {
                mindboxLogI("DB migration 4->5 started")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `$PUSH_DELIVERIES_TABLE_NAME` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`url` TEXT NOT NULL, " +
                        "`receivedAt` INTEGER NOT NULL, " +
                        "`attempts` INTEGER NOT NULL, " +
                        "`throttles` INTEGER NOT NULL, " +
                        "`isHighPriority` INTEGER NOT NULL, " +
                        "`provider` TEXT, " +
                        "`nextAttemptAt` INTEGER)"
                )
                mindboxLogI("DB migration 4->5 finished")
            }
        }

        internal var isTestMode = false

        internal fun getInstance(context: Context) = if (!isTestMode) {
            Room
                .databaseBuilder(
                    context.applicationContext,
                    MindboxDatabase::class.java,
                    DATABASE_NAME,
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                )
                .build()
        } else {
            Room
                .inMemoryDatabaseBuilder(context.applicationContext, MindboxDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        }
    }

    abstract fun configurationDao(): ConfigurationsDao

    abstract fun eventsDao(): EventsDao

    abstract fun pushDeliveriesDao(): PushDeliveriesDao
}
