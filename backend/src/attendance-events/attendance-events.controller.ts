import { Controller, Post, Get, Body, Param, UseGuards } from '@nestjs/common';
import { AttendanceEventsService } from './attendance-events.service';
import { BatchEventsDto } from './dto/batch-events.dto';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';

@Controller('attendance/events')
export class AttendanceEventsController {
    constructor(private readonly attendanceEventsService: AttendanceEventsService) { }

    @Post('batch')
    @UseGuards(JwtAuthGuard)
    async batchInsertEvents(@Body() dto: BatchEventsDto) {
        const result = await this.attendanceEventsService.batchInsertEvents(dto);

        return {
            success: true,
            message: `Processed ${result.total} events: ${result.inserted} inserted, ${result.ignored} ignored (duplicates)`,
            data: result,
        };
    }

    @Get('session/:sessionId')
    @UseGuards(JwtAuthGuard)
    async getEventsBySession(@Param('sessionId') sessionId: string) {
        const events = await this.attendanceEventsService.getEventsBySession(sessionId);

        return {
            success: true,
            data: events,
        };
    }
}
