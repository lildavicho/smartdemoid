import { IsNotEmpty, IsString, MinLength } from 'class-validator';

export class LoginDto {
    @IsNotEmpty({ message: 'El número de serie es requerido' })
    @IsString({ message: 'El número de serie debe ser texto' })
    serial_number: string;

    @IsNotEmpty({ message: 'El PIN es requerido' })
    @IsString({ message: 'El PIN debe ser texto' })
    @MinLength(4, { message: 'El PIN debe tener al menos 4 caracteres' })
    pin_code: string;
}
