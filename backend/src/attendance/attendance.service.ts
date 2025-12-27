import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { AttendanceSession } from './entities/attendance-session.entity';
import { AttendanceRecord } from './entities/attendance-record.entity';
import { SessionFinalization } from './entities/session-finalization.entity';
import { CreateAttendanceSessionDto } from './dto/create-attendance-session.dto';
import { UpdateAttendanceSessionDto } from './dto/update-attendance-session.dto';
import { FinalizeSessionDto } from './dto/finalize-session.dto';

@Injectable()
export class AttendanceService {
    constructor(
        @InjectRepository(AttendanceSession)
        private attendanceSessionRepository: Repository<AttendanceSession>,
        @InjectRepository(AttendanceRecord)
        private attendanceRecordRepository: Repository<AttendanceRecord>,
        @InjectRepository(SessionFinalization)
        private sessionFinalizationRepository: Repository<SessionFinalization>,
    ) { }

    async createSession(createDto: CreateAttendanceSessionDto) {
        // Extract teacherId and deviceId from JWT if not provided
        const teacherId = createDto.teacherId;
        const deviceId = createDto.deviceId;

        const session = this.attendanceSessionRepository.create({
            courseId: createDto.courseId,
            teacherId: teacherId,
            deviceId: deviceId,
            startedAt: new Date(),
            status: 'in_progress',
            metadata: createDto.metadata || {},
        });

        return this.attendanceSessionRepository.save(session);
    }

    async updateSession(id: string, updateDto: UpdateAttendanceSessionDto) {
        const session = await this.attendanceSessionRepository.findOne({
            where: { id },
            relations: ['attendanceRecords'],
        });

        if (!session) {
            throw new NotFoundException(`Attendance session with ID ${id} not found`);
        }

        // Update session fields
        if (updateDto.endedAt) {
            session.endedAt = new Date(updateDto.endedAt);
        }

        if (updateDto.status) {
            session.status = updateDto.status;
        }

        // Save attendance records if provided
        if (updateDto.attendanceRecords && updateDto.attendanceRecords.length > 0) {
            // Delete existing records for this session
            await this.attendanceRecordRepository.delete({ sessionId: id });

            // Create new records
            const records = updateDto.attendanceRecords.map((recordDto) => {
                return this.attendanceRecordRepository.create({
                    sessionId: id,
                    studentId: recordDto.studentId,
                    status: recordDto.status,
                    confidence: recordDto.confidence,
                    confirmedBy: recordDto.confirmedBy || 'ai',
                    detectedAt: recordDto.detectedAt ? new Date(recordDto.detectedAt) : new Date(),
                });
            });

            await this.attendanceRecordRepository.save(records);
        }

        await this.attendanceSessionRepository.save(session);

        // Return updated session with records
        return this.getSession(id);
    }

    async getSession(id: string) {
        const session = await this.attendanceSessionRepository.findOne({
            where: { id },
            relations: ['attendanceRecords', 'course', 'teacher', 'device'],
        });

        if (!session) {
            throw new NotFoundException(`Attendance session with ID ${id} not found`);
        }

        return session;
    }

    /**
     * Finalize session with idempotency
     * - Check if already finalized with same idempotency key
     * - If exists and status=applied, return success
     * - If exists and status=pending, apply finalization
     * - If not exists, create and apply
     */
    async finalizeSession(dto: FinalizeSessionDto): Promise<{ success: boolean; status: string; finalizationId: string }> {
        // Check for existing finalization
        let finalization = await this.sessionFinalizationRepository.findOne({
            where: { sessionId: dto.sessionId, idempotencyKey: dto.idempotencyKey },
        });

        if (finalization) {
            if (finalization.status === 'applied') {
                // Already applied, return success
                return {
                    success: true,
                    status: 'already_applied',
                    finalizationId: finalization.id,
                };
            }
        } else {
            // Create new finalization record
            finalization = this.sessionFinalizationRepository.create({
                sessionId: dto.sessionId,
                teacherId: dto.teacherId,
                courseId: dto.courseId,
                recordsJson: dto.recordsJson,
                idempotencyKey: dto.idempotencyKey,
                status: 'pending',
            });
            finalization = await this.sessionFinalizationRepository.save(finalization);
        }

        // Apply finalization
        try {
            // Update session status
            const session = await this.attendanceSessionRepository.findOne({
                where: { id: dto.sessionId },
            });

            if (!session) {
                finalization.status = 'rejected';
                finalization.errorMessage = 'Session not found';
                await this.sessionFinalizationRepository.save(finalization);
                return {
                    success: false,
                    status: 'rejected',
                    finalizationId: finalization.id,
                };
            }

            session.status = 'completed';
            session.endedAt = new Date();
            await this.attendanceSessionRepository.save(session);

            // Delete existing records and create new ones from recordsJson
            await this.attendanceRecordRepository.delete({ sessionId: dto.sessionId });

            const records = dto.recordsJson.map((record: any) => {
                return this.attendanceRecordRepository.create({
                    sessionId: dto.sessionId,
                    studentId: record.studentId,
                    status: record.status || 'present',
                    confidence: record.confidence,
                    confirmedBy: record.confirmedBy || 'system',
                    detectedAt: record.detectedAt ? new Date(record.detectedAt) : new Date(),
                });
            });

            await this.attendanceRecordRepository.save(records);

            // Mark finalization as applied
            finalization.status = 'applied';
            finalization.appliedAt = new Date();
            await this.sessionFinalizationRepository.save(finalization);

            return {
                success: true,
                status: 'applied',
                finalizationId: finalization.id,
            };
        } catch (error) {
            // Mark finalization as rejected
            const errorMessage = error instanceof Error ? error.message : 'Unknown error';
            finalization.status = 'rejected';
            finalization.errorMessage = errorMessage;
            await this.sessionFinalizationRepository.save(finalization);

            throw error;
        }
    }
}
