# SENTINEL — Detección por AccessibilityService (fase 1: detectar y avisar)

Primera implementación **opt-in** de detección en billeteras autorizadas mediante accesibilidad de
Android. **Objetivo: detectar y avisar, NO bloquear.** No pulsa controles, no modifica campos, no
toca transferencias.

## Arquitectura y archivos (reutiliza el motor existente, sin segundo pipeline)
- `detection/AccessibilityScanPolicy.kt` (**nuevo**, puro/testeable): allowlist de paquetes reales y
  decisión de inspección (app autorizada + no contraseña + largo razonable).
- `input/SentinelAccessibilityService.kt` (**nuevo**): el servicio. Reutiliza
  `BlacklistPersonMatcher.resolve` (mismo matcher) y `ConfirmedAlertProcessor.process` (mismo
  incidente + alarma + cola de Telegram). Dedup por destinatario (`lastKey`) + dedup del procesador.
- `res/xml/sentinel_accessibility_service.xml` (**nuevo**): config restringida a los 3 paquetes,
  `canRetrieveWindowContent=true`, **sin** `canPerformGestures` (no puede pulsar nada).
- `ui/input/AccessibilitySetupScreen.kt` (**nuevo**): pantalla opt-in (explicación + estado + abrir
  Ajustes). Entrada en Home «Detección en billeteras».
- `AndroidManifest.xml`: servicio `exported="false"` con `BIND_ACCESSIBILITY_SERVICE`.
- `strings.xml`: etiqueta y descripción del servicio.

## Flujo
1. Evento de una app autorizada (`com.mercadopago.wallet`, `ar.com.bancar.uala`, `com.brubank`).
2. Se lee el **texto del campo que cambió** (no la pantalla completa); se descartan campos de contraseña.
3. Normalización + comparación con la lista negra local (`resolve`).
4. Coincidencia confirmada → `ConfirmedAlertProcessor`: incidente + alarma + alerta de Telegram con el
   **formato acordado** (dispositivo, dato detectado, persona, tipo, fecha/hora). Identidad del
   dispositivo reutilizada (p. ej. `Celular Pato`).
5. Ambiguo → no se atribuye a una persona (persona «sin resolver (ambigua)»).
6. Sin coincidencia → no se genera alerta.
Dedup: el mismo destinatario no re-alerta mientras el usuario permanece en la pantalla; una pantalla
nueva (`TYPE_WINDOW_STATE_CHANGED`) rearma.

## Privacidad (cumplimiento)
- Solo apps de la allowlist; nunca contraseñas (`isPassword`).
- No se capturan pantallas completas, pulsaciones globales ni conversaciones.
- A Telegram solo van los campos mínimos de la coincidencia (no el texto general de la pantalla);
  `authorizedExcerpt = null` (no se almacena el texto).
- El servicio no puede realizar gestos ni pulsar controles (no declara `canPerformGestures`).

## Procedimiento para habilitar / deshabilitar
1. Abrir SENTINEL → «Detección en billeteras» → «Abrir ajustes para activar».
2. En Ajustes → Accesibilidad → «SENTINEL · detección en billeteras» → activar (Android pide confirmación).
3. Para desactivar: la misma ruta → desactivar. La pantalla de SENTINEL refleja el estado al volver.
Por adb (diagnóstico, con autorización): activar
`settings put secure enabled_accessibility_services com.sentinel.recaudacion.debug/com.sentinel.recaudacion.input.SentinelAccessibilityService`
y `settings put secure accessibility_enabled 1`; desactivar dejando el valor anterior.

## Diagnóstico por aplicación (PENDIENTE de prueba en dispositivo)
Los **paquetes** fueron verificados en el Moto G71 (lectura): Mercado Pago `com.mercadopago.wallet`,
Ualá `ar.com.bancar.uala`, Brubank `com.brubank`. **La exposición real de los nodos NO se probó aún**
(requiere autorización para instalar/activar y navegar las apps). Qué falta verificar por app, con el
método (enable del servicio + `uiautomator dump` de la pantalla de transferencia, o los eventos que
recibe el servicio):

