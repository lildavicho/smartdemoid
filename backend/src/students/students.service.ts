import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Student } from './entities/student.entity';
import { FaceTemplate } from '../face-templates/entities/face-template.entity';

@Injectable()
export class StudentsService {
    constructor(
        @InjectRepository(Student)
        private studentRepository: Repository<Student>,
        @InjectRepository(FaceTemplate)
        private faceTemplateRepository: Repository<FaceTemplate>,
    ) { }

    async getStudentFaceTemplates(studentId: string) {
        const student = await this.studentRepository.findOne({
            where: { id: studentId, status: 'active' },
        });

        if (!student) {
            throw new NotFoundException(`Student with ID ${studentId} not found`);
        }

        const faceTemplates = await this.faceTemplateRepository.find({
            where: { studentId },
            order: { createdAt: 'DESC' },
        });

        // Convert Buffer to Float32Array for each embedding
        const templatesWithEmbeddings = faceTemplates.map((template) => ({
            id: template.id,
            studentId: template.studentId,
            embedding: Array.from(new Float32Array(template.embeddingVector.buffer)),
            modelVersion: template.modelVersion,
            qualityScore: template.qualityScore,
            source: template.source,
            createdAt: template.createdAt,
        }));

        return {
            student: {
                id: student.id,
                documentId: student.documentId,
                firstName: student.firstName,
                lastName: student.lastName,
            },
            faceTemplates: templatesWithEmbeddings,
            totalTemplates: templatesWithEmbeddings.length,
        };
    }
}
