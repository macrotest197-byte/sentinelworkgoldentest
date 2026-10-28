# ENTREGA — S.E.N.T.I.N.E.L.

Herramienta de **detección y alerta** para celulares corporativos de recaudación.
Detecta la aparición de datos de una lista negra (personas con nombres/variantes e
identificadores) en **texto de fuentes autorizadas**, registra el incidente, activa una
alarma local y notifica a Telegram. **No** monitorea apps de terceros, **no** captura
teclas ni pantalla de forma oculta.

> Fecha de esta entrega: 2026-10-22. JDK 21. Build externo vía junction `app\build → C:\SentinelBuild\build3` (intacto).

## 0. Teclado de prevención (integración Opción B)

Integrado en el proyecto canónico reutilizando los componentes originales
(`BlacklistPersonMatcher`, `ConfirmedAlertProcessor`, `EventDeduplicator`, `TelegramClient`,
identidad del dispositivo). **No** se mezcló el checkout paralelo; backup previo en
`C:\SentinelBuild\backup-canonico-20261022-120057`.

**APK con teclado:** `C:\SentinelBuild\pkgout4\app\outputs\apk\debug\app-debug.apk`
· 2026-10-22 12:14 · 19.374.079 bytes · SHA256 `B94472CF4C61A792E21647A954D521E5CBA967B88E275297568BF893CBB94028`.
**Pruebas:** 43, 0 fallos (baseline 36 + `InputFieldPolicyTest` 7). Sin cambios de esquema Room.

### Tres niveles de protección (distintos — verificar por separado)
1. **Detectar** la coincidencia: el teclado compara lo escrito en un campo **marcado** con la
   lista negra. ✅ implementado; verificado en lógica por pruebas; falta verificación en campo real marcado.
2. **Impedir que el dato quede en el campo**: al coincidir, limpia el campo (best-effort) y
   avisa. ⚠ depende de la app/campo; no garantizado en campos personalizados ni en pegado.
3. **Garantizar que la transferencia no se complete**: ❌ **no** lo da el teclado. Requiere
   integración de la app de cobro o control previo a confirmar. No afirmar bloqueo de la
   transferencia por impedir escribir un dato.

### Alcance y límite esencial (fail-closed)
El teclado solo inspecciona campos que **la app dueña marca** con el marcador privado de
SENTINEL, y excluye contraseñas/PIN/OTP. Por diseño (para no ser un keylogger general),
**no** inspecciona apps que no cooperen.

### Matriz de compatibilidad (estado real)
| Aplicación | Escritura manual | Pegado | Detección | Bloqueo efectivo | Alertas/incidente | Observaciones |
|---|---|---|---|---|---|---|
| Campo de **demo** interno de SENTINEL | sí | sí* | **sí (verificado en lógica)** | limpia el campo | **sin** alarma/Telegram (es demo) | `KeyboardPlaygroundActivity`. *pegado por verificar en dispositivo |
| App de cobro **propia** que integre el marcador | sí | parcial | sí | limpia el campo | incidente+alarma+Telegram | requiere que esa app agregue `SAFE_RECIPIENT_TEXT`; aún no existe esa app |
| **Mercado Pago / Ualá / Brubank** | — | — | **no** | **no** | no | no integran el marcador → fuera de alcance del teclado |
| Navegadores / formularios web | — | — | no | no | no | los campos web no exponen el marcador; fuera de alcance |
| Otras apps de terceros sin marcador | — | — | no | no | no | fail-closed |

> Nada de esta matriz está verificado **dentro de las apps reales en un dispositivo** (no hay
> emulador con esas apps y no se instaló). Lo verificable hoy es la lógica (43 pruebas) y la
> compilación. La verificación en dispositivo requiere tu intervención (protocolo abajo).

### Control complementario recomendado (para las apps fuera de alcance)
Para Mercado Pago/Ualá/Brubank y navegadores, el teclado **no** puede prevenir. El control
efectivo es **integración autorizada** (la app de cobro propia valida el destinatario antes
de confirmar) y/o **política corporativa Android Enterprise** (restricciones del dispositivo).

