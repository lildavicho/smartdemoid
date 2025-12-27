import { IsUUID, IsNotEmpty, IsOptional, IsObject } from 'class-validator';

export class CreateAttendanceSessionDto {
    @IsUUID()
    @IsNotEmpty()
    courseId: string;

    @IsUUID()
    @IsOptional()
    teacherId?: string;

    @IsUUID()
    @IsOptional()
    deviceId?: string;

    @IsOptional()
    @IsObject()
    metadata?: Record<string, any>;
}
