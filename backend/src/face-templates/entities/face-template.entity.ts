import {
    Entity,
    PrimaryGeneratedColumn,
    Column,
    CreateDateColumn,
    UpdateDateColumn,
    ManyToOne,
    JoinColumn,
} from 'typeorm';
import { Student } from '../../students/entities/student.entity';

@Entity('face_templates')
export class FaceTemplate {
    @PrimaryGeneratedColumn('uuid')
    id: string;

    @Column({ name: 'student_id', type: 'uuid' })
    studentId: string;

    @Column({ name: 'embedding_vector', type: 'bytea' })
    embeddingVector: Buffer; // 512 floats stored as binary

    @Column({ name: 'model_version', type: 'varchar', length: 50 })
    modelVersion: string;

    @Column({ name: 'quality_score', type: 'float', nullable: true })
    qualityScore: number;

    @Column({ type: 'varchar', length: 50, default: 'enrollment' })
    source: string; // enrollment, verification, etc.

    @CreateDateColumn({ name: 'created_at' })
    createdAt: Date;

    @UpdateDateColumn({ name: 'updated_at' })
    updatedAt: Date;

    @ManyToOne(() => Student, (student) => student.faceTemplates)
    @JoinColumn({ name: 'student_id' })
    student: Student;
}