### Protocolo de prueba en dispositivo (lo hacés vos; no instalo sin tu OK)
1. Instalar el APK; activar el teclado "SENTINEL · teclado de prevención" en Ajustes.
2. Inicio → "Teclado de prevención" → "Abrir campo de demostración".
3. Con el teclado SENTINEL, escribir `sentinel.demo` → debe avisar y limpiar el campo (sin alarma/Telegram).
4. Probar pegar `sentinel.demo` y anotar si se bloquea.
5. En Mercado Pago/Ualá/Brubank: confirmar que el teclado **no** inspecciona (esperado, fuera de alcance).

## 0b. Teclado universal + cobertura de billeteras (actualizado)

**Teclado universal (verificado en Moto G71):** las teclas permanecen visibles y funcionales
en campos **no** autorizados; se puede escribir/borrar normalmente en WhatsApp con el teclado
SENTINEL, y ese campo **no** se inspecciona (fail-closed verificado: `sentinel.demo` escrito
en WhatsApp **permanece**; el mismo valor en la demo marcada se **bloquea**). Letras, Ñ,
números, símbolos, espacio, Enter y cambios de distribución: presentes (QWERTY-ES de 3 capas).

**Cobertura por aplicación — qué detecta / qué bloquea / qué requiere integración:**

| App | Escribir con el teclado | Detectar coincidencia | Bloquear el dato | Qué haría falta |
|---|---|---|---|---|
| **Campo demo / app que integre el marcador** | sí | **sí** | sí (limpia el campo) | ya integrado (demo); app propia debe marcar su campo con `SAFE_RECIPIENT_TEXT` |
| **WhatsApp** | **sí (verificado)** | no (campo no marcado) | no | no aplica (no es app de cobro) |
| **Mercado Pago / Ualá / Brubank** | sí (escritura normal) | **no** | **no** | **no integran el marcador** → el teclado no detecta ni bloquea. Requiere integración de la app o control previo a confirmar |
| Navegadores / formularios web | sí | no | no | el campo web no expone el marcador → fuera de alcance |

**Controles viables para las billeteras (fuera del alcance del teclado):**
1. **Integración con la app/flujo de cobro propio**: que valide el destinatario contra la
   lista negra **antes de confirmar** (por Intent/API local). Es lo más fiable.
2. **Validación previa a la confirmación** dentro de ese flujo controlado.
3. **Android Enterprise / MDM**: políticas en el dispositivo corporativo (apps obligatorias,
   bloqueo de desinstalación, kiosco), que **no** inspeccionan apps de terceros pero sí
   endurecen el dispositivo.
4. No existe en Android un control que, sin cooperación de Mercado Pago/Ualá/Brubank,
   garantice bloquear una transferencia de un tercero. **No se afirma lo contrario.**

## 1. Funcionalidades terminadas (compiladas y verificadas)

