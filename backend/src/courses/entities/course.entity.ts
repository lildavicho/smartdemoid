import {
    Entity,
    PrimaryGeneratedColumn,
    Column,
    CreateDateColumn,
    UpdateDateColumn,
    OneToMany,
} from 'typeorm';
import { AttendanceSession } from '../../attendance/entities/attendance-session.entity';

@Entity('courses')
export class Course {
    @PrimaryGeneratedColumn('uuid')
    id: string;

    @Column({ name: 'school_id', type: 'uuid' })
    schoolId: string;

    @Column({ type: 'varchar', length: 200 })
    name: string;

    @Column({ type: 'varchar', length: 50 })
    code: string;

    @Column({ name: 'academic_period', type: 'varchar', length: 50 })
    academicPeriod: string;

    @Column({ name: 'external_ids', type: 'jsonb', nullable: true })
    externalIds: Record<string, any>;

    @Column({ name: 'student_ids', type: 'jsonb', default: '[]' })
    studentIds: string[];

    @CreateDateColumn({ name: 'created_at' })
    createdAt: Date;

    @UpdateDateColumn({ name: 'updated_at' })
    updatedAt: Date;

    @OneToMany(() => AttendanceSession, (session) => session.course)
    attendanceSessions: AttendanceSession[];
}
