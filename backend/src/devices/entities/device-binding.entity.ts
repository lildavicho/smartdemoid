import { Column, Entity, PrimaryGeneratedColumn, CreateDateColumn, UpdateDateColumn } from 'typeorm';

@Entity('device_bindings')
export class DeviceBinding {
    @PrimaryGeneratedColumn('uuid')
    id: string;

    @Column({ type: 'text', name: 'teacher_id' })
    teacherId: string;

    @Column({ type: 'text', name: 'device_id' })
    deviceId: string;

    @Column({ type: 'text', default: 'active' })
    status: 'active' | 'revoked';

    @Column({ type: 'timestamptz', name: 'bound_at', default: () => 'CURRENT_TIMESTAMP' })
    boundAt: Date;

    @Column({ type: 'timestamptz', name: 'last_seen_at', default: () => 'CURRENT_TIMESTAMP' })
    lastSeenAt: Date;

    @Column({ type: 'timestamptz', name: 'revoked_at', nullable: true })
    revokedAt: Date | null;

    @Column({ type: 'jsonb', default: {} })
    metadata: Record<string, any>;

    @CreateDateColumn({ name: 'created_at' })
    createdAt: Date;

    @UpdateDateColumn({ name: 'updated_at' })
    updatedAt: Date;
}