- **Lista negra (Room)**: alta/edición/baja, búsqueda (incluye nombre/variantes/personId), activar/desactivar. Modelo de **persona** con `primaryName`, `knownNameVariants` e `identifiers`.
- **Matcher de personas** (`TextNormalizer` + `BlacklistPersonMatcher`): acepta segundos nombres/iniciales/acentos; **rechaza** apellido distinto, orden distinto (nombre extra al inicio) y apellido extra al final; identificadores (CBU/CVU/doc/alias) con coincidencia conservadora. *Verificado por tests.*
- **Deduplicación de eventos** (`EventDeduplicator`): un mismo evento no genera incidente/alarma/Telegram duplicados (ventana configurable). *Verificado por tests.*
- **Importación/Exportación CSV**: vista previa, validación de columnas y valores, detección de duplicados en archivo, plantilla, y **provisioning CSV de administración** (`BlacklistProvisioning`) que valida y puede **actualizar** reglas sin sobrescribir con datos inválidos.
- **Alarma local**: sonido en bucle + vibración intensa, pantalla de alerta sobre el bloqueo, botón RECONOCER, una sola alarma simultánea, servicio en primer plano. *Verificada en tu teléfono en una fase previa.*
- **Telegram**: envío real por API, **cola de pendientes**, reintentos controlados, **deduplicación**, **config de SOLO LECTURA** para el operador, **aprovisionamiento seguro del token** (cifrado Keystore, archivo importado y borrado), **redacción del token** en errores. *Configurado e instalado en el teléfono (token + grupo `-5144820304`).*
- **Historial de incidentes**: fecha, dispositivo/ID, persona, tipo, valor (oculto parcial), confianza, fuente, app, estado de alarma y de Telegram; búsqueda, filtros, exportación CSV.
- **Identidad de dispositivo** (`DeviceIdentityStore`): ID persistente por instalación + **nombre configurable por el administrador** (`DeviceProvisioning`), incluido en las alertas.
- **Migraciones Room 3→7**: columnas de deviceId, persona, confianza y excerpt. Consistentes con las entidades.
- **Circuito de detección** (`ConfirmedAlertProcessor`): texto autorizado → match de persona → validación de umbral → **dedup** → incidente → alarma → historial → Telegram, con el mensaje exacto requerido.
- **Pantalla de prueba simulada** (`SimulatedTextTestScreen`): solo compara texto con la lista; **no** crea incidente ni envía Telegram ni activa alarma (simulación claramente separada de incidentes reales).

### Mensaje de Telegram (formato exacto)
`AlertMessage.alert(...)` produce:
> "Se detectó un intento de transferencia a [NOMBRE DE LA PERSONA] desde el celular [NOMBRE DEL CELULAR]."
*Verificado por test (`TelegramClientTest.mensajeAlerta_tieneFormatoSolicitado`).*

## 2. Archivos modificados en esta sesión
- `detection/EventDeduplicator.kt` — **nuevo** (dedup de eventos).
- `detection/ConfirmedAlertProcessor.kt` — aplica dedup al inicio (no duplica por mismo evento).
- `detection/TextNormalizer.kt` — guarda simétrica del token previo (rechaza nombre extra al inicio).
- `SentinelApp.kt` — corrección `this@SentinelApp` en el provisioning CSV.
- `data/repo/BlacklistRepository.kt` — eliminado import duplicado de `Severity`.
- `app/src/test/.../EventDeduplicatorTest.kt` — **nuevo** (3 pruebas).
- `INFORME.md`, `ENTREGA_SENTINEL.md` — documentación.

## 3. Tests ejecutados — resultados reales
`gradlew testDebugUnitTest` (corrida 2026-10-22 11:22): **BUILD SUCCESSFUL — 32 pruebas, 0 fallos**:
- `TextNormalizerTest` 9 · `CsvTest` 6 · `TelegramClientTest` 6 · `TelegramSecurityTest` 6 · `DeviceProvisioningTest` 2 · `EventDeduplicatorTest` 3.
- Cubren: coincidencia positiva/negativa, falsos positivos de nombres, CBU/CVU, importación CSV sin pérdida, dedup, formato Telegram, seguridad del token (no se filtra), parseo de aprovisionamiento.

## 4. Resultado de compilación
- `compileDebugKotlin` / `compileDebugUnitTestKotlin`: **OK**.
- `assembleDebug` (salida aislada): **BUILD SUCCESSFUL**.

## 5. APK nuevo
- **Ruta:** `C:\SentinelBuild\pkgout2\app\outputs\apk\debug\app-debug.apk`
- **Fecha:** 2026-10-22 08:24:44 · **Tamaño:** 19.339.816 bytes
- **SHA256:** `D586386753DD02E4BC011752CC0AF352622F1459B389E302363F796BCA9B8361`
- Compilado en **directorio de salida aislado** (no en el junction) para evitar el bloqueo intermitente de `packageDebug` (ver INFORME.md). El junction `app\build → C:\SentinelBuild\build3` quedó **intacto**.

