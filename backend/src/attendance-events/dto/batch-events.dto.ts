import { IsString, IsNotEmpty, IsNumber, IsOptional, IsDateString, IsArray, ValidateNested } from 'class-validator';
import { Type } from 'class-transformer';

export class AttendanceEventItemDto {
    @IsString()
    @IsNotEmpty()
    studentId: string;

    @IsDateString()
    @IsNotEmpty()
    occurredAt: string;

    @IsOptional()
    @IsNumber()
    confidence?: number;

    @IsString()
    @IsNotEmpty()
    idempotencyKey: string;

    @IsOptional()
    @IsString()
    source?: 'edge' | 'manual' | 'import';
}

export class BatchEventsDto {
    @IsString()
    @IsNotEmpty()
    sessionId: string;

    @IsArray()
    @ValidateNested({ each: true })
    @Type(() => AttendanceEventItemDto)
    events: AttendanceEventItemDto[];
}
