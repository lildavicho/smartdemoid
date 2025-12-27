import {
    ArgumentsHost,
    Catch,
    ExceptionFilter,
    HttpException,
    HttpStatus,
    type LoggerService,
} from '@nestjs/common';
import type { Request, Response } from 'express';

type ErrorResponseBody = {
    statusCode: number;
    message: string;
    error?: string;
    path: string;
    method: string;
    timestamp: string;
    requestId?: string;
};

@Catch()
export class AllExceptionsFilter implements ExceptionFilter {
    constructor(private readonly logger: LoggerService) { }

    catch(exception: unknown, host: ArgumentsHost): void {
        const ctx = host.switchToHttp();
        const req = ctx.getRequest<Request>();
        const res = ctx.getResponse<Response>();

        const requestId = (req as any)?.requestId as string | undefined;

        let status =
            exception instanceof HttpException
                ? exception.getStatus()
                : HttpStatus.INTERNAL_SERVER_ERROR;

        const body: ErrorResponseBody = {
            statusCode: status,
            message: 'Internal Server Error',
            path: req.originalUrl,
            method: req.method,
            timestamp: new Date().toISOString(),
            requestId,
        };

        if (exception instanceof HttpException) {
            const response = exception.getResponse();

            if (typeof response === 'string') {
                body.message = response;
            } else if (response && typeof response === 'object') {
                const r = response as any;
                const msg = Array.isArray(r.message) ? r.message.join(', ') : r.message;
                body.message = msg || r.error || exception.message;
                if (typeof r.error === 'string') body.error = r.error;
            } else {
                body.message = exception.message;
            }
        } else if (exception && typeof exception === 'object') {
            const e = exception as any;
            const name = typeof e.name === 'string' ? e.name : '';
            const message = typeof e.message === 'string' ? e.message : '';

            const looksLikeDbNotReady =
                name === 'CannotExecuteNotConnectedError' ||
                name === 'ConnectionNotFoundError' ||
                name === 'EntityMetadataNotFoundError' ||
                message.includes('DataSource is not initialized') ||
                message.includes('not connected') ||
                message.includes('Connection terminated');

            if (looksLikeDbNotReady) {
                status = HttpStatus.SERVICE_UNAVAILABLE;
                body.statusCode = status;
                body.error = 'Service Unavailable';
                body.message = 'Database not ready';
            } else if (message.trim()) {
                body.message = message;
            }
        }

        const logLine = `${req.method} ${req.originalUrl} ${status}${requestId ? ` rid=${requestId}` : ''}`;

        if (status >= 500) {
            const stack = (exception as any)?.stack;
            this.logger.error(logLine, stack);
        } else if (status >= 400 && status !== HttpStatus.NOT_FOUND) {
            this.logger.warn(`${logLine} - ${body.message}`);
        }

        res.status(status).json(body);
    }
}