| App | Paquete | Campo destinatario accesible | Tipo | Evento recibido | Estado | Prueba en dispositivo (2026-10-24) |
|---|---|---|---|---|---|---|
| **Mercado Pago** | com.mercadopago.wallet | **SÍ** (campo «Ingresa un alias, CBU/CVU, celular o nombre») | EditText accesible | `TYPE_VIEW_TEXT_CHANGED` (16) y `TYPE_WINDOW_CONTENT_CHANGED` (2048) | **DETECTA** | ✅ `patodm.nx → Pato Encargado` detectado; incidente + alarma + Telegram (message_id 13). Captura de pantalla bloqueada por FLAG_SECURE, pero accesibilidad sí expone el texto. |
| **Ualá** | ar.com.bancar.uala | no verificable | login (huella/contraseña) | — | **NO VERIFICADO** | ❌ no logueada: no se pudo pasar la pantalla de ingreso (huella/clave del usuario). No se evaluó la pantalla de destinatarios. |
| **Brubank** | com.brubank | no verificable | login (huella/clave) + FLAG_SECURE | — | **NO VERIFICADO** | ❌ no logueada: pantalla de huella/clave; capturas bloqueadas. No se evaluó la pantalla de destinatarios. |

Si una app usa **WebView/Flutter/canvas** o pone `FLAG_SECURE` en la pantalla de transferencia, el
texto del destinatario **puede no estar expuesto** y el servicio no detectará nada ahí. Eso solo se
confirma probando en el dispositivo. **No se declara cobertura de ninguna billetera** con esta fase.

## Matriz de cobertura (implementado vs probado en dispositivo)
| Capacidad | Implementado | Probado automatizado | Probado en dispositivo |
|---|:--:|:--:|:--:|
| Servicio opt-in, allowlist, exclusión de contraseñas | ✅ | ✅ (`AccessibilityScanPolicyTest`) | ❌ |
| Reutiliza matcher + `ConfirmedAlertProcessor` (sin 2º pipeline) | ✅ | ✅ | ❌ |
| Resolución a persona / ambigüedad / sin coincidencia | ✅ | ✅ | ❌ |
| Dedup por destinatario | ✅ | ✅ | ❌ |
| Formato de alerta acordado | ✅ (reutilizado) | ✅ (`TelegramClientTest`) | ❌ (no se envió real) |
| Detección real en **Mercado Pago** | ✅ | — | ✅ **DETECTA** (en dispositivo) |
| Detección real en Ualá / Brubank | ✅ (código) | — | ❌ **NO VERIFICADO** (login) |
| Bloqueo de transferencia | ❌ (fuera de alcance de esta fase) | — | — |

## Riesgos y limitaciones
- **No bloquea**: solo detecta y avisa. Impedir la transferencia no es objetivo de esta fase y no está
  demostrado.
- **Exposición por app no garantizada**: WebView/Flutter/`FLAG_SECURE` pueden impedir leer el campo.
- **Fragilidad**: cambios de versión de las billeteras pueden alterar los nodos.
- **Consentimiento**: requiere activación manual; es revocable; en flota se gestiona por MDM.
- **OTP/PIN no-password**: se excluyen los campos `isPassword`; un OTP numérico en un campo común no se
  excluye explícitamente, pero **no** coincide con la lista negra (no genera falso positivo).
- **Deduplicación**: por destinatario en pantalla; cambiar de pantalla rearma.

## Próximos pasos (con autorización)
1. Instalar el APK debug (`pkgout14`), activar el servicio y, en cada billetera, llegar a la pantalla
   de transferencia e inspeccionar (sin ejecutar transferencias) qué nodos expone → completar la tabla
   por app.
2. Probar detección/aviso con datos de la lista negra (sin enviar Telegram real salvo autorización).
3. Según resultados, decidir si alguna app necesita OCR/MediaProjection (con diseño y consentimiento
   previos) o si queda documentada como no cubierta.

