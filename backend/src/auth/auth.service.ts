import { Injectable, UnauthorizedException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import * as bcrypt from 'bcrypt';
import { Device } from '../devices/entities/device.entity';
import { Teacher } from '../teachers/entities/teacher.entity';

@Injectable()
export class AuthService {
    constructor(
        @InjectRepository(Device)
        private deviceRepository: Repository<Device>,
        @InjectRepository(Teacher)
        private teacherRepository: Repository<Teacher>,
        private jwtService: JwtService,
    ) { }

    async deviceLogin(serialNumber: string, pin: string) {
        // Find device by serial number
        const device = await this.deviceRepository.findOne({
            where: { serialNumber, status: 'active' },
        });

        if (!device) {
            throw new UnauthorizedException('Invalid device serial number');
        }

        // Find teacher by PIN (we'll search all teachers and compare hashed PINs)
        const teachers = await this.teacherRepository.find({
            where: { status: 'active', schoolId: device.schoolId },
        });

        let authenticatedTeacher: Teacher = null;

        for (const teacher of teachers) {
            const isPinValid = await bcrypt.compare(pin, teacher.pinCode);
            if (isPinValid) {
                authenticatedTeacher = teacher;
                break;
            }
        }

        if (!authenticatedTeacher) {
            throw new UnauthorizedException('Invalid PIN');
        }

        // Generate JWT token
        const payload = {
            deviceId: device.id,
            teacherId: authenticatedTeacher.id,
            schoolId: device.schoolId,
        };

        const accessToken = this.jwtService.sign(payload);

        return {
            accessToken,
            device: {
                id: device.id,
                serialNumber: device.serialNumber,
                location: device.location,
            },
            teacher: {
                id: authenticatedTeacher.id,
                firstName: authenticatedTeacher.firstName,
                lastName: authenticatedTeacher.lastName,
            },
        };
    }

    async validateToken(payload: any) {
        return {
            deviceId: payload.deviceId,
            teacherId: payload.teacherId,
            schoolId: payload.schoolId,
        };
    }
}
