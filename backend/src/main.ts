import { ValidationPipe, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NestFactory } from '@nestjs/core';
import helmet from 'helmet';
import compression from 'compression';
import rateLimit from 'express-rate-limit';
import { randomUUID } from 'node:crypto';
import type { Request, Response, NextFunction } from 'express';
import { DataSource } from 'typeorm';
import { AppModule } from './app.module';
import { AllExceptionsFilter } from './common/filters/all-exceptions.filter';

function normalizeApiPrefix(input: string | undefined): string {
    const raw = (input ?? 'api/v1').trim();
    const noSlashes = raw.replace(/^\/+|\/+$/g, '');
    return noSlashes.length > 0 ? noSlashes : 'api/v1';
}

function parseCorsOrigins(raw: string | undefined): string[] | '*' | null {
    if (!raw) return null;
    const trimmed = raw.trim();
    if (!trimmed) return null;
    if (trimmed === '*') return '*';

    const origins = trimmed
        .split(',')
        .map((o) => o.trim())
        .filter(Boolean);

    return origins.length > 0 ? origins : null;
}

async function bootstrap() {
    const logger = new Logger('bootstrap');

    const app = await NestFactory.create(AppModule.register(), {
        bufferLogs: true,
    });

    const configService = app.get(ConfigService);
    app.useLogger(configService.get<string>('NODE_ENV') === 'production'
        ? ['log', 'warn', 'error']
        : ['log', 'warn', 'error', 'debug', 'verbose']);

    const httpAdapter = app.getHttpAdapter();
    const instance = httpAdapter.getInstance();

    if (typeof instance?.set === 'function') {
        instance.set('trust proxy', 1);
        instance.disable('x-powered-by');
    }

    const apiPrefix = normalizeApiPrefix(configService.get<string>('API_PREFIX'));

    const corsOrigins = parseCorsOrigins(configService.get<string>('CORS_ORIGINS'));
    const corsCredentials = configService.get<string>('CORS_CREDENTIALS') === 'true';

    if (corsOrigins !== null) {
        app.enableCors({
            origin: (origin, callback) => {
                if (!origin) return callback(null, true);
                if (corsOrigins === '*') return callback(null, !corsCredentials);
                return callback(null, corsOrigins.includes(origin));
            },
            credentials: corsCredentials,
            methods: ['GET', 'HEAD', 'PUT', 'PATCH', 'POST', 'DELETE', 'OPTIONS'],
            allowedHeaders: ['Authorization', 'Content-Type', 'Accept', 'Origin', 'X-Requested-With', 'X-Request-Id'],
            exposedHeaders: ['X-Request-Id'],
        });

        if (corsOrigins === '*' && corsCredentials) {
            logger.warn('CORS_ORIGINS="*" is incompatible with CORS_CREDENTIALS=true; use a concrete CORS_ORIGINS list or set CORS_CREDENTIALS=false.');
        }
    }

    app.use(helmet({
        contentSecurityPolicy: false,
        crossOriginEmbedderPolicy: false,
    }));

    app.use(compression());
    app.enableShutdownHooks();

    app.use((req: Request, res: Response, next: NextFunction) => {
        const requestId = (req.header('x-request-id') || randomUUID()).toString();
        (req as any).requestId = requestId;
        res.setHeader('x-request-id', requestId);
        next();
    });

    const rateLimitWindowMs = Number(configService.get<string>('RATE_LIMIT_WINDOW_MS') || 60_000);
    const rateLimitMax = Number(configService.get<string>('RATE_LIMIT_MAX') || 300);

    app.use(rateLimit({
        windowMs: rateLimitWindowMs,
        limit: rateLimitMax,
        standardHeaders: true,
        legacyHeaders: false,
        skip: (req) => req.path === '/healthz' || req.path === '/readyz',
        handler: (req, res) => {
            const requestId = (req as any).requestId as string | undefined;
            res.status(429).json({
                statusCode: 429,
                message: 'Too Many Requests',
                path: req.originalUrl,
                timestamp: new Date().toISOString(),
                requestId,
            });
        },
    }));

    app.use((req: Request, res: Response, next: NextFunction) => {
        if (req.path === '/healthz') return next();
        const start = Date.now();
        const requestId = (req as any).requestId as string | undefined;

        res.on('finish', () => {
            const ms = Date.now() - start;
            logger.log(`${req.method} ${req.originalUrl} ${res.statusCode} ${ms}ms${requestId ? ` rid=${requestId}` : ''}`);
        });

        next();
    });

    instance.get('/', (_req: Request, res: Response) => {
        res.status(200).json({
            service: 'smartpresence-backend',
            apiPrefix: `/${apiPrefix}`,
            status: 'ok',
        });
    });

    instance.get('/healthz', (_req: Request, res: Response) => {
        res.status(200).json({ status: 'ok' });
    });

    instance.get('/readyz', async (_req: Request, res: Response) => {
        const databaseUrl =
            configService.get<string>('DATABASE_URL') ||
            configService.get<string>('DIRECT_URL') ||
            process.env.DATABASE_URL ||
            process.env.DIRECT_URL ||
            '';
        if (!databaseUrl) {
            return res.status(503).json({
                status: 'not_ready',
                checks: { database: 'missing DATABASE_URL/DIRECT_URL' },
            });
        }

        try {
            const dataSource = app.get(DataSource, { strict: false });
            if (!dataSource?.isInitialized) {
                return res.status(503).json({
                    status: 'not_ready',
                    checks: { database: 'not_initialized' },
                });
            }

            await dataSource.query('SELECT 1');

            return res.status(200).json({
                status: 'ready',
                checks: { database: 'ok' },
            });
        } catch (error: any) {
            logger.error(`readyz database check failed: ${error?.message ?? error}`);
            return res.status(503).json({
                status: 'not_ready',
                checks: { database: 'failed' },
            });
        }
    });

    app.useGlobalPipes(new ValidationPipe({
        whitelist: true,
        forbidNonWhitelisted: true,
        transform: true,
        transformOptions: { enableImplicitConversion: true },
    }));

    app.useGlobalFilters(new AllExceptionsFilter(logger));

    app.setGlobalPrefix(apiPrefix);

    const port = Number(process.env.PORT || configService.get<number>('PORT') || 3000);
    await app.listen(port, '0.0.0.0');

    const server: any = app.getHttpServer?.();
    if (server) {
        server.keepAliveTimeout = 65_000;
        server.headersTimeout = 70_000;
        server.requestTimeout = 60_000;
    }

    const missing = [
        ['DATABASE_URL', !!(configService.get<string>('DATABASE_URL') || configService.get<string>('DIRECT_URL') || process.env.DATABASE_URL || process.env.DIRECT_URL)],
        ['JWT_SECRET', !!(configService.get<string>('JWT_SECRET') || process.env.JWT_SECRET)],
    ].filter(([, ok]) => !ok).map(([key]) => key);

    if (missing.length > 0) {
        logger.warn(`Missing env vars for full operation: ${missing.join(', ')}`);
    }

    logger.log(`Listening on 0.0.0.0:${port} (prefix=/${apiPrefix})`);
}

void bootstrap();