## Resultados de la prueba en dispositivo (2026-10-24, Moto G71 ZY22DZ4BHM)
- Serial ZY22DZ4BHM · APK `pkgout15` debug SHA-256 `ec0f1e07b0329ddb280c188d03a9cdeb18f97cd25528b05f5301e5340cd16bba`
  (se agregó un log **solo-debug** de eventos recibidos, SIN contenido de texto, para evidencia por app).
- Servicio habilitado por `settings` (equivalente al toggle de Accesibilidad), visible y revocable.
- **Mercado Pago: DETECTA.** En el campo de destinatario se escribió `patodm.nx`; el servicio recibió
  los eventos y resolvió `Pato Encargado (ALIAS)`. Circuito completo: **1 incidente** (#3, source
  «Accesibilidad · Mercado Pago»), **1 alerta** real a Telegram (message_id 13), cola final **ENVIADO**,
  **sin duplicados**, alarma activada y **detenida** al terminar.
- **Ualá y Brubank: NO VERIFICADO** — ambas en pantalla de login (huella/contraseña del usuario); no se
  ingresó para no usar credenciales. No se evaluó la pantalla de destinatarios.
- Conteos: incidentes 2 → **3** (+1, el de MP); cola 3 → **4** ENVIADO (+1). Sin transferencias reales.
- Preservado: lista negra (`patodm.nx → Pato Encargado`, **no** reemplazada), identidad `Celular Pato`,
  token cifrado, historial y cola. Teclado predeterminado sin cambios (Gboard). Token no apareció en logs.
- **Nota importante**: al hacer `force-stop` para detener la alarma, Android **deshabilita** el servicio
  de accesibilidad (por seguridad); hay que reactivarlo. En operación normal el servicio se detiene desde
  Ajustes de accesibilidad, no con force-stop.
- APK release `pkgout15` SHA-256 `8ee0680c1ca89d9f0f2e02f0f630832ddb7cd6051d74a038df811cb58760b2e9` (SIN FIRMAR).

## Matriz de cobertura — 8 billeteras (en dispositivo, 2026-10-24, Moto G71 ZY22DZ4BHM)
Probado con el dato sintético `patodm.nx` en **modo diagnóstico solo-debug** (registra la coincidencia
pero NO dispara alarma ni Telegram; archivo-bandera `files/a11y_diag`). **Cero alertas** enviadas:
incidentes 3→3, cola 4→4 ENVIADO. Sin confirmar transferencias, sin leer credenciales, sin eludir login.

| # | App | Paquete (verificado) · versión | Estado | Evidencia |
|---|---|---|---|---|
| 1 | **Mercado Pago** | com.mercadopago.wallet · 2.458.2 | **DETECTA** | Campo «alias, CBU/CVU, celular o nombre» accesible; `match → pato-1 (ALIAS)`. (Circuito completo validado antes: incidente+alarma+Telegram msg 13.) |
| 2 | **ARQ** (DolarApp) | com.dolarapp | **DETECTA** | «Añadir destinatario → Argentina CBU/CVU o alias»; `DIAG detecta → pato-1 (ALIAS)` sin alerta. |
| 3 | **Buepp** | com.buepp | **DETECTA** | «Transferir → A un CBU/CVU/Alias»; `DIAG detecta → pato-1 (ALIAS)` sin alerta. |
| 4 | **Lemon** | com.applemoncash · 3.0.20 | **DETECTA** | «Enviar → Pesos → Ingresa CBU, CVU o Alias»; `DIAG detecta → pato-1 (ALIAS)` sin alerta. |
| 5 | **Ualá** | ar.com.bancar.uala · 3.667.0 | **NO VERIFICADO** | Pantalla de login (huella/contraseña). No se ingresó; no se evaluó el campo de destinatario. |
| 6 | **Brubank** | com.brubank · 2.120.0 | **NO VERIFICADO** | Login (huella/clave) + FLAG_SECURE. No se evaluó. |
| 7 | **Personal Pay** | ar.com.personalpay · 3.3.9 | **NO VERIFICADO** | Login (huella). No se evaluó. |
| 8 | **AstroPay** | com.astropaycard.android | **NO VERIFICADO** | Login por PIN. No se ingresó PIN (campo sensible). No se evaluó. |

