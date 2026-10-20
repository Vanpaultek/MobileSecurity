/*
 * Copyright (C) 2026 57Million LLC
 *
 * This file is part of MobileSecurity.
 *
 * MobileSecurity is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MobileSecurity is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.mobilesecurity.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mobilesecurity.app.data.db.entities.BlockedNumber
import com.mobilesecurity.app.data.db.entities.SecurityEvent
import com.mobilesecurity.app.data.db.entities.ShieldSession
import com.mobilesecurity.app.data.db.entities.TrustedContact
import com.mobilesecurity.app.data.db.entities.TrustedNumber
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom

@Database(
    entities = [
        SecurityEvent::class,
        BlockedNumber::class,
        TrustedContact::class,
        TrustedNumber::class,
        ShieldSession::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao
    abstract fun blockedNumberDao(): BlockedNumberDao
    abstract fun trustedContactDao(): TrustedContactDao
    abstract fun shieldSessionDao(): ShieldSessionDao

    companion object {
        private const val DB_NAME = "mobile_security.db"
        private const val PREFS_NAME = "mobile_security_prefs"
        private const val KEY_DB_PASSPHRASE = "db_passphrase"

        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): AppDatabase {
            SQLiteDatabase.loadLibs(context)
            val passphrase = resolvePassphrase(context)
            val factory = SupportFactory(passphrase)

            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }

        private fun resolvePassphrase(context: Context): ByteArray {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            val existing = prefs.getString(KEY_DB_PASSPHRASE, null)
            val secret = existing ?: run {
                val random = ByteArray(32).also { SecureRandom().nextBytes(it) }
                val encoded = android.util.Base64.encodeToString(random, android.util.Base64.NO_WRAP)
                prefs.edit().putString(KEY_DB_PASSPHRASE, encoded).apply()
                encoded
            }
            return secret.toCharArray().joinToString("").toByteArray(Charsets.UTF_8)
        }
    }
}
