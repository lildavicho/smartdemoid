import { IsString, IsNotEmpty, IsOptional, IsObject } from 'class-validator';

export class RebindDeviceDto {
    @IsString()
    @IsNotEmpty()
    teacherId: string;

    @IsString()
    @IsNotEmpty()
    deviceId: string;

    @IsString()
    @IsNotEmpty()
    adminPinProof: string;

    @IsOptional()
    @IsObject()
    metadata?: Record<string, any>;
}
