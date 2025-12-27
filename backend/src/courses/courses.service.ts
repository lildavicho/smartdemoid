import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, In } from 'typeorm';
import { Course } from './entities/course.entity';
import { Student } from '../students/entities/student.entity';

@Injectable()
export class CoursesService {
    constructor(
        @InjectRepository(Course)
        private courseRepository: Repository<Course>,
        @InjectRepository(Student)
        private studentRepository: Repository<Student>,
    ) { }

    async getCourseRoster(courseId: string) {
        const course = await this.courseRepository.findOne({
            where: { id: courseId },
        });

        if (!course) {
            throw new NotFoundException(`Course with ID ${courseId} not found`);
        }

        // Get all students in the course
        const students = await this.studentRepository.find({
            where: {
                id: In(course.studentIds),
                status: 'active',
            },
            select: ['id', 'documentId', 'firstName', 'lastName', 'email'],
        });

        return {
            course: {
                id: course.id,
                name: course.name,
                code: course.code,
                academicPeriod: course.academicPeriod,
            },
            students,
            totalStudents: students.length,
        };
    }
}
