import {
    Entity,
    PrimaryGeneratedColumn,
    Column,
    CreateDateColumn,
    UpdateDateColumn,
    OneToMany,
} from 'typeorm';
import { AttendanceSession } from '../../attendance/entities/attendance-session.entity';

@Entity('devices')
export class Device {
    @PrimaryGeneratedColumn('uuid')
    id: string;

    @Column({ name: 'serial_number', type: 'varchar', length: 100, unique: true })
    serialNumber: string;

    @Column({ type: 'varchar', length: 100, nullable: true })
    model: string;

    @Column({ name: 'school_id', type: 'uuid' })
    schoolId: string;

    @Column({ type: 'varchar', length: 200, nullable: true })
    location: string;

    @Column({ name: 'app_version', type: 'varchar', length: 50, nullable: true })
    appVersion: string;

    @Column({
        type: 'varchar',
        length: 20,
        default: 'active',
    })
    status: string;

    @CreateDateColumn({ name: 'created_at' })
    createdAt: Date;

    @UpdateDateColumn({ name: 'updated_at' })
    updatedAt: Date;

    @OneToMany(() => AttendanceSession, (session) => session.device)
    attendanceSessions: AttendanceSession[];
}
