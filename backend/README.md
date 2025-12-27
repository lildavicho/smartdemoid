# SmartPresence Backend

Backend API for SmartPresence - Facial Recognition Attendance System

## Tech Stack

- **Framework**: NestJS 10.x
- **Language**: TypeScript 5.x
- **Database**: PostgreSQL 15 (Supabase)
- **ORM**: TypeORM 0.3.x
- **Authentication**: JWT (Passport)
- **Queue**: Bull + Redis (Upstash)
- **Package Manager**: PNPM

## Prerequisites

- Node.js 18+ 
- PNPM 8+
- PostgreSQL (Supabase account)
- Redis (Upstash account)

## Installation

```bash
# Install dependencies
pnpm install
```

## Configuration

The `.env` file is located in the parent directory (`../env`). It should contain:

```env
# Database (Supabase)
DB_HOST=your-supabase-host
DB_PORT=5432
DB_USERNAME=postgres
DB_PASSWORD=your-password
DB_DATABASE=postgres

# JWT
JWT_SECRET=your-secret-key
JWT_EXPIRES_IN=1h

# App
NODE_ENV=development
PORT=3000
API_PREFIX=api/v1
```

## Running the Application

```bash
# Development mode
pnpm start:dev

# Production mode
pnpm build
pnpm start:prod
```

## Database Setup

```bash
# Run seed script to populate demo data
pnpm seed
```

This will create:
- 1 Device (Serial: `DEMO-001`)
- 1 Teacher (PIN: `1234`)
- 1 Course (Matemáticas 10A)
- 30 Students with face templates

## API Endpoints

### Authentication
- `POST /api/v1/auth/device/login` - Device + Teacher PIN login

### Courses
- `GET /api/v1/courses/:id/roster` - Get course roster with students

### Students
- `GET /api/v1/students/:id/face-templates` - Get student face embeddings

### Attendance
- `POST /api/v1/attendance/sessions` - Create attendance session
- `PUT /api/v1/attendance/sessions/:id` - Update session with records
- `GET /api/v1/attendance/sessions/:id` - Get session details

## Testing Login

```bash
curl -X POST http://localhost:3000/api/v1/auth/device/login \
  -H "Content-Type: application/json" \
  -d '{
    "serialNumber": "DEMO-001",
    "pin": "1234"
  }'
```

## Project Structure

```
src/
├── auth/                 # Authentication module
├── attendance/           # Attendance sessions & records
├── courses/              # Course management
├── students/             # Student management
├── face-templates/       # Face embeddings
├── devices/              # Device entities
├── teachers/             # Teacher entities
├── config/               # Configuration files
├── app.module.ts         # Root module
├── main.ts               # Application entry point
└── seed.ts               # Database seeding script
```

## License

MIT
