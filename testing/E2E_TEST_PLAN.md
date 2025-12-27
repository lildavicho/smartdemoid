# SmartPresence - E2E Test Plan

## Test Case 1: Device Binding - First Login
**Objetivo**: Verificar que el primer login en un dispositivo crea el binding correctamente

**Precondiciones**:
- Teacher no tiene binding activo
- App instalada en Device A

**Pasos**:
1. Abrir app en Device A
2. Login con credenciales válidas (serialNumber + PIN)
3. Esperar respuesta de login

**Resultado Esperado**:
- ✅ Login exitoso
- ✅ Backend crea registro en `device_bindings` con status='active'
- ✅ App navega a pantalla principal
- ✅ Logs muestran: "Device bound successfully"

**Verificación SQL**:
```sql
SELECT * FROM device_bindings 
WHERE teacher_id = 'TEACHER_ID' AND status = 'active';
```

---

## Test Case 2: Device Mismatch - Login en Otro Dispositivo
**Objetivo**: Verificar que login en dispositivo diferente retorna 409 y bloquea acceso

**Precondiciones**:
- Teacher ya tiene binding activo en Device A
- App instalada en Device B

**Pasos**:
1. Abrir app en Device B
2. Login con mismas credenciales del teacher
3. Observar respuesta del backend

**Resultado Esperado**:
- ✅ Backend retorna 409 DEVICE_MISMATCH
- ✅ App muestra `DeviceNotAuthorizedScreen`
- ✅ Pantalla muestra device IDs enmascarados (****1234)
- ✅ Botón "Rebind with Admin PIN" visible
- ✅ Logs muestran: "Device mismatch: ..."

**Verificación SQL**:
```sql
SELECT device_id, status, bound_at 
FROM device_bindings 
WHERE teacher_id = 'TEACHER_ID' AND status = 'active';
-- Debe mostrar solo Device A
```

---

## Test Case 3: Device Rebind con Admin PIN
**Objetivo**: Verificar que rebind con PIN correcto permite acceso en nuevo dispositivo

**Precondiciones**:
- Test Case 2 completado (en DeviceNotAuthorizedScreen)
- Admin PIN configurado previamente

**Pasos**:
1. Click "Rebind with Admin PIN"
2. Ingresar PIN admin correcto en dialog
3. Esperar respuesta

**Resultado Esperado**:
- ✅ Backend revoca binding de Device A (status='revoked')
- ✅ Backend crea nuevo binding para Device B (status='active')
- ✅ App navega a pantalla principal
- ✅ Logs muestran: "Device rebound successfully"

**Verificación SQL**:
```sql
SELECT device_id, status, bound_at, revoked_at 
FROM device_bindings 
WHERE teacher_id = 'TEACHER_ID' 
ORDER BY bound_at DESC LIMIT 2;
-- Debe mostrar Device A revoked y Device B active
```

---

## Test Case 4: Batch Events - Offline Sync con Idempotencia
**Objetivo**: Verificar que confirmaciones offline se sincronizan correctamente

**Precondiciones**:
- Sesión activa
- Modo offline (sin red)

**Pasos**:
1. Confirmar 5 estudiantes manualmente
2. Verificar que se crean 5 `PendingAttendanceEntity` con localId único
3. Activar red
4. Esperar sync automático (o trigger manual)
5. Observar logs del worker

**Resultado Esperado**:
- ✅ Worker agrupa por sessionId
- ✅ Llama POST /api/v1/attendance/events/batch con 5 eventos
- ✅ Backend inserta 5 registros en `attendance_events`
- ✅ Response: {inserted: 5, ignored: 0, total: 5}
- ✅ Worker marca los 5 como SENT en local
- ✅ Logs muestran: "Batch events for session X: 5 inserted, 0 ignored"

**Verificación SQL**:
```sql
SELECT COUNT(*) FROM attendance_events 
WHERE session_id = 'SESSION_ID';
-- Debe ser 5

SELECT idempotency_key FROM attendance_events 
WHERE session_id = 'SESSION_ID';
-- Debe mostrar 5 localIds únicos
```

---

