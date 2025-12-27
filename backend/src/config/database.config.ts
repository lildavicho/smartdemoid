import { ConfigService } from '@nestjs/config';
import { TypeOrmModuleOptions } from '@nestjs/typeorm';

export const getDatabaseConfig = (
    configService: ConfigService,
): TypeOrmModuleOptions => {
    const databaseUrl =
        configService.get<string>('DATABASE_URL') ||
        configService.get<string>('DIRECT_URL') ||
        process.env.DATABASE_URL ||
        process.env.DIRECT_URL;

    if (!databaseUrl) {
        return {
            type: 'postgres',
            url: 'postgresql://invalid:invalid@invalid:5432/invalid',
            entities: [__dirname + '/../**/*.entity{.ts,.js}'],
            synchronize: false,
            logging: false,
        };
    }

    const nodeEnv = configService.get<string>('NODE_ENV') || process.env.NODE_ENV || 'development';
    const isProd = nodeEnv === 'production';

    const synchronize =
        (configService.get<string>('TYPEORM_SYNCHRONIZE') || '').toLowerCase() === 'true'
            ? true
            : !isProd;

    const logging =
        (configService.get<string>('TYPEORM_LOGGING') || '').toLowerCase() === 'true'
            ? true
            : !isProd;

    const forceSsl = (configService.get<string>('DB_SSL') || '').toLowerCase() === 'true';
    const isLocalDb = /localhost|127\.0\.0\.1/i.test(databaseUrl);
    const ssl = forceSsl || (!isLocalDb && isProd);

    return {
        type: 'postgres',
        url: databaseUrl,
        entities: [__dirname + '/../**/*.entity{.ts,.js}'],
        synchronize,
        logging,
        ssl: ssl ? { rejectUnauthorized: false } : undefined,
        extra: {
            connectionTimeoutMillis: Number(configService.get<string>('DB_CONNECT_TIMEOUT_MS') || 5_000),
        },
    };
};
