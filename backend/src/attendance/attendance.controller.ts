import { Controller, Post, Put, Get, Body, Param, UseGuards, Request } from '@nestjs/common';
import { AttendanceService } from './attendance.service';
import { CreateAttendanceSessionDto } from './dto/create-attendance-session.dto';
import { UpdateAttendanceSessionDto } from './dto/update-attendance-session.dto';
import { FinalizeSessionDto } from './dto/finalize-session.dto';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';

@Controller('attendance')
@UseGuards(JwtAuthGuard)
export class AttendanceController {
    constructor(private readonly attendanceService: AttendanceService) { }

    @Post('sessions')
    async createSession(@Request() req, @Body() createDto: CreateAttendanceSessionDto) {
        // Extract teacherId and deviceId from JWT if not provided in body
        if (!createDto.teacherId) {
            createDto.teacherId = req.user.teacherId;
        }
        if (!createDto.deviceId) {
            createDto.deviceId = req.user.deviceId;
        }
        return this.attendanceService.createSession(createDto);
    }

    @Put('sessions/:id')
    async updateSession(
        @Param('id') id: string,
        @Body() updateDto: UpdateAttendanceSessionDto,
    ) {
        return this.attendanceService.updateSession(id, updateDto);
    }

    @Get('sessions/:id')
    async getSession(@Param('id') id: string) {
        return this.attendanceService.getSession(id);
    }

    @Post('sessions/finalize')
    async finalizeSession(@Body() dto: FinalizeSessionDto) {
        const result = await this.attendanceService.finalizeSession(dto);
        return {
            success: result.success,
            message: result.status === 'applied' ? 'Session finalized successfully' :
                result.status === 'already_applied' ? 'Session already finalized' :
                    'Session finalization rejected',
            data: result,
        };
    }
}
