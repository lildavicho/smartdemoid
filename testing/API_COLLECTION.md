# SmartPresence - API Collection (Postman/Insomnia)

## Environment Variables
```json
{
  "base_url": "http://localhost:3000",
  "bearer_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "teacher_id": "teacher123",
  "device_id_a": "device-android-001",
  "device_id_b": "device-android-002",
  "session_id": "session-uuid-123",
  "course_id": "course-math-101"
}
```

---

## 1. POST /api/v1/devices/bind

### Request
```http
POST {{base_url}}/api/v1/devices/bind
Authorization: Bearer {{bearer_token}}
Content-Type: application/json

{
  "teacherId": "{{teacher_id}}",
  "deviceId": "{{device_id_a}}",
  "metadata": {
    "model": "Samsung Galaxy S21",
    "manufacturer": "Samsung",
    "osVersion": "13",
    "sdkInt": "33"
  }
}
```

### Response 200 OK (First Bind)
```json
{
  "success": true,
  "message": "Device bound successfully",
  "data": {
    "id": "binding-uuid-123",
    "teacherId": "teacher123",
    "deviceId": "device-android-001",
    "boundAt": "2024-01-15T10:30:00Z",
    "lastSeenAt": "2024-01-15T10:30:00Z"
  }
}
```

### Response 200 OK (Same Device)
```json
{
  "success": true,
  "message": "Device bound successfully",
  "data": {
    "id": "binding-uuid-123",
    "teacherId": "teacher123",
    "deviceId": "device-android-001",
    "boundAt": "2024-01-15T10:30:00Z",
    "lastSeenAt": "2024-01-15T11:45:00Z"
  }
}
```

### Response 409 Conflict (Different Device)
```json
{
  "statusCode": 409,
  "message": "Teacher is already bound to a different device",
  "error": "Conflict",
  "code": "DEVICE_MISMATCH",
  "boundDeviceId": "****-001"
}
```

---

## 2. POST /api/v1/devices/rebind

### Request
```http
POST {{base_url}}/api/v1/devices/rebind
Authorization: Bearer {{bearer_token}}
Content-Type: application/json

{
  "teacherId": "{{teacher_id}}",
  "deviceId": "{{device_id_b}}",
  "adminPinProof": "admin_pin_verified_1705318200000",
  "metadata": {
    "model": "Xiaomi Redmi Note 12",
    "manufacturer": "Xiaomi",
    "osVersion": "12",
    "sdkInt": "32"
  }
}
```

### Response 200 OK
```json
{
  "success": true,
  "message": "Device rebound successfully",
  "data": {
    "id": "binding-uuid-456",
    "teacherId": "teacher123",
    "deviceId": "device-android-002",
    "boundAt": "2024-01-15T12:00:00Z"
  }
}
```

### Response 401 Unauthorized (Invalid PIN Proof)
```json
{
  "statusCode": 401,
  "message": "Invalid admin PIN proof",
  "error": "Unauthorized"
}
```

---

## 3. POST /api/v1/attendance/events/batch

### Request (3 Events Example)
```http
POST {{base_url}}/api/v1/attendance/events/batch
Authorization: Bearer {{bearer_token}}
Content-Type: application/json

{
  "sessionId": "{{session_id}}",
  "events": [
    {
      "studentId": "student001",
      "occurredAt": "2024-01-15T10:35:00Z",
      "confidence": 0.95,
      "idempotencyKey": "local-uuid-001",
      "source": "edge"
    },
    {
      "studentId": "student002",
      "occurredAt": "2024-01-15T10:36:30Z",
      "confidence": 0.92,
      "idempotencyKey": "local-uuid-002",
      "source": "edge"
    },
    {
      "studentId": "student003",
      "occurredAt": "2024-01-15T10:38:15Z",
      "confidence": 0.88,
      "idempotencyKey": "local-uuid-003",
      "source": "edge"
    }
  ]
}
```

### Response 200 OK (First Time)
```json
{
  "success": true,
  "message": "Processed 3 events: 3 inserted, 0 ignored (duplicates)",
  "data": {
    "inserted": 3,
    "ignored": 0,
    "total": 3
  }
}
```

### Response 200 OK (Retry - Idempotent)
```json
{
  "success": true,
  "message": "Processed 3 events: 0 inserted, 3 ignored (duplicates)",
  "data": {
    "inserted": 0,
    "ignored": 3,
    "total": 3
  }
}
```

