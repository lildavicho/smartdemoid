import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AttendanceEventsController } from './attendance-events.controller';
import { AttendanceEventsService } from './attendance-events.service';
import { AttendanceEvent } from './entities/attendance-event.entity';

@Module({
    imports: [TypeOrmModule.forFeature([AttendanceEvent])],
    controllers: [AttendanceEventsController],
    providers: [AttendanceEventsService],
    exports: [AttendanceEventsService],
})
export class AttendanceEventsModule { }
