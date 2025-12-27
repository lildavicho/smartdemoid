import {
    Entity,
    PrimaryGeneratedColumn,
    Column,
    CreateDateColumn,
    UpdateDateColumn,
    ManyToOne,
    JoinColumn,
} from 'typeorm';
import { AttendanceSession } from './attendance-session.entity';
import { Student } from '../../students/entities/student.entity';

@Entity('attendance_records')
export class AttendanceRecord {
    @PrimaryGeneratedColumn('uuid')
    id: string;

    @Column({ name: 'session_id', type: 'uuid' })
    sessionId: string;

    @Column({ name: 'student_id', type: 'uuid' })
    studentId: string;

    @Column({
        type: 'varchar',
        length: 20,
        default: 'pending',
    })
    status: string; // present, absent, pending

    @Column({ type: 'float', nullable: true })
    confidence: number; // Recognition confidence score

    @Column({ name: 'confirmed_by', type: 'varchar', length: 50, nullable: true })
    confirmedBy: string; // 'ai', 'teacher', 'manual'

    @Column({ name: 'detected_at', type: 'timestamp', nullable: true })
    detectedAt: Date;

    @Column({ type: 'jsonb', nullable: true })
    metadata: Record<string, any>;

    @CreateDateColumn({ name: 'created_at' })
    createdAt: Date;

    @UpdateDateColumn({ name: 'updated_at' })
    updatedAt: Date;

    @ManyToOne(() => AttendanceSession, (session) => session.attendanceRecords)
    @JoinColumn({ name: 'session_id' })
    session: AttendanceSession;

    @ManyToOne(() => Student, (student) => student.attendanceRecords)
    @JoinColumn({ name: 'student_id' })
    student: Student;
}
