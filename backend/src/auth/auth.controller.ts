import { Controller, Post, Body } from '@nestjs/common';
import { AuthService } from './auth.service';
import { LoginDto } from './dto/login.dto';

@Controller('auth')
export class AuthController {
    constructor(private readonly authService: AuthService) { }

    @Post('device/login')
    async login(@Body() loginDto: LoginDto) {
        return this.authService.deviceLogin(
            loginDto.serial_number,
            loginDto.pin_code,
        );
    }
}
