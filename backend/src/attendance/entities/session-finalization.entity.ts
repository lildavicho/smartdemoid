import { Column, Entity, PrimaryGeneratedColumn, CreateDateColumn, UpdateDateColumn, Index } from 'typeorm';

@Entity('session_finalizations')
@Index(['sessionId', 'idempotencyKey'], { unique: true })
export class SessionFinalization {
    @PrimaryGeneratedColumn('uuid')
    id: string;

    @Column({ type: 'text', name: 'session_id' })
    @Index()
    sessionId: string;

    @Column({ type: 'text', name: 'teacher_id' })
    @Index()
    teacherId: string;

    @Column({ type: 'text', name: 'course_id' })
    courseId: string;

    @Column({ type: 'jsonb', name: 'records_json' })
    recordsJson: any;

    @Column({ type: 'text', name: 'idempotency_key' })
    idempotencyKey: string;

    @Column({ type: 'text', default: 'pending' })
    @Index()
    status: 'pending' | 'applied' | 'rejected';

    @Column({ type: 'text', name: 'error_message', nullable: true })
    errorMessage: string | null;

    @CreateDateColumn({ name: 'created_at' })
    createdAt: Date;

    @Column({ type: 'timestamptz', name: 'applied_at', nullable: true })
    appliedAt: Date | null;

    @UpdateDateColumn({ name: 'updated_at' })
    updatedAt: Date;
}
