import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { AttendanceEvent } from './entities/attendance-event.entity';
import { BatchEventsDto, AttendanceEventItemDto } from './dto/batch-events.dto';

@Injectable()
export class AttendanceEventsService {
    private readonly logger = new Logger(AttendanceEventsService.name);

    constructor(
        @InjectRepository(AttendanceEvent)
        private attendanceEventRepository: Repository<AttendanceEvent>,
    ) { }

    /**
     * Batch insert attendance events with idempotency
     * Uses ON CONFLICT DO NOTHING to prevent duplicates
     * Returns count of inserted vs ignored events
     */
    async batchInsertEvents(dto: BatchEventsDto): Promise<{ inserted: number; ignored: number; total: number }> {
        const total = dto.events.length;
        let inserted = 0;

        this.logger.log(`Processing batch of ${total} events for session ${dto.sessionId}`);

        for (const eventDto of dto.events) {
            try {
                const event = this.attendanceEventRepository.create({
                    sessionId: dto.sessionId,
                    studentId: eventDto.studentId,
                    occurredAt: new Date(eventDto.occurredAt),
                    confidence: eventDto.confidence ?? null,
                    source: eventDto.source || 'edge',
                    idempotencyKey: eventDto.idempotencyKey,
                });

                // Use INSERT ... ON CONFLICT DO NOTHING
                const result = await this.attendanceEventRepository
                    .createQueryBuilder()
                    .insert()
                    .into(AttendanceEvent)
                    .values(event)
                    .orIgnore() // ON CONFLICT DO NOTHING
                    .execute();

                if (result.identifiers.length > 0) {
                    inserted++;
                }
            } catch (error) {
                // Log but continue processing other events
                const errorMessage = error instanceof Error ? error.message : 'Unknown error';
                this.logger.warn(`Failed to insert event with key ${eventDto.idempotencyKey}: ${errorMessage}`);
            }
        }

        const ignored = total - inserted;
        this.logger.log(`Batch complete: ${inserted} inserted, ${ignored} ignored (duplicates)`);

        return { inserted, ignored, total };
    }

    /**
     * Get events for a session
     */
    async getEventsBySession(sessionId: string): Promise<AttendanceEvent[]> {
        return await this.attendanceEventRepository.find({
            where: { sessionId },
            order: { occurredAt: 'ASC' },
        });
    }

    /**
     * Check if event exists by idempotency key
     */
    async eventExists(sessionId: string, idempotencyKey: string): Promise<boolean> {
        const count = await this.attendanceEventRepository.count({
            where: { sessionId, idempotencyKey },
        });
        return count > 0;
    }
}
