# SmartPresence - Arquitectura del Sistema

## Stack Tecnológico

### Backend
- NestJS 10.x + TypeScript 5.x
- TypeORM 0.3.x
- PostgreSQL 15 (Supabase)
- Redis (Upstash) + Bull queues
- JWT Authentication
- Package Manager: PNPM

### Android
- Kotlin 1.9.x
- Jetpack Compose + Material 3
- MVVM Architecture
- Min SDK: 26, Target SDK: 34
- CameraX 1.3
- ONNX Runtime 1.16
- Room Database
- Retrofit + Moshi
- Hilt (DI)
- Coroutines + Flow

### Modelos IA
- SCRFD (Detection): scrfd_10g_bnkps.onnx (16MB)
- ArcFace (Recognition): w600k_r50.onnx (166MB)
- Ubicación: C:\SmartPresence\models\

## Entidades Base de Datos

1. students (id, school_id, document_id, first_name, last_name, email, external_ids, status)
2. courses (id, school_id, name, code, academic_period, external_ids)
3. teachers (id, school_id, document_id, first_name, last_name, email, pin_code)
4. devices (id, serial_number, model, school_id, location, app_version, status)
5. face_templates (id, student_id, embedding_vector, model_version, quality_score, source)
6. attendance_sessions (id, course_id, teacher_id, device_id, started_at, ended_at, status)
7. attendance_records (id, session_id, student_id, status, confidence, confirmed_by)

## Endpoints Requeridos

### Auth
- POST /api/v1/auth/device/login

### Courses
- GET /api/v1/courses/:id/roster

### Students
- GET /api/v1/students/:id/face-templates

### Attendance
- POST /api/v1/attendance/sessions
- PUT /api/v1/attendance/sessions/:id
- GET /api/v1/attendance/sessions/:id

## Reglas de Negocio

1. Auth: Device serial + Teacher PIN → JWT token
2. Face templates: Embeddings guardados como Buffer (bytea)
3. Attendance: Status puede ser 'present', 'absent', 'pending'
4. Sync: WorkManager offline-first en Android
```

---

## PASO 3: PROMPTS EXACTOS PARA CADA IA

### 🎯 **PROMPT 1: PARA CLAUDE SONNET 4.5 (Backend NestJS)**

**DÓNDE EJECUTARLO:** Claude.ai o Claude Desktop

**PROMPT EXACTO:**
```
Eres un desarrollador NestJS experto. Necesito que generes el código completo del backend para SmartPresence.

CONTEXTO:
- Sistema de asistencia facial automática para instituciones educativas
- Stack: NestJS 10 + TypeScript + TypeORM + PostgreSQL (Supabase)
- NO usar Docker
- Package manager: PNPM

UBICACIÓN DEL PROYECTO:
C:\SmartPresence\backend

ARCHIVOS YA EXISTENTES:
- .env (configurado con Supabase y Upstash)
- ARCHITECTURE.md (en C:\SmartPresence\)

TAREAS ESPECÍFICAS:

1. GENERAR package.json completo con todas las dependencias necesarias:
   - @nestjs/core, @nestjs/common, @nestjs/platform-express
   - @nestjs/typeorm, typeorm, pg
   - @nestjs/config
   - @nestjs/jwt, @nestjs/passport, passport, passport-jwt
   - @nestjs/bull, bull, ioredis
   - class-validator, class-transformer
   - bcrypt, uuid

2. CREAR tsconfig.json optimizado para NestJS

3. GENERAR src/config/database.config.ts que:
   - Use ConfigService
   - Conecte a Supabase con SSL
   - synchronize: true en development

4. CREAR todas las entidades TypeORM:
   - src/students/entities/student.entity.ts
   - src/courses/entities/course.entity.ts
   - src/teachers/entities/teacher.entity.ts
   - src/devices/entities/device.entity.ts
   - src/face-templates/entities/face-template.entity.ts
   - src/attendance/entities/attendance-session.entity.ts
   - src/attendance/entities/attendance-record.entity.ts

5. IMPLEMENTAR autenticación JWT completa:
   - src/auth/auth.service.ts (login con device serial + teacher PIN)
   - src/auth/auth.controller.ts
   - src/auth/strategies/jwt.strategy.ts
   - src/auth/guards/jwt-auth.guard.ts
   - src/auth/auth.module.ts

6. CREAR endpoints de Attendance:
   - POST /api/v1/attendance/sessions (crear sesión)
   - PUT /api/v1/attendance/sessions/:id (completar con records)
   - GET /api/v1/attendance/sessions/:id (obtener sesión)

