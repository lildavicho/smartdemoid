import { NestFactory } from '@nestjs/core';
import { DataSource } from 'typeorm';
import * as bcrypt from 'bcrypt';
import { v4 as uuidv4 } from 'uuid';
import { AppModule } from './app.module';

async function seed() {
    const app = await NestFactory.createApplicationContext(AppModule.register());
    const dataSource = app.get(DataSource);

    if (!dataSource.isInitialized) {
        await dataSource.initialize();
    }

    console.log('Starting seed process...');

    try {
        const schoolId = uuidv4();

        console.log('Creating device...');
        const deviceResult = await dataSource.query(
            `INSERT INTO devices (id, serial_number, model, school_id, location, app_version, status, created_at, updated_at)
       VALUES ($1, $2, $3, $4, $5, $6, $7, NOW(), NOW())
       RETURNING id`,
            [
                uuidv4(),
                'DEMO-001',
                'Android Tablet',
                schoolId,
                'Classroom 101',
                '1.0.0',
                'active',
            ],
        );
        const deviceId = deviceResult[0].id as string;
        console.log(`Device created: DEMO-001 (${deviceId})`);

        console.log('Creating teacher...');
        const hashedPin = await bcrypt.hash('1234', 10);
        const teacherResult = await dataSource.query(
            `INSERT INTO teachers (id, school_id, document_id, first_name, last_name, email, pin_code, status, created_at, updated_at)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, NOW(), NOW())
       RETURNING id`,
            [
                uuidv4(),
                schoolId,
                'T-001',
                'Maria',
                'Gonzalez',
                'maria.gonzalez@school.edu',
                hashedPin,
                'active',
            ],
        );
        const teacherId = teacherResult[0].id as string;
        console.log(`Teacher created: Maria Gonzalez (PIN: 1234) (${teacherId})`);

        console.log('Creating 30 students...');
        const studentIds: string[] = [];

        const firstNames = [
            'Juan', 'Maria', 'Carlos', 'Ana', 'Luis', 'Carmen', 'Pedro', 'Laura',
            'Miguel', 'Isabel', 'Jose', 'Sofia', 'Antonio', 'Elena', 'Francisco',
            'Lucia', 'Manuel', 'Paula', 'David', 'Marta', 'Javier', 'Cristina',
            'Alejandro', 'Beatriz', 'Roberto', 'Natalia', 'Fernando', 'Andrea',
            'Diego', 'Valentina',
        ];
        const lastNames = [
            'Garcia', 'Rodriguez', 'Martinez', 'Lopez', 'Gonzalez', 'Perez',
            'Sanchez', 'Ramirez', 'Torres', 'Flores', 'Rivera', 'Gomez',
            'Diaz', 'Cruz', 'Morales', 'Reyes', 'Gutierrez', 'Ortiz',
            'Jimenez', 'Hernandez', 'Ruiz', 'Mendoza', 'Alvarez', 'Castillo',
            'Romero', 'Silva', 'Castro', 'Vargas', 'Ramos', 'Medina',
        ];

        for (let i = 0; i < 30; i++) {
            const studentId = uuidv4();
            studentIds.push(studentId);

            await dataSource.query(
                `INSERT INTO students (id, school_id, document_id, first_name, last_name, email, status, created_at, updated_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, NOW(), NOW())`,
                [
                    studentId,
                    schoolId,
                    `S-${String(i + 1).padStart(3, '0')}`,
                    firstNames[i],
                    lastNames[i],
                    `${firstNames[i].toLowerCase()}.${lastNames[i].toLowerCase()}@student.edu`,
                    'active',
                ],
            );

            const embedding = new Float32Array(512);
            for (let j = 0; j < 512; j++) {
                embedding[j] = Math.random() * 2 - 1;
            }
            const embeddingBuffer = Buffer.from(embedding.buffer);

            await dataSource.query(
                `INSERT INTO face_templates (id, student_id, embedding_vector, model_version, quality_score, source, created_at, updated_at)
         VALUES ($1, $2, $3, $4, $5, $6, NOW(), NOW())`,
                [
                    uuidv4(),
                    studentId,
                    embeddingBuffer,
                    'w600k_r50',
                    0.85 + Math.random() * 0.15,
                    'enrollment',
                ],
            );
        }

        console.log('Created 30 students with face templates');

        console.log('Creating course...');
        const courseId = uuidv4();
        await dataSource.query(
            `INSERT INTO courses (id, school_id, name, code, academic_period, student_ids, created_at, updated_at)
       VALUES ($1, $2, $3, $4, $5, $6, NOW(), NOW())`,
            [
                courseId,
                schoolId,
                'Matematicas 10A',
                'MAT-10A',
                '2024-1',
                JSON.stringify(studentIds),
            ],
        );
        console.log(`Course created: Matematicas 10A (${courseId})`);

        console.log('\nSeed completed successfully!');
        console.log('\nSummary:');
        console.log(`   School ID: ${schoolId}`);
        console.log(`   Device: DEMO-001 (${deviceId})`);
        console.log(`   Teacher: Maria Gonzalez (${teacherId})`);
        console.log(`   Course: Matematicas 10A (${studentIds.length} students)`);
        console.log('\nLogin credentials:');
        console.log('   Serial Number: DEMO-001');
        console.log('   PIN: 1234');
    } catch (error) {
        console.error('Seed failed:', error);
        throw error;
    } finally {
        await app.close();
    }
}

void seed();
