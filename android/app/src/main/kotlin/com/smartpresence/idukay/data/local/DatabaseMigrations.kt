package com.smartpresence.idukay.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pending_attendance (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sessionId TEXT NOT NULL,
                studentId TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                status TEXT NOT NULL,
                retryCount INTEGER NOT NULL,
                confidence REAL NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE IF EXISTS pending_attendance_backup")
        
        database.execSQL(
            """
            CREATE TABLE pending_attendance_backup (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sessionId TEXT NOT NULL,
                studentId TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                status TEXT NOT NULL,
                retryCount INTEGER NOT NULL,
                confidence REAL NOT NULL
            )
            """.trimIndent()
        )
        
        database.execSQL(
            """
            INSERT INTO pending_attendance_backup (id, sessionId, studentId, timestamp, status, retryCount, confidence)
            SELECT id, sessionId, studentId, timestamp, status, retryCount, confidence
            FROM pending_attendance
            """.trimIndent()
        )
        
        database.execSQL("DROP TABLE pending_attendance")
        
        database.execSQL(
            """
            CREATE TABLE pending_attendance (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                localId TEXT NOT NULL,
                sessionId TEXT NOT NULL,
                studentId TEXT NOT NULL,
                confidence REAL NOT NULL,
                detectedAt INTEGER NOT NULL,
                confirmedAt INTEGER NOT NULL,
                timestamp INTEGER NOT NULL,
                status TEXT NOT NULL,
                retryCount INTEGER NOT NULL
            )
            """.trimIndent()
        )
        
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pending_attendance_localId ON pending_attendance(localId)")
        
        database.execSQL(
            """
            INSERT INTO pending_attendance (id, localId, sessionId, studentId, confidence, detectedAt, confirmedAt, timestamp, status, retryCount)
            SELECT 
                id,
                hex(randomblob(16)),
                sessionId,
                studentId,
                confidence,
                timestamp,
                timestamp,
                timestamp,
                status,
                retryCount
            FROM pending_attendance_backup
            """.trimIndent()
        )
        
        database.execSQL("DROP TABLE pending_attendance_backup")
        
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pending_session_updates (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                localId TEXT NOT NULL,
                sessionId TEXT NOT NULL,
                status TEXT NOT NULL,
                recordsJson TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                syncStatus TEXT NOT NULL,
                retryCount INTEGER NOT NULL
            )
            """.trimIndent()
        )
        
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pending_session_updates_localId ON pending_session_updates(localId)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add courseId column to pending_session_updates table
        database.execSQL(
            """
            ALTER TABLE pending_session_updates 
            ADD COLUMN courseId TEXT
            """.trimIndent()
        )
    }
}