## 6. Pruebas de integración completadas
- La **lógica del circuito** está verificada por pruebas JVM: matcher de personas (positivos/negativos), dedup de eventos, formato del mensaje, validación de CSV/aprovisionamiento y seguridad del token.
- La **prueba simulada en la app** (`SimulatedTextTestScreen`) verifica la etapa de detección sin efectos (no incidente/alarma/Telegram).
- **Pendiente (honesto):** una prueba end-to-end **en dispositivo** del circuito vivo completo (alarma real + envío real + escritura en DB) **no se ejecutó automáticamente** aquí, porque requiere un equipo/emulador y, por diseño, no debe enviar mensajes reales ni disparar alarmas sin control. Ver "Qué falta para producción".

## 7. Limitaciones reales de Android
- **Accesibilidad/OCR**: no implementados (decisión de diseño). Muchos bancos usan `FLAG_SECURE` y no exponen texto; la detección usa **fuentes autorizadas**, no scraping de terceros.
- **Servicio en segundo plano**: Android 12+ restringe iniciar un foreground service desde segundo plano; fabricantes pueden pausar servicios por batería.
- **No molestar**: el sonido puede silenciarse salvo que se conceda la excepción de DND.
- **Persistencia**: identidad e historial sobreviven a **actualizaciones** del APK; se **pierden** ante "Borrar datos" o **desinstalación**. Migraciones <3 son destructivas (no hay instalaciones viejas esperadas).
- **Token**: `EncryptedSharedPreferences` protege en reposo, pero un secreto en el dispositivo es extraíble con acceso físico/root. Protección real = backend.

## 8. Qué falta para producción
1. **APK release firmado** (keystore de la empresa) con `minify`/`shrink`.
2. **Backend relay** para que el token no viva en el dispositivo y validar el destino del lado servidor.
3. **Adaptador de fuente autorizada** real que entregue eventos a `ConfirmedAlertProcessor` (p. ej. integración con la app de cobro propia por Intent/API local). Hoy el circuito existe pero no hay fuente viva conectada.
4. **Prueba integral en app controlada (dry-run)**: un modo de prueba que recorra el circuito completo marcándolo como simulación, sin enviar a Telegram ni sonar. (Base lista: `SimulatedTextTestScreen` + `ConfirmedAlertProcessor`.)
5. **Android Enterprise** para impedir desinstalación (ver Fase 4 abajo).
6. Afinar umbrales de falsos positivos con datos reales.

## 9. Instalación y configuración de cada celular (pasos concretos)

1. **Instalar el APK**: transferir `app-debug.apk` y permitir instalación de orígenes desconocidos. (Para varios equipos, preferir distribución por MDM.)
2. **Permisos**: abrir SENTINEL y conceder **Notificaciones**. Para pantalla de alerta sobre bloqueo en algunos OEM (Xiaomi/Huawei/Oppo), habilitar "mostrar en pantalla de bloqueo"/"ventanas emergentes". Para sonar en No molestar, conceder excepción de DND.
3. **Nombre del dispositivo** (admin): colocar `sentinel_device_provision.json` con `{"deviceName":"PUESTO_03"}` en `Android/data/<paquete>/files/`. Al abrir, SENTINEL lo importa y lo borra.
4. **Telegram** (admin): colocar `sentinel_telegram_provision.json` (token + `groupChatId`) en la misma carpeta. Se importa cifrado y se borra. El operador no puede editarlo.
5. **Lista negra** (admin): colocar `sentinel_blacklist_provision.csv` (columnas tipo,valor,descripcion,severidad,activo[,persona_id,nombre_principal,variantes]) para cargar/actualizar reglas. Se valida y se borra tras importar.
6. **Verificación**: en SENTINEL, pantalla Telegram → "Enviar notificación de prueba" (confirma envío real); pantalla "Prueba de texto simulada" → pegar un texto con un nombre de la lista y ver la coincidencia (sin efectos).
7. **Anti-desinstalación**: inscribir el equipo en **Android Enterprise** (ver Fase 4).