**Resumen: 4 DETECTA · 4 NO VERIFICADO (todas por login/biometría del propietario).** Las 4 NO
VERIFICADO requieren que el propietario inicie sesión y deje la app en la pantalla de carga de
destinatario; con eso se completa su verificación (misma mecánica que las 4 que sí detectan). **No se
declara cobertura universal**: 4 de 8 verificadas con detección efectiva del campo de destinatario.

Artefactos: APK debug SHA-256 `d9742802665b45f8207ff81c7922f5796175fbf960549161dc8306719a7b868c`;
APK release (SIN FIRMAR) SHA-256 `7536bef01a0dab078c05fd5fa5c1f712c0abf0e87e46371be8f4a43a480a5931`.
Modo diagnóstico: SOLO DEBUG (guardado por `BuildConfig.DEBUG` + archivo-bandera); ausente del release.

## RESULTADO FINAL — 8/8 billeteras DETECTAN (en dispositivo, 2026-10-24, Moto G71 ZY22DZ4BHM)
Todas probadas con el dato sintético `patodm.nx` en **modo diagnóstico solo-debug** (registra la
coincidencia, NO dispara alarma ni Telegram). **Cero alertas nuevas**: incidentes 3→3, cola 4→4 ENVIADO.
Sin confirmar transferencias, sin leer credenciales, sin eludir login (el propietario inició sesión en
las que lo requerían).

| # | App | Paquete · versión | Campo de destinatario | Estado |
|---|---|---|---|---|
| 1 | Mercado Pago | com.mercadopago.wallet · 2.458.2 | «alias, CBU/CVU, celular o nombre» | **DETECTA** ✅ |
| 2 | ARQ (DolarApp) | com.dolarapp | «Argentina · CBU/CVU o alias» | **DETECTA** ✅ |
| 3 | Buepp | com.buepp | «A un CBU/CVU/Alias» | **DETECTA** ✅ |
| 4 | Lemon | com.applemoncash · 3.0.20 | «Ingresa CBU, CVU o Alias» | **DETECTA** ✅ |
| 5 | Personal Pay | ar.com.personalpay · 3.3.9 | campo de transferir | **DETECTA** ✅ |
| 6 | Ualá | ar.com.bancar.uala · 3.667.0 | buscador de destinatario | **DETECTA** ✅ |
| 7 | Brubank | com.brubank · 2.120.0 | «Alias, CBU, CVU o Correo» | **DETECTA** ✅ |
| 8 | AstroPay | com.astropaycard.android | «CBU/CVU/Alias» | **DETECTA** ✅ |

En las 8, el servicio recibió el evento del campo de destinatario (`TYPE_VIEW_TEXT_CHANGED`) y resolvió
`patodm.nx → Pato Encargado (ALIAS)` (`DIAG detecta … persona=pato-1`). Evidencia: logs `SentinelA11y`
por paquete, con el texto confirmado en el campo. **Circuito completo de extremo a extremo** (incidente
+ alarma + Telegram) validado una vez en Mercado Pago (incidente #3, message_id 13); el resto se validó
en diagnóstico para no repetir alertas al grupo.

**Honestidad / límites**: esto es **detección y aviso**, NO bloqueo (no impide la transferencia).
Ninguna de las 8 usó WebView que impidiera leer el campo; Brubank usa FLAG_SECURE (bloquea capturas)
pero **sí** expone el campo a accesibilidad. La conexión USB se cortó un par de veces durante las
pruebas (reintentos); los resultados se confirmaron tras reconectar.
