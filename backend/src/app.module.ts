import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { getDatabaseConfig } from './config/database.config';
import { AuthModule } from './auth/auth.module';
import { AttendanceModule } from './attendance/attendance.module';
import { CoursesModule } from './courses/courses.module';
import { StudentsModule } from './students/students.module';
import { DevicesModule } from './devices/devices.module';
import { AttendanceEventsModule } from './attendance-events/attendance-events.module';

@Module({
    imports: [
        ConfigModule.forRoot({
            isGlobal: true,
            envFilePath: '../.env',
        }),
        TypeOrmModule.forRootAsync({
            imports: [ConfigModule],
            useFactory: getDatabaseConfig,
            inject: [ConfigService],
        }),
        AuthModule,
        AttendanceModule,
        CoursesModule,
        StudentsModule,
        DevicesModule,
        AttendanceEventsModule,
    ],
    controllers: [AppController],
    providers: [AppService],
})
export class AppModule { }
