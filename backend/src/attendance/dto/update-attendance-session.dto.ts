import { IsArray, IsOptional, ValidateNested, IsString, IsUUID, IsNumber, IsDateString } from 'class-validator';
import { Type } from 'class-transformer';

export class AttendanceRecordDto {
    @IsUUID()
    studentId: string;

    @IsString()
    status: string; // present, absent, pending

    @IsOptional()
    @IsNumber()
    confidence?: number;

    @IsOptional()
    @IsString()
    confirmedBy?: string;

    @IsOptional()
    @IsDateString()
    detectedAt?: string;
}

export class UpdateAttendanceSessionDto {
    @IsOptional()
    @IsArray()
    @ValidateNested({ each: true })
    @Type(() => AttendanceRecordDto)
    attendanceRecords?: AttendanceRecordDto[];

    @IsOptional()
    @IsDateString()
    endedAt?: string;

    @IsOptional()
    @IsString()
    status?: string;
}
