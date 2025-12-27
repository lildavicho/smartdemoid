import {
    Entity,
    PrimaryGeneratedColumn,
    Column,
    CreateDateColumn,
    UpdateDateColumn,
    OneToMany,
} from 'typeorm';
import { AttendanceSession } from '../../attendance/entities/attendance-session.entity';

@Entity('teachers')
export class Teacher {
    @PrimaryGeneratedColumn('uuid')
    id: string;

    @Column({ name: 'school_id', type: 'uuid' })
    schoolId: string;

    @Column({ name: 'document_id', type: 'varchar', length: 50 })
    documentId: string;

    @Column({ name: 'first_name', type: 'varchar', length: 100 })
    firstName: string;

    @Column({ name: 'last_name', type: 'varchar', length: 100 })
    lastName: string;

    @Column({ type: 'varchar', length: 255, nullable: true })
    email: string;

    @Column({ name: 'pin_code', type: 'varchar', length: 255 })
    pinCode: string; // Hashed PIN

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

    @OneToMany(() => AttendanceSession, (session) => session.teacher)
    attendanceSessions: AttendanceSession[];
}
