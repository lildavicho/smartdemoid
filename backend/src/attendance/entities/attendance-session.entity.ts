import {
    Entity,
    PrimaryGeneratedColumn,
    Column,
    CreateDateColumn,
    UpdateDateColumn,
    ManyToOne,
    OneToMany,
    JoinColumn,
} from 'typeorm';
import { Course } from '../../courses/entities/course.entity';
import { Teacher } from '../../teachers/entities/teacher.entity';
import { Device } from '../../devices/entities/device.entity';
import { AttendanceRecord } from './attendance-record.entity';

@Entity('attendance_sessions')
export class AttendanceSession {
    @PrimaryGeneratedColumn('uuid')
    id: string;

    @Column({ name: 'course_id', type: 'uuid' })
    courseId: string;

    @Column({ name: 'teacher_id', type: 'uuid' })
    teacherId: string;

    @Column({ name: 'device_id', type: 'uuid' })
    deviceId: string;

    @Column({ name: 'started_at', type: 'timestamp' })
    startedAt: Date;

    @Column({ name: 'ended_at', type: 'timestamp', nullable: true })
    endedAt: Date;

    @Column({
        type: 'varchar',
        length: 20,
        default: 'in_progress',
    })
    status: string; // in_progress, completed, cancelled

    @Column({ type: 'jsonb', nullable: true })
    metadata: Record<string, any>;

    @CreateDateColumn({ name: 'created_at' })
    createdAt: Date;

    @UpdateDateColumn({ name: 'updated_at' })
    updatedAt: Date;

    @ManyToOne(() => Course, (course) => course.attendanceSessions)
    @JoinColumn({ name: 'course_id' })
    course: Course;

    @ManyToOne(() => Teacher, (teacher) => teacher.attendanceSessions)
    @JoinColumn({ name: 'teacher_id' })
    teacher: Teacher;

    @ManyToOne(() => Device, (device) => device.attendanceSessions)
    @JoinColumn({ name: 'device_id' })
    device: Device;

    @OneToMany(() => AttendanceRecord, (record) => record.session)
    attendanceRecords: AttendanceRecord[];
}
