import { IsString, IsNotEmpty, IsOptional, IsObject } from 'class-validator';

export class BindDeviceDto {
    @IsString()
    @IsNotEmpty()
    teacherId: string;

    @IsString()
    @IsNotEmpty()
    deviceId: string;

    @IsOptional()
    @IsObject()
    metadata?: Record<string, any>;
}
