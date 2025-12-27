import { Injectable, CanActivate, ExecutionContext, ConflictException } from '@nestjs/common';
import { DevicesService } from '../../devices/devices.service';

@Injectable()
export class DeviceBindingGuard implements CanActivate {
    constructor(private devicesService: DevicesService) { }

    async canActivate(context: ExecutionContext): Promise<boolean> {
        const request = context.switchToHttp().getRequest();
        const teacherId = request.user?.teacherId || request.user?.sub;
        const deviceId = request.headers['x-device-id'] || request.body?.deviceId;

        if (!teacherId || !deviceId) {
            // No device binding check if missing info
            return true;
        }

        const status = await this.devicesService.checkBinding(teacherId, deviceId);

        if (status.mismatch) {
            const activeBinding = await this.devicesService.getActiveBinding(teacherId);
            throw new ConflictException({
                code: 'DEVICE_MISMATCH',
                message: 'Teacher is bound to a different device',
                boundDeviceId: activeBinding ? this.maskDeviceId(activeBinding.deviceId) : null,
            });
        }

        return true;
    }

    private maskDeviceId(deviceId: string): string {
        if (deviceId.length <= 4) return deviceId;
        return '****' + deviceId.slice(-4);
    }
}
