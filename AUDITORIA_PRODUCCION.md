# SENTINEL — Auditoría de producción (2026-10-23)

Auditoría de consolidación previa a distribución corporativa. **Conclusión: SENTINEL NO está
listo para distribución.** Faltan: firma corporativa del release, protección real del
aprovisionamiento administrativo (hoy accesible a quien escriba en la carpeta externa de la app),
el identificador TELÉFONO, y pruebas en dispositivo de CBU/CVU/DNI/nombre. Detalle abajo.

Artefactos auditados (pkgout12):
- APK debug: SHA-256 `ab627114bd331bdc8ad12c677b5bda6b769e35e54b1f303040a011006d79a322`
- APK release (SIN FIRMAR): SHA-256 `b4a1bf530506a3cc227f5e42195c48acd08d347a8d2143514c9690739d15b5f2`

## Separación pruebas / producción (control real, compile-time)
Se agregó `BuildConfig.TEST_TOOLS` (debug=true, release=false) y el placeholder de manifest
`testToolsEnabled`:
- **Release corporativo**: sin pantallas «Prueba de alarma» ni «Prueba de texto» (ocultas por
  `TEST_TOOLS`), y `KeyboardPlaygroundActivity` **deshabilitada a nivel de componente**
  (`android:enabled="false"` — no instanciable, verificado en el manifest fusionado del release).
  El disparador de alerta real (`RealAlertTestActivity`) vive en `src/debug` y **no existe** en el
  release (verificado). El log de `message_id` de Telegram está guardado por `BuildConfig.DEBUG`.
- **Build de pruebas (debug)**: conserva todas esas herramientas para el Moto G71.
No es «ocultar un botón»: en el release el código de las entradas no se ejecuta y el componente de
demostración está desactivado por el sistema.

## Las 9 preguntas del encargo

**1) ¿Puede un empleado cambiar la lista negra desde el teléfono?**
- **Desde la interfaz: NO.** `BlacklistScreen` es solo lectura («Lista administrada por el
  responsable (solo lectura en este celular)»), sin agregar/editar/eliminar/activar. `BlacklistEditScreen`
  (con `vm.save`) **no tiene ruta** en el `NavHost` (código muerto, inalcanzable). `ImportScreen` es
  **solo exportación** («el operador no puede importar cambios»).
- **Limitación real: SÍ por aprovisionamiento externo.** `BlacklistProvisioning` lee
  `sentinel_blacklist_provision.csv` desde `getExternalFilesDir()` (`Android/data/<app>/files/`).
  Quien pueda escribir ahí (gestor de archivos con acceso a `Android/data`, PC por USB, adb o MDM)
  puede alterar reglas. **No está protegido por una autorización real.** Ver «Pendientes».

**2) ¿Puede configurar Telegram o cambiar el grupo de alertas?**
- **Desde la interfaz: NO.** `TelegramScreen` es «Configuración autorizada (solo lectura)»; el único
  botón es «enviar prueba». No se puede editar token, grupo ni destinatarios.
- **Mismo límite que (1):** el JSON `sentinel_telegram_provision.json` en la carpeta externa sí
  permite reconfigurar. No protegido por autorización.

**3) ¿Cómo se asigna y valida la identidad del dispositivo?**
- `DeviceIdentityStore` (SharedPreferences `sentinel_device`): `id` interno **UUID estable**
  (persiste entre reinicios — verificado tras reboot), `friendlyName` (nombre corporativo), `status`
  (`PENDIENTE`/`REGISTRADO`), `appVersion`, `registeredAt`, `updatedAt`.
- El **nombre NO se puede escribir desde la UI** (no hay flujo de renombrado en pantalla; verificado).
  Se asigna solo por `DeviceProvisioning` (JSON externo `sentinel_device_provision.json`).
- **Validación: NO hay autenticación central.** El id es estable pero local; el nombre lo puede
  cambiar quien escriba el JSON externo. **La identidad NO es inviolable.** Punto de integración
  pendiente (ver «Pendientes»); no se simula un servidor.

**4) ¿Qué identificadores están completamente implementados y probados?** (ver matriz abajo)
- **ALIAS**: implementado + probado en JVM + **en dispositivo** (`patodm.nx → Pato Encargado`).
- **CBU, CVU, DOCUMENTO (DNI), NOMBRE + variantes**: implementados + probados en **JVM**
  (`PersonResolutionTest`, `TextNormalizerTest`); **no** probados en dispositivo con datos reales.
- **CODIGO, CUENTA, OTRO**: implementados; cobertura de prueba parcial.

**5) ¿Qué identificadores o escenarios siguen pendientes?**
- **TELÉFONO: NO implementado** (no existe `RuleType.TELEFONO`). Si está en alcance, hay que
  agregar el tipo + normalización (código de país, 0/15, separadores) + pruebas.
- Pruebas **en dispositivo** de CBU/CVU/DNI/nombre.
- Autoridad central de administración y protección del aprovisionamiento (ver «Pendientes»).
- Firma del release.

**6) ¿Qué aplicaciones y tipos de campo están realmente cubiertos?**
- **Solo campos marcados** con el marcador privado de SENTINEL (su propia app o apps que lo integren),
  excluyendo contraseña/PIN/OTP/email/URI/numérico/`NO_SUGGESTIONS`.
- **WhatsApp y Chrome**: escritura normal verificada; **inspección/prevención NO** (campos no marcados).
- **Mercado Pago, Ualá, Brubank**: **no cubiertas** (sin evidencia de detección/prevención). Ver
  `COBERTURA.md`. No hay detección universal con un IME normal.

