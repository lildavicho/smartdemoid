import { DynamicModule, Logger, Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { DataSource } from 'typeorm';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { AttendanceEventsModule } from './attendance-events/attendance-events.module';
import { AttendanceModule } from './attendance/attendance.module';
import { AuthModule } from './auth/auth.module';
import { getDatabaseConfig } from './config/database.config';
import { CoursesModule } from './courses/courses.module';
import { DevicesModule } from './devices/devices.module';
import { StudentsModule } from './students/students.module';

function loadEnvIfPresent(): void {
    const candidates = [
        resolve(process.cwd(), '.env'),
        resolve(process.cwd(), '../.env'),
    ];

    for (const filePath of candidates) {
        if (!existsSync(filePath)) continue;
        const content = readFileSync(filePath, 'utf8');
        for (const line of content.split(/\r?\n/)) {
            const trimmed = line.trim();
            if (!trimmed || trimmed.startsWith('#')) continue;

            const match = trimmed.match(/^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/);
            if (!match) continue;

            const key = match[1];
            let value = match[2] ?? '';

            if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.slice(1, -1);
            }

            if (process.env[key] == null) {
                process.env[key] = value;
            }
        }
    }
}

function hasDatabaseUrl(): boolean {
    return Boolean(process.env.DATABASE_URL || process.env.DIRECT_URL);
}

@Module({})
export class AppModule {
    static register(): DynamicModule {
        loadEnvIfPresent();
        const withDb = hasDatabaseUrl();

        return {
            module: AppModule,
            imports: [
                ConfigModule.forRoot({
                    isGlobal: true,
                    cache: true,
                    envFilePath: ['.env', '../.env'],
                }),
                ...(withDb
                    ? [
                        TypeOrmModule.forRootAsync({
                            imports: [ConfigModule],
                            useFactory: getDatabaseConfig,
                            inject: [ConfigService],
                            dataSourceFactory: async (options) => {
                                const typeormLogger = new Logger('typeorm');
                                const dataSource = new DataSource(options);
                                try {
                                    await dataSource.initialize();
                                } catch (error: any) {
                                    // Keep the app running so /healthz can respond; /readyz will report DB as not ready.
                                    typeormLogger.error(`TypeORM initialize failed: ${error?.message ?? error}`);
                                }
                                return dataSource;
                            },
                        }),
                        AuthModule,
                        AttendanceModule,
                        CoursesModule,
                        StudentsModule,
                        DevicesModule,
                        AttendanceEventsModule,
                    ]
                    : []),
            ],
            controllers: [AppController],
            providers: [AppService],
        };
    }
}
