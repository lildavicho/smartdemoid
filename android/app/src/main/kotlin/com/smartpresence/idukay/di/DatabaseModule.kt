package com.smartpresence.idukay.di

import android.content.Context
import androidx.room.Room
import com.smartpresence.idukay.data.local.AppDatabase
import com.smartpresence.idukay.data.local.MIGRATION_1_2
import com.smartpresence.idukay.data.local.MIGRATION_2_3
import com.smartpresence.idukay.data.local.MIGRATION_3_4
import com.smartpresence.idukay.data.local.dao.AttendanceSessionDao
import com.smartpresence.idukay.data.local.dao.CourseDao
import com.smartpresence.idukay.data.local.dao.FaceTemplateDao
import com.smartpresence.idukay.data.local.dao.PendingAttendanceDao
import com.smartpresence.idukay.data.local.dao.PendingSessionUpdateDao
import com.smartpresence.idukay.data.local.dao.StudentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "smartpresence_db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideStudentDao(database: AppDatabase): StudentDao {
        return database.studentDao()
    }
    
    @Provides
    @Singleton
    fun provideCourseDao(database: AppDatabase): CourseDao {
        return database.courseDao()
    }
    
    @Provides
    @Singleton
    fun provideAttendanceSessionDao(database: AppDatabase): AttendanceSessionDao {
        return database.attendanceSessionDao()
    }
    
    @Provides
    @Singleton
    fun provideFaceTemplateDao(database: AppDatabase): FaceTemplateDao {
        return database.faceTemplateDao()
    }
    
    @Provides
    @Singleton
    fun providePendingAttendanceDao(database: AppDatabase): PendingAttendanceDao {
        return database.pendingAttendanceDao()
    }
    
    @Provides
    @Singleton
    fun providePendingSessionUpdateDao(database: AppDatabase): PendingSessionUpdateDao {
        return database.pendingSessionUpdateDao()
    }
}
