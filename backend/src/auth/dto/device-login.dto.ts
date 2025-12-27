import { IsString, IsNotEmpty, Length } from 'class-validator';

export class DeviceLoginDto {
    @IsString()
    @IsNotEmpty()
    serialNumber: string;

    @IsString()
    @IsNotEmpty()
    @Length(4, 10)
    pin: string;
}
