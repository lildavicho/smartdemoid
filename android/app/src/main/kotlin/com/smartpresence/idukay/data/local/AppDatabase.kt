package com.smartpresence.idukay.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.smartpresence.idukay.data.local.dao.AttendanceSessionDao
import com.smartpresence.idukay.data.local.dao.CourseDao
import com.smartpresence.idukay.data.local.dao.FaceTemplateDao
import com.smartpresence.idukay.data.local.dao.PendingAttendanceDao
import com.smartpresence.idukay.data.local.dao.PendingSessionUpdateDao
import com.smartpresence.idukay.data.local.dao.StudentDao
import com.smartpresence.idukay.data.local.entity.AttendanceSessionEntity
import com.smartpresence.idukay.data.local.entity.CourseEntity
import com.smartpresence.idukay.data.local.entity.FaceTemplateEntity
import com.smartpresence.idukay.data.local.entity.PendingAttendanceEntity
import com.smartpresence.idukay.data.local.entity.PendingSessionUpdateEntity
import com.smartpresence.idukay.data.local.entity.StudentEntity

@Database(
    entities = [
        StudentEntity::class,
        CourseEntity::class,
        AttendanceSessionEntity::class,
        FaceTemplateEntity::class,
        PendingAttendanceEntity::class,
        PendingSessionUpdateEntity::class
    ],
    version = 4, // Incremented from 3 to 4
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun courseDao(): CourseDao
    abstract fun attendanceSessionDao(): AttendanceSessionDao
    abstract fun faceTemplateDao(): FaceTemplateDao
    abstract fun pendingAttendanceDao(): PendingAttendanceDao
    abstract fun pendingSessionUpdateDao(): PendingSessionUpdateDao
}