> El paquete en la APK debug es `com.sentinel.recaudacion.debug` (la ruta `Android/data/` usa ese nombre). En release será `com.sentinel.recaudacion`.

---

## Fase 4 — Android Enterprise (anti-desinstalación): solución documentada

Para impedir que un operador desinstale SENTINEL o borre sus datos, la vía soportada por
Android es la **administración de dispositivos corporativos**, no trucos de app:

1. **Inscripción gestionada**: registrar cada celular como **company-owned** en un **EMM/MDM**
   compatible con Android Enterprise (p. ej. Android Management API de Google, o un EMM
   comercial). La inscripción se hace con el equipo en fábrica/reset autorizado (QR/afw#,
   token de aprovisionamiento) — **requiere tu intervención; no inscribo ni reseteo equipos.**
2. **Política**: en el EMM, marcar SENTINEL como **app obligatoria/persistente**
   (`installType: FORCE_INSTALLED` o `KIOSK`), y aplicar restricciones:
   `uninstallAppsDisabled`/bloqueo de desinstalación para el paquete, bloqueo de "Borrar datos",
   y opcionalmente **modo kiosco** (app en primer plano fija) para puestos dedicados.
3. **Alternativa Device Owner**: si los equipos son exclusivos, provisionarlos como
   **Device Owner** permite políticas fuertes (bloqueo de desinstalación, factory-reset
   protection) desde un DPC. También requiere reset/inscripción inicial autorizada.
4. **Datos que sobreviven**: con FORCE_INSTALLED la app no se puede desinstalar; una
   actualización conserva `SharedPreferences`/DB (identidad, historial, config). Un
   "Borrar datos" (si no está bloqueado por política) sí los elimina — por eso conviene el
   bloqueo vía EMM.

**No implementable solo desde la app**: la protección anti-desinstalación depende de la
inscripción MDM y políticas, que son acciones administrativas externas.

---

## Actualización 2026-10-23 — Modelo de personas, identidad del dispositivo y nueva alerta

La unidad primaria pasa a ser la **persona**, que agrupa múltiples identificadores (alias, CBU,
CVU, DNI, nombre y variantes). Al detectar cualquier identificador en un campo autorizado,
SENTINEL resuelve la **persona asociada**, registra el incidente con la **identidad del
dispositivo** y emite la alerta con el nuevo formato. Detalle en `PROCEDIMIENTO_PERSONAS.md`.

- **Resolución de persona** (`BlacklistPersonMatcher.resolve`): devuelve el identificador que
  coincidió + la persona vinculada. Si el identificador corresponde a más de una persona, marca
  **ambigüedad** y deja la persona sin resolver (no elige arbitrariamente). Nunca inventa un nombre.
- **Identidad del dispositivo** (`DeviceIdentityStore`): id interno estable (UUID), nombre visible
  del administrador, estado (`PENDIENTE`/`REGISTRADO`), fechas de alta/actualización y versión de
  SENTINEL. Persiste entre reinicios; renombrado por el flujo de administrador, no desde la UI.
- **Formato de alerta** (sin datos sensibles):
  ```
  🚨 SENTINEL — DETECCIÓN DE LISTA NEGRA
  📱 Dispositivo: Celular Pato
  🔎 Palabra detectada: patodm.nx
  👤 Le corresponde a: Pato Encargado
  🧾 Tipo de dato: Alias
  🕒 Fecha y hora: <fecha y hora local>
  ```
- **CSV de administración**: columnas opcionales `persona_id,nombre_principal,variantes`
  (retrocompatible). Una persona se expresa en varias filas con el mismo `persona_id`. La regla
  `patodm.nx` queda vinculada a la persona `pato-1` «Pato Encargado».
- **Pruebas** (`testDebugUnitTest` verde): `PersonResolutionTest`, `CsvTest`, `TelegramClientTest`,
  `DetectionPipelineTest`. Durante las pruebas no se envía Telegram real ni se activa la alarma.
