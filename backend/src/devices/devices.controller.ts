import { Controller, Post, Get, Body, Param, UseGuards } from '@nestjs/common';
import { DevicesService } from './devices.service';
import { BindDeviceDto } from './dto/bind-device.dto';
import { RebindDeviceDto } from './dto/rebind-device.dto';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';

@Controller('devices')
export class DevicesController {
    constructor(private readonly devicesService: DevicesService) { }

    @Post('bind')
    @UseGuards(JwtAuthGuard)
    async bindDevice(@Body() dto: BindDeviceDto) {
        const binding = await this.devicesService.bindDevice(dto);

        return {
            success: true,
            message: 'Device bound successfully',
            data: binding,
        };
    }

    @Post('rebind')
    @UseGuards(JwtAuthGuard)
    async rebindDevice(@Body() dto: RebindDeviceDto) {
        const binding = await this.devicesService.rebindDevice(dto);

        return {
            success: true,
            message: 'Device rebound successfully',
            data: binding,
        };
    }

    @Get('check/:teacherId/:deviceId')
    @UseGuards(JwtAuthGuard)
    async checkBinding(
        @Param('teacherId') teacherId: string,
        @Param('deviceId') deviceId: string,
    ) {
        const result = await this.devicesService.checkBinding(teacherId, deviceId);

        return {
            success: true,
            data: result,
        };
    }

    @Get('active/:teacherId')
    @UseGuards(JwtAuthGuard)
    async getActiveBinding(@Param('teacherId') teacherId: string) {
        const binding = await this.devicesService.getActiveBinding(teacherId);

        return {
            success: true,
            data: binding,
        };
    }
}