**7) ¿El release está firmado con la clave corporativa correcta?**
- **NO.** `app-release-unsigned.apk` está **sin firmar**. No hay `signingConfig` y **no se inventó
  ninguna keystore**. Para distribuir hay que firmarlo con la keystore de la organización
  (`GUIA_OPERACION.md` §1). Sin firma, no es instalable ni distribuible.

**8) ¿Qué datos se conservan en una actualización y qué se provisiona en una instalación nueva?**
- **Actualización (`install -r`, misma firma):** conserva la base Room (reglas, personas, historial),
  la identidad del dispositivo y el token cifrado. Verificado repetidamente (incl. tras reinicio).
- **Instalación nueva en OTRO teléfono:** arranca **vacío** — base de datos sin reglas, sin identidad,
  sin token. `android:allowBackup="false"` evita que un backup restaure datos del Moto G71. **No
  recibe por accidente** la lista, identidad, historial ni credenciales del dispositivo de pruebas.
  Cada teléfono debe aprovisionarse por separado (CSV + JSON de Telegram + JSON de dispositivo).
  (Riesgo residual: copiar manualmente esos archivos de un equipo a otro sí los transfiere; es una
  acción deliberada del administrador, no automática.)

**9) ¿Qué falta para declarar SENTINEL listo para distribución?**
1. **Firmar** el release con la keystore corporativa.
2. **Proteger el aprovisionamiento** con autorización real (MDM/managed config, o autoridad central
   que autentique el dispositivo y entregue reglas firmadas). Hoy la carpeta externa es escribible.
3. **Completar TELÉFONO** si está en alcance; **probar en dispositivo** todos los identificadores.
4. **Matriz de cobertura verificada** (incluida aquí) y documentación de lo que NO puede detectar.

## Matriz de cobertura por identificador

| Tipo | Implementado | Normalización | Match (confianza) | Prueba JVM | Prueba dispositivo |
|---|:--:|---|---|:--:|:--:|
| ALIAS | ✅ | minúsculas, sin acentos, colapsa espacios (conserva `.`/`-`/`_`) | exacta 1.0 · secuencia de tokens 0.99 | ✅ | ✅ (`patodm.nx`) |
| CBU | ✅ | solo dígitos (quita separadores) | exacta 1.0 · contiene 0.99 (mín. 6) | ✅ | ❌ |
| CVU | ✅ | solo dígitos | exacta/contiene | ✅ | ❌ |
| DOCUMENTO (DNI) | ✅ | solo dígitos | exacta/contiene | ✅ | ❌ |
| NOMBRE + variantes | ✅ | tokens; 1er y último exactos, medios flexibles | aproximada 0.96 (rechaza nombres extra) | ✅ | ❌ |
| CODIGO | ✅ | texto normalizado | secuencia de tokens 0.99 | parcial | ❌ |
| CUENTA | ✅ | solo dígitos | exacta/contiene | parcial | ❌ |
| OTRO | ✅ | texto normalizado | secuencia de tokens | parcial | ❌ |
| TELÉFONO | ✅ (nuevo) | canónico AR: dígitos sin +54/0/9 | exacta por grupo completo (sin fragmentos) | ✅ (`PhoneDetectionTest`) | ❌ (no hay teléfonos en el prototipo) |

Tipos de coincidencia diferenciados por confianza: **exacta (1.0)**, **normalizada/contenida (0.99)**,
**aproximada de nombre (0.96)**. `MIN_CONFIDENCE = 0.95`. Una coincidencia aproximada de nombre **no**
se trata como prueba definitiva de identidad: queda con confianza 0.96 y, si el identificador apunta a
más de una persona, se marca **ambigua** (no se elige arbitrariamente); si no apunta a nadie, **sin
resolver**. Identificadores desconocidos **no** inventan persona.

Entrada cubierta: escritura carácter por carácter, **pegado** (portapapeles), autocorrección/sugerencias
(no convierten texto permitido en coincidencia), con deduplicación por episodio y rearme — todo
verificado en dispositivo (pkgout10/11) sobre campos marcados.

## Puntos de entrada / superficie (release corporativo)
- `MainActivity` (LAUNCHER, exported) — UI del operador, solo lectura para lista/Telegram.
- `EvaluateTextActivity` (exported, PROCESS_TEXT/SEND) — verificación consentida de un texto que el
  operador comparte; **solo lectura** (no crea incidente ni alerta). **Decisión pendiente**: expone
  el resultado de coincidencia a cualquier app que envíe texto; si se considera fuga de información,
  conviene deshabilitarla en el release (hoy queda habilitada como función del operador).
- `SentinelInputMethodService` (exported con `BIND_INPUT_METHOD`, requerido para un IME).
- `AlarmService` (no exported), `AlertActivity` (no exported), `KeyboardPlaygroundActivity`
  (**deshabilitada en release**).
- Permisos: INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE(+SPECIAL_USE), POST_NOTIFICATIONS,
  VIBRATE. Sin permisos peligrosos (sin contactos, SMS, almacenamiento amplio, accesibilidad).

## Arquitectura pendiente (documentada, NO implementada — no se simula)
Para una administración central real se requiere, como mínimo:
- Un servicio que **autentique cada dispositivo** (p. ej. certificado/clave por dispositivo emitido en
  el alta) y entregue la lista negra **firmada**; el teléfono verifica la firma antes de aplicar.
- Aprovisionamiento por **MDM/managed configuration** (RestrictionsManager) o almacenamiento no
  escribible por el usuario, en lugar de `getExternalFilesDir()`.
- Registro de dispositivo contra ese servicio (estado, nombre corporativo) no alterable localmente.
- Rotación/custodia del token de Telegram del lado servidor.
Nada de esto existe hoy; el aprovisionamiento por archivos externos es un **mecanismo de transición**
que debe quedar fuera del alcance del usuario final mediante MDM mientras no exista la autoridad central.