### Response 409 Conflict (Device Mismatch)
```json
{
  "statusCode": 409,
  "message": "Teacher is bound to a different device",
  "error": "Conflict",
  "code": "DEVICE_MISMATCH"
}
```

---

## 4. POST /api/v1/attendance/sessions/finalize

### Request
```http
POST {{base_url}}/api/v1/attendance/sessions/finalize
Authorization: Bearer {{bearer_token}}
Content-Type: application/json

{
  "sessionId": "{{session_id}}",
  "teacherId": "{{teacher_id}}",
  "courseId": "{{course_id}}",
  "recordsJson": [
    {
      "studentId": "student001",
      "status": "present",
      "confidence": 0.95,
      "confirmedBy": "system",
      "detectedAt": "2024-01-15T10:35:00Z"
    },
    {
      "studentId": "student002",
      "status": "present",
      "confidence": 0.92,
      "confirmedBy": "system",
      "detectedAt": "2024-01-15T10:36:30Z"
    },
    {
      "studentId": "student003",
      "status": "present",
      "confidence": 0.88,
      "confirmedBy": "system",
      "detectedAt": "2024-01-15T10:38:15Z"
    }
  ],
  "idempotencyKey": "pending-session-update-uuid-001"
}
```

### Response 200 OK (First Time)
```json
{
  "success": true,
  "message": "Session finalized successfully",
  "data": {
    "success": true,
    "status": "applied",
    "finalizationId": "finalization-uuid-123"
  }
}
```

### Response 200 OK (Retry - Idempotent)
```json
{
  "success": true,
  "message": "Session already finalized",
  "data": {
    "success": true,
    "status": "already_applied",
    "finalizationId": "finalization-uuid-123"
  }
}
```

### Response 400 Bad Request (Session Not Found)
```json
{
  "success": false,
  "message": "Session finalization rejected",
  "data": {
    "success": false,
    "status": "rejected",
    "finalizationId": "finalization-uuid-456"
  }
}
```

---

## 5. GET /api/v1/devices/status

### Request
```http
GET {{base_url}}/api/v1/devices/status
Authorization: Bearer {{bearer_token}}
X-Device-Id: {{device_id_a}}
```

### Response 200 OK (Bound)
```json
{
  "success": true,
  "data": {
    "bound": true,
    "mismatch": false,
    "activeBinding": {
      "deviceId": "device-android-001",
      "boundAt": "2024-01-15T10:30:00Z",
      "lastSeenAt": "2024-01-15T11:45:00Z"
    }
  }
}
```

### Response 200 OK (Mismatch)
```json
{
  "success": true,
  "data": {
    "bound": true,
    "mismatch": true,
    "activeBinding": {
      "deviceId": "device-android-001",
      "boundAt": "2024-01-15T10:30:00Z",
      "lastSeenAt": "2024-01-15T11:45:00Z"
    }
  }
}
```

---

## Import Instructions

### Postman
1. File → Import
2. Paste JSON above or save as `.json` file
3. Create Environment with variables
4. Set `bearer_token` after login

### Insomnia
1. Application → Preferences → Data → Import Data
2. Paste JSON or import file
3. Create Environment with variables
4. Set `bearer_token` after login

---

## Testing Sequence

1. **Setup**: Login to get `bearer_token`
2. **Test 1**: Bind Device A → 200 OK
3. **Test 2**: Bind Device B → 409 DEVICE_MISMATCH
4. **Test 3**: Rebind Device B → 200 OK
5. **Test 4**: Batch Events (3 events) → 200 OK (3 inserted)
6. **Test 5**: Batch Events (same 3) → 200 OK (0 inserted, 3 ignored)
7. **Test 6**: Finalize Session → 200 OK (applied)
8. **Test 7**: Finalize Session (same) → 200 OK (already_applied)
9. **Test 8**: Check Device Status → 200 OK

---

## Expected HTTP Status Codes

| Endpoint | Success | Conflict | Unauthorized | Not Found |
|----------|---------|----------|--------------|-----------|
| /devices/bind | 200 | 409 | 401 | - |
| /devices/rebind | 200 | - | 401 | - |
| /events/batch | 200 | 409 | 401 | - |
| /sessions/finalize | 200 | - | 401 | 400 |
| /devices/status | 200 | - | 401 | - |
