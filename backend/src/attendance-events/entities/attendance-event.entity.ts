import { Column, Entity, PrimaryGeneratedColumn, CreateDateColumn, Index } from 'typeorm';

@Entity('attendance_events')
@Index(['sessionId', 'idempotencyKey'], { unique: true })
export class AttendanceEvent {
    @PrimaryGeneratedColumn('uuid')
    id: string;

    @Column({ type: 'text', name: 'session_id' })
    @Index()
    sessionId: string;

    @Column({ type: 'text', name: 'student_id' })
    @Index()
    studentId: string;

    @Column({ type: 'timestamptz', name: 'occurred_at' })
    occurredAt: Date;

    @Column({ type: 'real', nullable: true })
    confidence: number | null;

    @Column({ type: 'text', default: 'edge' })
    source: 'edge' | 'manual' | 'import';

    @Column({ type: 'text', name: 'idempotency_key' })
    idempotencyKey: string;

    @CreateDateColumn({ name: 'created_at' })
    createdAt: Date;
}