- **APK**: `app-debug.apk` · SHA-256 `f6ece917500871dd585187cb3f7a07e8dcac4b3bd071c3f70245195929cc32c8`.
- **Build (nota)**: la JBR de Android Studio se actualizó a JDK 25 (incompatible con Gradle 8.9);
  se compila con `JAVA_HOME=C:\Users\diazm\.jdks\jbr-21.0.11` (JDK 21) y el init-script
  `C:\SentinelBuild\reloc7.init.gradle.kts` que reubica el buildDir fuera de OneDrive. Sin `clean`.

---

## Entrega final — Candidato a producción (2026-10-23, pkgout10)

- **Teclado SENTINEL** con barra de herramientas: Pegar, sugerencias/autocorrección locales (sin
  almacenar lo escrito), estado de detección y cambio de teclado. Escritura normal en apps
  compatibles; sugerencias excluidas en campos sensibles. Detalle técnico en `INFORME.md`.
- **Modelo de personas** y **formato de alerta** de seis renglones preservados (ver `PROCEDIMIENTO_PERSONAS.md`).
- **Circuito real único** (detección → persona → dispositivo → dedup → historial → alarma → cola →
  Telegram) sin pipeline paralelo; validado en dispositivo en la prueba integral previa (pkgout9).
- **Debug/producción separados**: herramienta de prueba solo en `src/debug`, ausente del release.
- **APK release (sin firmar)**: `app-release-unsigned.apk` · SHA-256
  `4efb5971551963094d12764fb09736f76ef450b51d0624af2d9ffa6c59f6060a` (firmar antes de instalar).
- **APK debug**: `app-debug.apk` · SHA-256
  `556de0413c4632c6a43cb5f08523d87a68cc1da7eb5cc6622c3dd909d3394572`.
- **Pruebas**: 73 automatizadas, 0 fallos (JDK 21).
- **Guías**: `GUIA_OPERACION.md` (instalación/firma/config/actualización/recuperación) y
  `COBERTURA.md` (matriz real por app + límites).
- **Límite declarado**: sin detección universal; billeteras de terceros no protegidas automáticamente.

---

## Actualización 2026-10-23b — Barra inteligente contextual (pkgout11)

La barra del teclado ahora alterna, como un teclado Android normal: **Pegar centrado** cuando hay
texto en el portapapeles (sugerencias ocultas), o **hasta 3 sugerencias** (centro = la más probable)
cuando el portapapeles está vacío y el campo las permite. 🌐 queda separado; altura estable; aviso
de detección intacto. Lógica en `KeyboardToolbarPlanner` (puro, testeable), sin segundo sistema.
- APK debug SHA-256 `878074adc534cfff105170ee0c9bc6b9aa0f7c1cf3ebaf70bbb9cb3ca239db41`.
- APK release (sin firmar) SHA-256 `bfcdbda416501c185ce166991288709720809b13244a9e2aa67751bec256598c`.
- 80 pruebas automatizadas, 0 fallos. Validación en dispositivo pendiente (teléfono desconectado).

---

## Auditoría de producción (2026-10-23, pkgout12)

**SENTINEL NO está listo para distribución** (ver `AUDITORIA_PRODUCCION.md`). Falta: firma corporativa,
protección real del aprovisionamiento (hoy carpeta externa), tipo TELÉFONO, y pruebas en dispositivo de
CBU/CVU/DNI/nombre. Se agregó separación por build (`TEST_TOOLS`): el release corporativo no expone
pantallas de prueba y deshabilita el campo de demostración; el build de pruebas conserva todo.
- APK debug SHA-256 `ab627114bd331bdc8ad12c677b5bda6b769e35e54b1f303040a011006d79a322`.
- APK release (SIN FIRMAR) SHA-256 `b4a1bf530506a3cc227f5e42195c48acd08d347a8d2143514c9690739d15b5f2`.
- 80 pruebas automatizadas, 0 fallos. Guías: `AUDITORIA_PRODUCCION.md`, `ALTA_DISPOSITIVO.md`.
