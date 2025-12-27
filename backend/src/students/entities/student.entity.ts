import {
    Entity,
    PrimaryGeneratedColumn,
    Column,
    CreateDateColumn,
    UpdateDateColumn,
    OneToMany,
    ManyToOne,
    JoinColumn,
} from 'typeorm';
import { FaceTemplate } from '../../face-templates/entities/face-template.entity';
import { AttendanceRecord } from '../../attendance/entities/attendance-record.entity';

@Entity('students')
export class Student {
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

    @Column({ name: 'external_ids', type: 'jsonb', nullable: true })
    externalIds: Record<string, any>;

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

    @OneToMany(() => FaceTemplate, (faceTemplate) => faceTemplate.student)
    faceTemplates: FaceTemplate[];

    @OneToMany(() => AttendanceRecord, (record) => record.student)
    attendanceRecords: AttendanceRecord[];
}
