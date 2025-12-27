import { IsString, IsNotEmpty, IsArray } from 'class-validator';

export class FinalizeSessionDto {
    @IsString()
    @IsNotEmpty()
    sessionId: string;

    @IsString()
    @IsNotEmpty()
    teacherId: string;

    @IsString()
    @IsNotEmpty()
    courseId: string;

    @IsArray()
    @IsNotEmpty()
    recordsJson: any[];

    @IsString()
    @IsNotEmpty()
    idempotencyKey: string;
}