## Test Case 5: Session Finalization - Offline con Idempotencia
**Objetivo**: Verificar que finalización offline se sincroniza correctamente

**Precondiciones**:
- Sesión activa con 3 estudiantes confirmados
- Modo offline

**Pasos**:
1. Click "Finalizar Sesión"
2. Verificar creación de `PendingSessionUpdateEntity` con courseId
3. Activar red
4. Esperar sync
5. Observar logs

**Resultado Esperado**:
- ✅ Se crea `PendingSessionUpdateEntity` con courseId no vacío
- ✅ Worker llama POST /api/v1/attendance/sessions/finalize
- ✅ Backend crea registro en `session_finalizations` con status='applied'
- ✅ Response: {success: true, status: "applied"}
- ✅ Worker marca como SENT
- ✅ Logs muestran: "Session X finalized: applied"

**Verificación SQL**:
```sql
SELECT session_id, course_id, status, applied_at 
FROM session_finalizations 
WHERE session_id = 'SESSION_ID';
-- Debe mostrar status='applied' y applied_at no null
```

---

## Test Case 6: Idempotencia Batch Events - No Duplicados
**Objetivo**: Verificar que enviar mismo batch 2 veces no crea duplicados

**Precondiciones**:
- Test Case 4 completado

**Pasos**:
1. Forzar re-sync del mismo batch (simular retry)
2. Observar response del backend
3. Verificar DB

**Resultado Esperado**:
- ✅ Backend detecta idempotencyKey duplicados
- ✅ Response: {inserted: 0, ignored: 5, total: 5}
- ✅ DB sigue teniendo solo 5 registros (no 10)
- ✅ Logs muestran: "0 inserted, 5 ignored (duplicates)"

**Verificación SQL**:
```sql
SELECT COUNT(*) FROM attendance_events 
WHERE session_id = 'SESSION_ID';
-- Debe seguir siendo 5, no 10

SELECT idempotency_key, COUNT(*) 
FROM attendance_events 
WHERE session_id = 'SESSION_ID' 
GROUP BY idempotency_key 
HAVING COUNT(*) > 1;
-- Debe retornar 0 filas (no duplicados)
```

---

## Test Case 7: Idempotencia Session Finalization - No Duplicados
**Objetivo**: Verificar que finalizar 2 veces no crea duplicados

**Precondiciones**:
- Test Case 5 completado

**Pasos**:
1. Forzar re-sync de finalization
2. Observar response
3. Verificar DB

**Resultado Esperado**:
- ✅ Backend detecta idempotencyKey duplicado
- ✅ Response: {success: true, status: "already_applied"}
- ✅ DB sigue teniendo solo 1 registro de finalization
- ✅ Logs muestran: "Session X finalized: already_applied"

**Verificación SQL**:
```sql
SELECT COUNT(*) FROM session_finalizations 
WHERE session_id = 'SESSION_ID';
-- Debe ser 1, no 2

SELECT idempotency_key, COUNT(*) 
FROM session_finalizations 
WHERE session_id = 'SESSION_ID' 
GROUP BY idempotency_key 
HAVING COUNT(*) > 1;
-- Debe retornar 0 filas
```

---

## Test Case 8: switchCourse - No Mezcla de Datos
**Objetivo**: Verificar que cambiar curso limpia estado correctamente

**Precondiciones**:
- Sesión activa en Curso A
- 2 estudiantes confirmados del Curso A

**Pasos**:
1. Observar confirmedRecords (debe tener 2 estudiantes de Curso A)
2. Click "Cambiar Curso" → Seleccionar Curso B
3. Observar UI y logs
4. Confirmar 1 estudiante del Curso B
5. Verificar lista de confirmados

**Resultado Esperado**:
- ✅ Logs muestran: "Switching course: cursoA -> cursoB"
- ✅ Logs muestran: "Course switched successfully: X templates loaded for course cursoB"
- ✅ UI muestra confirmedRecords vacío después del switch
- ✅ recognizedStudents vacío
- ✅ overlayFaces vacío
- ✅ Después de confirmar en Curso B, solo muestra 1 estudiante (del Curso B)
- ✅ NO muestra los 2 estudiantes del Curso A

---

