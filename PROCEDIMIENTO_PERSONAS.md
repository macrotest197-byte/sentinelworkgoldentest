# Procedimiento: modelo de personas, identidad del dispositivo y formato de alerta

Evolución del 2026-10-23. La unidad primaria pasa a ser la **persona**; cada persona agrupa
varios **identificadores** (alias, CBU, CVU, DNI, nombre y variantes). Al detectarse cualquier
identificador en un campo autorizado, SENTINEL resuelve la **persona asociada**, registra el
incidente con la identidad del dispositivo y emite la alerta.

Reutiliza la arquitectura existente (**no** hay segundo cliente ni pipeline nuevo):
`BlacklistPersonMatcher.resolve()` · `ConfirmedAlertProcessor` · `EventDeduplicator` ·
`SecureSettingsStore` · `TelegramClient` · `TelegramSendWorker`.

## 1. Flujo completo
Campo autorizado → normalización (`TextNormalizer`) → búsqueda del identificador →
persona asociada (`resolve`) → identidad del dispositivo (`DeviceIdentityStore`) →
deduplicación (`EventDeduplicator`) → historial (`IncidentRepository`) → alarma local →
Telegram (cola con reintentos). No se alerta por carácter; se evalúa el campo completo
(pegado/autocompletado/acción del editor vía `onUpdateSelection`).

## 2. Formato de la alerta de Telegram
```
🚨 SENTINEL — DETECCIÓN DE LISTA NEGRA
📱 Dispositivo: Celular Pato
🔎 Palabra detectada: patodm.nx
👤 Le corresponde a: Pato Encargado
🧾 Tipo de dato: Alias
🕒 Fecha y hora: <fecha y hora local>
```
- **Palabra detectada**: el identificador concreto que coincidió (no el texto del operador).
- **Le corresponde a**: nombre de la persona vinculada. Si el identificador no resuelve a nadie,
  `Sin resolver`; si corresponde a **más de una persona**, `Sin resolver (asociación ambigua)`.
  **Nunca** se inventa un nombre ni se elige una persona arbitrariamente.
- Solo se transmiten los campos necesarios: sin saldos, contraseñas, credenciales ni capturas.

## 3. Formato CSV de administración (personas)
Encabezado (las dos primeras columnas son obligatorias; el resto es opcional y retrocompatible):
```
tipo,valor,descripcion,severidad,activo,persona_id,nombre_principal,variantes
```
- Una persona se expresa en **varias filas** que comparten el mismo `persona_id` y
  `nombre_principal`. Cada fila aporta un identificador (alias/CBU/CVU/DNI/nombre).
- `variantes`: nombres alternativos separados por `|` (p. ej. `"Patricio Encargado|Pato E."`).
- CSV sin las columnas de persona sigue funcionando igual que antes (retrocompatibilidad):
  las reglas se tratan como identificadores sueltos sin persona asociada.

Ejemplo (persona `pato-1` = «Pato Encargado», con la regla `patodm.nx`):
```
tipo,valor,descripcion,severidad,activo,persona_id,nombre_principal,variantes
Alias,patodm.nx,Cuenta restringida,Crítica,si,pato-1,Pato Encargado,"Patricio Encargado"
CBU,0000003100010000000001,,Crítica,si,pato-1,Pato Encargado,"Patricio Encargado"
Nombre,Pato Encargado,,Crítica,si,pato-1,Pato Encargado,"Patricio Encargado"
```
- `BlacklistProvisioning.applyProvisionedRules` **actualiza en sitio** las reglas existentes por
  `(tipo, valor normalizado)`; no borra la lista ni reinicia la base. El historial se conserva.
- Aprovisionamiento idéntico al de Telegram: se deja el archivo CSV en el directorio privado de la
  app y se importa al iniciar (la interfaz del operador es de **solo lectura**).

## 4. Identidad del dispositivo (`DeviceIdentityStore`)
Registro local, sin servidor:
- `id`: identificador interno estable (UUID), persiste entre reinicios y renombrados.
- `friendlyName`: nombre visible del administrador (p. ej. «Celular Pato»).
- `status`: `PENDIENTE` hasta que el administrador asigna nombre, luego `REGISTRADO`.
- `registeredAt` / `updatedAt`: fechas de alta y última actualización.
- `appVersion`: versión de SENTINEL (se sincroniza en cada arranque, sin alterar id ni alta).

Renombrado/registro por el flujo de administrador (`DeviceProvisioning`, JSON
`sentinel_device_provision.json` con `{"deviceName":"Celular Pato"}`), nunca desde la UI del operador.

## 5. Criterio de aceptación
Al detectar `patodm.nx` en un campo autorizado del dispositivo registrado como **Celular Pato**,
SENTINEL debe resolverlo como **Pato Encargado**, registrar el incidente y generar la alerta con los
tres datos correctos (dispositivo, palabra detectada → persona, tipo). No se considera completo si
solo funciona la detección pero no la asociación con la persona o el dispositivo.

## 6. Pruebas de regresión (verde)
`PersonResolutionTest` (alias principal → Pato Encargado; segundo alias → misma persona; CBU/CVU →
persona correcta; variante de nombre → persona; desconocido → no inventa persona; identificador en
dos personas → ambigüedad). `CsvTest` (import/export preserva persona/variantes/identificadores).
`TelegramClientTest` (formato exacto + exclusión de campos sensibles). `DetectionPipelineTest`
(dedup + rearmado). Durante las pruebas **no** se envía Telegram real ni se activa la alarma física.
