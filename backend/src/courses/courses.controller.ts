import { Controller, Get, Param, UseGuards } from '@nestjs/common';
import { CoursesService } from './courses.service';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';

@Controller('courses')
@UseGuards(JwtAuthGuard)
export class CoursesController {
    constructor(private readonly coursesService: CoursesService) { }

    @Get(':id/roster')
    async getCourseRoster(@Param('id') id: string) {
        return this.coursesService.getCourseRoster(id);
    }
}