## Test Case 9: Token Expirado - Worker Resiliente
**Objetivo**: Verificar que worker no crashea con token expirado

**Precondiciones**:
- Pending records en cola
- Token JWT expirado o inválido

**Pasos**:
1. Simular token expirado (modificar en DataStore o esperar expiración)
2. Trigger sync worker
3. Observar logs y comportamiento

**Resultado Esperado**:
- ✅ Worker recibe 401/403 del backend
- ✅ Worker NO crashea
- ✅ Worker marca registros como FAILED (retry < 3)
- ✅ SyncHealthDataStore registra error
- ✅ Logs muestran: "HTTP error syncing session X"
- ✅ App sigue funcionando, no force close

---

## Test Case 10: Diagnostics Export - JSON Completo
**Objetivo**: Verificar que export de diagnósticos contiene toda la info

**Precondiciones**:
- App en uso con datos

**Pasos**:
1. Navegar a DiagnosticsScreen
2. Click "Export Diagnostics"
3. Seleccionar "Anonymize" o no
4. Observar JSON exportado

**Resultado Esperado**:
- ✅ JSON contiene:
  - deviceInfo (model, manufacturer, osVersion)
  - aiConfig (detector, recognizer, threshold)
  - syncStatus (lastSuccess, failureCount, pendingCount)
  - deviceBinding (boundDeviceId enmascarado si anonymize)
  - modelLoadingErrors (si existen)
- ✅ Si anonymize=true: teacherId y deviceId enmascarados (****1234)
- ✅ Si anonymize=false: IDs completos
- ✅ JSON válido y parseable

---

## Test Case 11: Kiosk Mode - Bloqueo de Navegación
**Objetivo**: Verificar que kiosk mode bloquea back/logout sin PIN

**Precondiciones**:
- Kiosk mode habilitado en Diagnostics
- Admin PIN configurado
- Sesión activa

**Pasos**:
1. Presionar botón back del dispositivo
2. Observar comportamiento
3. Ingresar PIN admin correcto
4. Observar comportamiento

**Resultado Esperado**:
- ✅ Back button bloqueado, muestra AdminPinDialog
- ✅ Dialog muestra: "Enter Admin PIN to exit session"
- ✅ Intentos incorrectos muestran contador
- ✅ 5 intentos fallidos → lockout 5 minutos
- ✅ PIN correcto → permite salir de sesión
- ✅ Logs muestran: "Kiosk mode exit with PIN"

---

## Test Case 12: Model Loading Error - App Resiliente
**Objetivo**: Verificar que app no crashea si modelos ONNX fallan

**Precondiciones**:
- Modelos ONNX corruptos o faltantes (simular)

**Pasos**:
1. Abrir app
2. Navegar a AttendanceScreen
3. Observar UI y logs

**Resultado Esperado**:
- ✅ App NO crashea
- ✅ Banner de error visible: "Model loading failed: ..."
- ✅ Botón "Start Session" deshabilitado
- ✅ Logs muestran: "Model loading error: ..."
- ✅ DiagnosticsScreen muestra error en "Model Loading Errors"
- ✅ Usuario puede navegar a otras pantallas
- ✅ App sigue funcional (no force close)

---

## Resumen de Resultados Esperados

| Test Case | Descripción | Crítico |
|-----------|-------------|---------|
| 1 | Device Binding First Login | ✅ Sí |
| 2 | Device Mismatch 409 | ✅ Sí |
| 3 | Rebind con PIN | ✅ Sí |
| 4 | Batch Events Offline Sync | ✅ Sí |
| 5 | Session Finalization Offline | ✅ Sí |
| 6 | Idempotencia Batch | ✅ Sí |
| 7 | Idempotencia Finalization | ✅ Sí |
| 8 | switchCourse No Mezcla | ⚠️ Importante |
| 9 | Token Expirado Resiliente | ⚠️ Importante |
| 10 | Diagnostics Export | ⚠️ Importante |
| 11 | Kiosk Mode Bloqueo | ⚠️ Importante |
| 12 | Model Error Resiliente | ⚠️ Importante |

**Total**: 12 casos (7 críticos, 5 importantes)
