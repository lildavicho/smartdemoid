import { Controller, Get, Param, UseGuards } from '@nestjs/common';
import { StudentsService } from './students.service';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';

@Controller('students')
@UseGuards(JwtAuthGuard)
export class StudentsController {
    constructor(private readonly studentsService: StudentsService) { }

    @Get(':id/face-templates')
    async getStudentFaceTemplates(@Param('id') id: string) {
        return this.studentsService.getStudentFaceTemplates(id);
    }
}
