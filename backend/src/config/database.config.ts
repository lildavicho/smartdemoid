import { ConfigService } from '@nestjs/config';
import { TypeOrmModuleOptions } from '@nestjs/typeorm';

export const getDatabaseConfig = (
    configService: ConfigService,
): TypeOrmModuleOptions => {
    const databaseUrl = configService.get<string>('DATABASE_URL');

    if (!databaseUrl) {
        throw new Error('DATABASE_URL is not defined');
    }

    return {
        type: 'postgres',
        url: databaseUrl,
        entities: [__dirname + '/../**/*.entity{.ts,.js}'],
        synchronize: true, // Solo en desarrollo
        logging: true,
        ssl: {
            rejectUnauthorized: false,
        },
    };
};
