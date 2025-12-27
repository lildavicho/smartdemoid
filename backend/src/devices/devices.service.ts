import { Injectable, ConflictException, UnauthorizedException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { DeviceBinding } from './entities/device-binding.entity';
import { BindDeviceDto } from './dto/bind-device.dto';
import { RebindDeviceDto } from './dto/rebind-device.dto';

@Injectable()
export class DevicesService {
    constructor(
        @InjectRepository(DeviceBinding)
        private deviceBindingRepository: Repository<DeviceBinding>,
    ) { }

    /**
     * Bind a device to a teacher
     * - If no active binding exists, create one
     * - If active binding exists with same deviceId, update last_seen_at
     * - If active binding exists with different deviceId, throw 409 DEVICE_MISMATCH
     */
    async bindDevice(dto: BindDeviceDto): Promise<DeviceBinding> {
        // Check for existing active binding
        const existingBinding = await this.deviceBindingRepository.findOne({
            where: { teacherId: dto.teacherId, status: 'active' },
        });

        if (existingBinding) {
            if (existingBinding.deviceId === dto.deviceId) {
                // Same device, update last_seen_at
                existingBinding.lastSeenAt = new Date();
                if (dto.metadata) {
                    existingBinding.metadata = { ...existingBinding.metadata, ...dto.metadata };
                }
                return await this.deviceBindingRepository.save(existingBinding);
            } else {
                // Different device, throw conflict
                throw new ConflictException({
                    code: 'DEVICE_MISMATCH',
                    message: 'Teacher is already bound to a different device',
                    boundDeviceId: this.maskDeviceId(existingBinding.deviceId),
                });
            }
        }

        // No active binding, create new one
        const newBinding = this.deviceBindingRepository.create({
            teacherId: dto.teacherId,
            deviceId: dto.deviceId,
            status: 'active',
            boundAt: new Date(),
            lastSeenAt: new Date(),
            metadata: dto.metadata || {},
        });

        return await this.deviceBindingRepository.save(newBinding);
    }

    /**
     * Rebind a device with admin PIN proof
     * - Verify admin PIN proof (simple validation for now)
     * - Revoke existing active binding
     * - Create new active binding
     */
    async rebindDevice(dto: RebindDeviceDto): Promise<DeviceBinding> {
        // Verify admin PIN proof
        // For now, we do a simple check. In production, verify JWT or signature
        if (!this.verifyAdminPinProof(dto.adminPinProof)) {
            throw new UnauthorizedException('Invalid admin PIN proof');
        }

        // Find and revoke existing active binding
        const existingBinding = await this.deviceBindingRepository.findOne({
            where: { teacherId: dto.teacherId, status: 'active' },
        });

        if (existingBinding) {
            existingBinding.status = 'revoked';
            existingBinding.revokedAt = new Date();
            await this.deviceBindingRepository.save(existingBinding);
        }

        // Create new active binding
        const newBinding = this.deviceBindingRepository.create({
            teacherId: dto.teacherId,
            deviceId: dto.deviceId,
            status: 'active',
            boundAt: new Date(),
            lastSeenAt: new Date(),
            metadata: dto.metadata || {},
        });

        return await this.deviceBindingRepository.save(newBinding);
    }

    /**
     * Check device binding status
     */
    async checkBinding(teacherId: string, deviceId: string): Promise<{ bound: boolean; mismatch: boolean }> {
        const existingBinding = await this.deviceBindingRepository.findOne({
            where: { teacherId, status: 'active' },
        });

        if (!existingBinding) {
            return { bound: false, mismatch: false };
        }

        if (existingBinding.deviceId === deviceId) {
            return { bound: true, mismatch: false };
        }

        return { bound: true, mismatch: true };
    }

    /**
     * Get active binding for teacher
     */
    async getActiveBinding(teacherId: string): Promise<DeviceBinding | null> {
        return await this.deviceBindingRepository.findOne({
            where: { teacherId, status: 'active' },
        });
    }

    /**
     * Verify admin PIN proof
     * TODO: Implement proper JWT/signature verification
     */
    private verifyAdminPinProof(proof: string): boolean {
        // For now, accept any non-empty proof
        // In production, verify JWT signed by Android with admin PIN hash
        return proof.trim().length > 0;
    }

    /**
     * Mask device ID for security (show last 4 chars)
     */
    private maskDeviceId(deviceId: string): string {
        if (deviceId.length <= 4) return deviceId;
        return '****' + deviceId.slice(-4);
    }
}
