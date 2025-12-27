import { NestFactory } from '@nestjs/core';
import { ValidationPipe } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AppModule } from './app.module';

async function bootstrap() {
    const app = await NestFactory.create(AppModule);

    const configService = app.get(ConfigService);

    // Enable CORS
    app.enableCors({
        origin: '*',
        methods: 'GET,HEAD,PUT,PATCH,POST,DELETE,OPTIONS',
        credentials: true,
    });

    // Set global prefix
    const apiPrefix = configService.get<string>('API_PREFIX') || 'api/v1';
    app.setGlobalPrefix(apiPrefix);

    // Enable global validation pipe
    app.useGlobalPipes(
        new ValidationPipe({
            whitelist: true,
            forbidNonWhitelisted: true,
            transform: true,
            transformOptions: {
                enableImplicitConversion: true,
            },
        }),
    );

    const port = configService.get<number>('PORT') || 3000;
    await app.listen(port);

    console.log(`🚀 SmartPresence Backend running on: http://localhost:${port}/${apiPrefix}`);
}

bootstrap();