7. CREAR endpoint de Courses:
   - GET /api/v1/courses/:id/roster (estudiantes del curso)

8. CREAR endpoint de Students:
   - GET /api/v1/students/:id/face-templates (embeddings del estudiante)

9. GENERAR src/main.ts con:
   - CORS habilitado
   - Global prefix: api/v1
   - ValidationPipe global
   - Puerto desde .env

10. CREAR script de seed (src/seed.ts):
    - 1 device (serial: DEMO-001)
    - 1 teacher (PIN: 1234, hasheado con bcrypt)
    - 1 curso (Matemáticas 10A)
    - 30 estudiantes con nombres aleatorios
    - Face templates fake (embeddings random de 512 floats)

RESTRICCIONES:
- NO usar Prisma, solo TypeORM
- NO usar Docker
- Código debe compilar en Windows
- Usar decoradores de class-validator para DTOs
- Todos los IDs son UUID
- Timestamps con CreateDateColumn y UpdateDateColumn

FORMATO DE SALIDA:
Genera cada archivo completo, uno por uno, con la ruta exacta donde debe ir.

Empieza por package.json.
```

---

### 🎯 **PROMPT 2: PARA GEMINI 2.0 FLASH THINKING (Verificación + Optimización)**

**DÓNDE EJECUTARLO:** Google AI Studio con Gemini 2.0 Flash Thinking

**PROMPT EXACTO:**
```
Eres un experto en arquitectura de software y NestJS. Revisa el código generado por Claude para el backend de SmartPresence.

TAREA:
1. Analiza cada archivo generado
2. Verifica que no haya errores de TypeScript
3. Verifica que las relaciones TypeORM sean correctas
4. Verifica que los DTOs tengan validaciones
5. Identifica optimizaciones de performance
6. Identifica problemas de seguridad

CONTEXTO DEL PROYECTO:
- Backend NestJS + TypeORM + PostgreSQL (Supabase)
- Sistema de asistencia facial automática
- Debe soportar hasta 100 requests/segundo
- Datos sensibles: face embeddings, información de estudiantes

ENTREGABLES:
1. Lista de errores encontrados (si hay)
2. Lista de mejoras sugeridas
3. Código corregido para los archivos con errores
4. Recomendaciones de índices de base de datos

ARCHIVOS A REVISAR:
[Aquí copiarás el código que Claude generó]

Enfócate en:
- Type safety
- Error handling
- Performance
- Security (SQL injection, XSS, etc)
- Best practices NestJS
```

---

### 🎯 **PROMPT 3: PARA GEMINI 1.5 PRO (Android App Base)**

**DÓNDE EJECUTARLO:** Google AI Studio (después de tener backend listo)

**PROMPT EXACTO:**
```
Eres un desarrollador Android experto en Kotlin y Jetpack Compose. Genera la estructura completa de la app Android para SmartPresence.

CONTEXTO:
- App para pizarras interactivas Android
- Asistencia facial automática
- Offline-first con sincronización
- Stack: Kotlin + Compose + MVVM + Hilt + Room + Retrofit + CameraX + ONNX Runtime

UBICACIÓN:
C:\SmartPresence\android

ARCHIVOS DE MODELOS IA:
C:\SmartPresence\models\scrfd_10g_bnkps.onnx
C:\SmartPresence\models\w600k_r50.onnx

TAREAS:

1. GENERAR build.gradle.kts (project level) con:
   - Kotlin 1.9.21
   - AGP 8.2.1
   - Hilt 2.50

2. GENERAR build.gradle.kts (app level) con dependencias:
   - Compose BOM 2023.10.01
   - Material 3
   - CameraX 1.3.1
   - ONNX Runtime 1.16.0
   - Room 2.6.1
   - Retrofit 2.9.0 + Moshi
   - Hilt
   - WorkManager
   - Coroutines + Flow

3. CREAR estructura de paquetes Clean Architecture:
   - com.smartpresence.idukay.data
   - com.smartpresence.idukay.domain
   - com.smartpresence.idukay.presentation
   - com.smartpresence.idukay.ai
   - com.smartpresence.idukay.di

4. IMPLEMENTAR Room Database:
   - Entities: StudentEntity, CourseEntity, AttendanceSessionEntity, FaceTemplateEntity
   - DAOs correspondientes
   - AppDatabase.kt

5. IMPLEMENTAR Retrofit API:
   - SmartPresenceApi.kt con endpoints:
     * POST /api/v1/auth/device/login
     * GET /api/v1/courses/{id}/roster
     * POST /api/v1/attendance/sessions
     * PUT /api/v1/attendance/sessions/{id}
   - AuthInterceptor.kt (inyecta JWT token)
   - DTOs (LoginRequest, LoginResponse, etc)

6. CREAR UI básica con Compose:
   - LoginScreen.kt (serial number + PIN input)
   - CourseSelectionScreen.kt (lista de cursos)
   - AttendanceScreen.kt (con preview de cámara)
   - Navigation con NavHost

7. IMPLEMENTAR Hilt modules:
   - NetworkModule (Retrofit)
   - DatabaseModule (Room)
   - AIModule (ONNX Runtime - solo estructura, sin implementación aún)

8. CONFIGURAR AndroidManifest.xml:
   - Permissions: CAMERA, INTERNET
   - Application class con @HiltAndroidApp
   - Activities

9. CREAR SmartPresenceApp.kt (Application class)

10. GENERAR strings.xml, colors.xml, themes.xml

RESTRICCIONES:
- Min SDK: 26 (Android 8.0)
- Target SDK: 34 (Android 14)
- SOLO Jetpack Compose (NO XML layouts)
- Orientation: Landscape (para pizarras)
- UI simple pero profesional (Material 3)
- NO implementar lógica IA aún (solo estructura)

FORMATO:
Genera cada archivo con ruta completa en orden de creación.

Empieza por settings.gradle.kts.
```

---

### 🎯 **PROMPT 4: PARA CODEX/GPT-4 (Pipeline IA Android)**

**DÓNDE EJECUTARLO:** ChatGPT Plus o GitHub Copilot Chat (después de tener Android base)

**PROMPT EXACTO:**
```
Eres experto en IA y ONNX Runtime en Android. Implementa el pipeline completo de reconocimiento facial para SmartPresence.

CONTEXTO:
- App Android Kotlin existente
- Modelos ONNX: SCRFD (detection) + ArcFace (recognition)
- Ubicación modelos: app/src/main/assets/models/

TAREAS:

1. IMPLEMENTAR SCRFDDetector.kt:
   - Cargar modelo scrfd_10g_bnkps.onnx con OrtSession
   - Input: Bitmap (cualquier tamaño)
   - Preprocessing: Resize a 640x640, normalize [-1,1], CHW format
   - Inference con ONNX Runtime
   - Output: List<Detection> con bbox, confidence, keypoints
   - NMS con IoU threshold 0.4

2. IMPLEMENTAR ArcFaceRecognizer.kt:
   - Cargar modelo w600k_r50.onnx
   - Input: Bitmap aligned face 112x112
   - Preprocessing: Normalize, CHW format
   - Output: FloatArray de 512 dimensiones (embedding)
   - Función cosineSimilarity(emb1, emb2): Float

3. IMPLEMENTAR FaceTracker.kt:
   - Tracking basado en IoU
   - Mantener IDs consistentes entre frames
   - Gestionar tracks desaparecidos (max 10 frames)
   - update(detections): List<TrackedDetection>

4. IMPLEMENTAR TemporalVoter.kt:
   - Acumular votos de reconocimiento por track_id
   - Ventana de 5 frames
   - Requerir 3 frames consecutivos para confirmar
   - getConsensus(trackId): Pair<studentId, confidence>?

5. IMPLEMENTAR QualityFilter.kt:
   - Filtrar caras muy pequeñas (<80px)
   - Filtrar caras borrosas (Laplacian variance)
   - Filtrar ángulos extremos (yaw, pitch, roll)
   - assessQuality(bitmap, keypoints): QualityScore

6. CREAR RecognitionPipeline.kt que integre todo:
   - processFrame(bitmap, rosterEmbeddings, frameNum): FrameResult
   - Pipeline: Detect → Track → Filter → Align → Recognize → Match → Vote
   - Thresholds: similarity > 0.60, margin > 0.15

PARÁMETROS EXACTOS:
```kotlin
class RecognitionConfig {
    companion object {
        const val DETECTION_THRESHOLD = 0.5f
        const val MIN_FACE_SIZE = 80 // pixels
        const val SIMILARITY_THRESHOLD = 0.60f
        const val MARGIN_THRESHOLD = 0.15f
        const val MIN_CONSECUTIVE_FRAMES = 3
        const val VOTING_WINDOW = 5
        const val IOU_THRESHOLD = 0.4f
    }
}
```

REQUISITOS:
- Todo en Kotlin idiomático
- Usar Coroutines (suspend functions)
- Ejecutar en Dispatchers.Default
- Timber para logging
- Error handling robusto
- Memory efficient (no leaks)

ENTREGABLES:
Código completo de cada clase, listo para copiar/pegar.