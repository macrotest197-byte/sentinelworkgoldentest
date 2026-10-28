# S.E.N.T.I.N.E.L. — Informe de desarrollo

Herramienta de **detección y alerta** para un celular corporativo de recaudación.
Detecta la aparición de valores de una lista negra configurable en **fuentes de
detección autorizadas** (contenido propio, pantallas de prueba o fuentes expresamente
habilitadas) y genera una alerta local + notificación a Telegram. **No** bloquea
transferencias ni modifica aplicaciones bancarias, y **no** extrae silenciosamente el
contenido de apps de terceros.

## Estado por fases

| Fase | Contenido | Estado |
|------|-----------|--------|
| 1 | Proyecto Android + interfaz + lista negra persistente (Room) | ✅ Compila y pruebas OK |
| 2 | Alarma local (sonido + vibración) y pantalla de alerta | ✅ Compila y pruebas OK |
| 3 | Integración Telegram (token seguro, destinos, prueba) | ✅ Compila y pruebas OK |
| 4 | Historial + CSV + aprovisionamiento de reglas | ✅ Compila y pruebas OK |
| 5 | Detección (matcher de personas) + circuito de alertas | ✅ Lógica verificada por tests; E2E en dispositivo pendiente |
| 6 | Identidad de dispositivo + admin de solo lectura | ✅ Implementado y testeado (parseo) |
| 7 | Entrega, APK y documentación | ✅ Ver ENTREGA_SENTINEL.md |

> Detalle completo y honesto de lo terminado/pendiente: **`ENTREGA_SENTINEL.md`**.

## Cierre integral (2026-10-22)

- **Empaquetado resuelto sin `clean` ni tocar el junction.** El bloqueo intermitente de
  `packageDebug` ("Failed to clean up output files") ocurría al **borrar el APK previo** en
  `build3\outputs` (handle on-access, probablemente Defender; no confirmado con handles). 
  **Solución aplicada:** compilar a un **directorio de salida aislado y fresco** vía
  `--init-script` (sin salida previa que borrar → sin bloqueo), preservando el junction
  `app\build → C:\SentinelBuild\build3`.
- **APK nuevo (con entrada de texto consentida):** `C:\SentinelBuild\pkgout3\app\outputs\apk\debug\app-debug.apk`
  (2026-10-22 08:54:24 · 19.356.424 bytes · SHA256 `B47FD90EF53FED92C05C63BA4B2CB1C7315C4D9BFDFED2AA58146370598D36A2`).
- **Pruebas:** `testDebugUnitTest` → **36 pruebas, 0 fallos**.
- **Entrada de texto:** método consentido por el operador (`ACTION_PROCESS_TEXT` + Compartir) → matcher → detección, con dedup. Evaluación de Accessibility/OCR/integración directa y límites en **`ENTRADA_TEXTO.md`**.
- **Correcciones concretas:** dedup de eventos (`EventDeduplicator` + `ConfirmedAlertProcessor`),
  guarda de nombre previo en `TextNormalizer`, `this@SentinelApp`, import duplicado de `Severity`.
- **Entrega completa documentada en `ENTREGA_SENTINEL.md`** (funciones, tests, APK, limitaciones,
  qué falta para producción, pasos de instalación por celular y Android Enterprise anti-desinstalación).

## Teclado de prevención integrado (Opción B, 2026-10-22)

Integrado **selectivamente** en el proyecto canónico (sin mezclar el checkout paralelo de
Codex), reutilizando los componentes originales:
- `input/SentinelInputMethodService.kt` — IME opt-in. Usa `BlacklistPersonMatcher`,
  `ConfirmedAlertProcessor` (incidente+alarma+Telegram+dedup) y la identidad del dispositivo.
  Al detectar un destinatario de la lista negra en un campo **marcado**: avisa fuera de las
  teclas, **limpia el dato** (prevención best-effort) y dispara el circuito. Dedup por
  episodio + rearmado al desaparecer el dato.
- `detection/InputFieldPolicy.kt` — **fail-closed**: solo inspecciona campos marcados por la
  app dueña; excluye contraseñas/PIN/OTP/autocompletado. No guarda texto ni teclas.
- `ui/input/KeyboardSetupScreen.kt` + `KeyboardPlaygroundActivity.kt` — activación y campo
  de demo (sin alarma/Telegram).
- Backup verificable del canónico antes de tocar: `C:\SentinelBuild\backup-canonico-20261022-120057`.
- **Pruebas:** baseline 36 → **43 pruebas, 0 fallos** (`InputFieldPolicyTest` +7). Sin cambios
  de esquema Room (datos y migraciones preservados).
- **APK (con teclado):** `C:\SentinelBuild\pkgout4\app\outputs\apk\debug\app-debug.apk`
  (2026-10-22 12:14 · 19.374.079 bytes · SHA256 `B94472CF4C61A792E21647A954D521E5CBA967B88E275297568BF893CBB94028`).
- **Alcance real (verificar en dispositivo):** el teclado **solo** cubre apps que integran el
  marcador de SENTINEL. **Mercado Pago, Ualá, Brubank y otras billeteras de terceros NO lo
  integran → quedan fuera.** Matriz y niveles de protección en `ENTREGA_SENTINEL.md`.

## Ubicación definitiva

- Proyecto: `C:\Users\diazm\OneDrive\Documents\SENTINEL`
- APK debug: `app\build\outputs\apk\debug\app-debug.apk`
- `app\build` es un junction que apunta a `C:\SentinelBuild\build3`; conservarlo.
  No ejecutar `gradlew clean` ni borrar `app\build`.

## Fase 2 — alarma local (nuevo)

- `alarm/AlarmController.kt`: sonido en bucle (stream de alarma) + vibración intensa
  repetida (waveform con amplitud máxima). Singleton de proceso: **una sola alarma
  simultánea** (si ya hay una activa, no arranca otra).
- `alarm/AlarmService.kt`: servicio en **primer plano** (tipo `specialUse`) que mantiene
  la alarma viva cuando la app pasa a segundo plano, con notificación persistente.
- `alarm/AlarmNotifications.kt`: canal `IMPORTANCE_HIGH`, notificación `PRIORITY_MAX`
  categoría `ALARM`, con **full-screen intent** y acción **RECONOCER**.
- `alarm/AlertActivity.kt`: pantalla de alerta a pantalla completa, se muestra **sobre la
  pantalla de bloqueo** (`showWhenLocked`/`turnScreenOn`); botón **RECONOCER Y DETENER**
  corta sonido + vibración. El botón atrás no descarta la alerta.
- `ui/alarm/AlarmTestScreen.kt`: prueba manual desde la app (botón Probar / Detener),
  pide el permiso `POST_NOTIFICATIONS` en Android 13+, muestra estado activo/detenido.
- Ciclo de vida: `stop()` libera MediaPlayer, cancela la vibración y **restaura el
  volumen** previo del stream de alarma.

### Limitaciones reales de la alarma (verificadas en el diseño, no se prometen imposibles)

- **No molestar (DND):** el canal pide `setBypassDnd(true)`, pero sólo surte efecto si el
  usuario concede a SENTINEL el acceso a No molestar en los ajustes del sistema. No se
  puede forzar universalmente el silencio del usuario.
- **Vibración:** intensidad y duración dependen del hardware; algunos equipos las limitan.
- **Segundo plano:** en Android 12+ un servicio en primer plano no puede arrancarse estando
  la app en segundo plano salvo excepciones; por eso la prueba se lanza con la app abierta.
  Cuando exista el motor de detección (Fase 5) se arrancará desde un contexto permitido.
- **Permiso de notificaciones:** en Android 13+ es necesario concederlo; sin él, la
  notificación de alta prioridad y la pantalla completa pueden no mostrarse.

## Fase 3 — Telegram (nuevo)

- `data/settings/SecureSettingsStore.kt`: token e IDs cifrados con **EncryptedSharedPreferences
  (Android Keystore)**. El token nunca se escribe en logs ni en el código.
- `telegram/TelegramClient.kt`: envío real vía API de bots (OkHttp). `parseResponse` es una
  función pura (testeada) que interpreta `ok`/`description` sin exponer el token.
- `telegram/TelegramRepository.kt`: destinos (grupo / privado) habilitables, **envío de
  prueba** con resultado real por destino, **cola de pendientes** en Room, **reintentos
  controlados** (hasta 5, sólo errores de red son reintentables), **deduplicación** por
  `dedupKey` dentro de una ventana de 2 min, y **registro real** de cada envío.
- `telegram/TelegramSendWorker.kt`: WorkManager con backoff exponencial y espera de red
  para vaciar la cola en segundo plano.
- `ui/telegram/TelegramScreen.kt`: configuración en español, token oculto con ojo para
  mostrar, IDs de grupo/privado, switches por destino, validación, botón de prueba,
  estado del último envío, contador de pendientes y ayuda de BotFather.
- **La alarma local NO depende de Telegram**: un fallo de red encola y reintdenta aparte.

Pruebas (`TelegramClientTest`, 6): ok=true→Success, ok=false→ApiError con descripción,
JSON inválido con 200 y con error HTTP, error de red reintentable, y que el mensaje de
alerta no incluye saldos/contraseñas/token y sí los campos requeridos.

## Fase 4 — Historial e importación/exportación CSV (nuevo)

- `data/db/IncidentEntity.kt` + `IncidentDao`: **historial persistente** (DB v3). Campos:
  fecha/hora, dispositivo, tipo de coincidencia, valor, severidad, fuente, app, estado de
  alarma y estado de Telegram. El valor se muestra parcialmente oculto en la interfaz.
- `ui/history/HistoryScreen.kt`: lista, **búsqueda**, **filtros** por severidad y
  **exportación CSV** (selector de archivos del sistema, UTF-8).
- `csv/Csv.kt`: parser/serializador CSV correcto (comillas, comas y saltos internos, quita BOM).
- `csv/BlacklistCsv.kt`: parser, validador, plantilla y exportación. El CSV administrativo
  puede incluir `persona_id`, `nombre_principal` y `variantes` para agrupar registros.
- `ui/importcsv/ImportScreen.kt`: exportar copia y descargar plantilla; no permite al
  operador importar ni alterar la lista.
- `csv/BlacklistProvisioning.kt`: carga administrativa desde
  `Android/data/com.sentinel.recaudacion.debug/files/sentinel_blacklist_provision.csv` en
  debug (`com.sentinel.recaudacion/files/` en release). Valida antes de aplicar y conserva
  las reglas omitidas; un registro existente se actualiza por tipo y valor normalizado.
- El historial se alimenta de eventos reales. Hoy se registra un incidente al hacer la
  **prueba de alarma** (fuente "PRUEBA MANUAL"); con el motor de detección (Fase 5) se
  registrarán las coincidencias reales. La pantalla de Inicio muestra el último evento real.

Pruebas anteriores de `CsvTest`: parseo, validación, duplicados y exportación. Se añadieron
casos de agrupación por persona y variantes; deben ejecutarse junto con el resto del suite.

## Telegram fijado y protegido (configuración no editable por el operador)

Se separó la **configuración autorizada** (que fija el administrador) de la **interfaz del
operador**:

- `ui/telegram/TelegramScreen.kt` ahora es **solo lectura**: muestra estado (configurado o
  no), token **enmascarado**, destinos autorizados y pendientes, y conserva el botón
  **Enviar prueba**. **No** hay campos para editar token/IDs/destinos ni botón Guardar.
- `telegram/AuthorizedDestinations.kt`: **fuente única** que resuelve a qué chats se envía,
  **solo** desde la configuración autorizada. Ni una pantalla ni la base de datos pueden
  introducir destinos arbitrarios: lo que no está aquí, no se envía.
- `telegram/TelegramProvisioning.kt`: el administrador coloca un archivo JSON
  (`sentinel_telegram_provision.json`) en el directorio privado de la app; al iniciar,
  SENTINEL lo valida, lo guarda **cifrado** (EncryptedSharedPreferences / Keystore) y
  **borra el archivo**. Así el token y los destinos **no** viven en el código, recursos ni APK.
- El token **nunca** se registra: `TelegramClient.redactSecrets()` borra cualquier token que
  pudiera aparecer en un mensaje de error de red (algunas IOException incluyen la URL).
- Se conservaron cola de pendientes, reintentos, deduplicación y pruebas.
- La **alarma local** sigue funcionando sin conexión e independiente de Telegram.

### Pasos de configuración (administrador)

1. Crear el bot con @BotFather y obtener el token.
2. Confirmar el **ID del grupo operativo autorizado** (no se deduce del enlace de invitación;
   usar @getidsbot o getUpdates con el bot ya en el grupo).
3. Copiar `telegram-provision.example.json` como `sentinel_telegram_provision.json`,
   completar `token` y `groupChatId` reales, y colocarlo por USB/adb o gestor de archivos en:
   `Android/data/com.sentinel.recaudacion.debug/files/sentinel_telegram_provision.json`
   (en la APK release el sufijo `.debug` no está).
4. Abrir SENTINEL: importa la configuración, la cifra y **borra** el archivo. La pantalla de
   Telegram mostrará "configurado" y el destino autorizado. Probar con "Enviar prueba".

### Limitación de seguridad (extracción del APK) y evaluación de backend

- `EncryptedSharedPreferences` protege el token **en reposo** en el dispositivo (respaldado
  por Keystore). Es adecuado para un dispositivo corporativo controlado.
- **Pero** cualquier secreto que la app pueda descifrar para usarlo es, en teoría,
  **extraíble por alguien con acceso físico/root** al dispositivo. Esto es inherente a toda
  app local; no lo resuelve ni ofuscación ni cifrado local.
- Si se requiere proteger el token frente a extracción del APK/dispositivo, la **única**
  solución real es un **backend**: el token vive en el servidor; la app llama al backend, que
  **valida el destino permitido** y reenvía a Telegram. La app nunca contiene el token y no
  puede enviar a destinos arbitrarios. Queda **evaluado y recomendado** para ese escenario;
  **no implementado** en esta versión local-first (no se pidió servidor todavía).

Pruebas (`TelegramSecurityTest`, 6): el error de red **no revela el token** (redacción),
los destinos se resuelven **solo** desde la config autorizada (privado deshabilitado no
aparece; sin ID no hay destino), y el aprovisionamiento acepta JSON válido y **rechaza**
sin token o sin destino habilitado. **Total del proyecto: 22 pruebas, 0 fallos.**

## Nota de compilación: Android Studio abierto / `gradlew clean` y el junction

**IMPORTANTE — no uses `gradlew clean` en este proyecto.** La tarea `clean` borra el
directorio `app\build`, y como ese directorio **es el junction**, lo destruye (queda como
carpeta normal dentro de OneDrive y vuelven los bloqueos de sincronización). Si alguna vez
necesitás un build limpio, **reapuntá el junction a una carpeta de salida vacía** en vez de
`clean`:

```
rmdir /S /Q "C:\Users\diazm\OneDrive\Documents\SENTINEL\app\build"
mkdir "C:\SentinelBuild\buildN"   &   mklink /J "C:\Users\diazm\OneDrive\Documents\SENTINEL\app\build" "C:\SentinelBuild\buildN"
gradlew.bat assembleDebug testDebugUnitTest
```

Además, si Android Studio está **abierto**, su indexador puede retener archivos de
`app\build` y hacer fallar un build por **línea de comandos**. En ese caso: compilá
**desde Android Studio** (Build/Run), o cerralo mientras compilás por CLI. Las builds por
CLI de este proyecto se hacen **sin `clean`**, con el junction apuntando a un directorio de
salida fresco fuera de OneDrive (así se compiló y verificó la Fase 4).

## Incremento de seguridad y comparación (21/10/2026)

- `detection/TextNormalizer.matches`: comparación de alta precisión preparada para una
  fuente autorizada futura. Nombres exigen nombre y apellido exactos luego de normalizar
  mayúsculas, acentos y espacios; aceptan nombres intermedios adicionales e iniciales.
  Alias/códigos usan límites de token, nunca subcadenas arbitrarias. CBU/CVU/DNI/cuenta
  comparan dígitos exactos; en textos largos aceptan el identificador completo (mínimo
  seis dígitos). Casos positivos y negativos están en `TextNormalizerTest`.
- La app todavía no ejecuta un motor de detección real. **Prueba de texto** es una
  simulación dentro de SENTINEL: no crea incidentes ni genera envíos Telegram.
- El comparador devuelve confianza conservadora: nombres exactos 100%, variantes con
  segundo nombre/inicial 96%, alias embebidos 99% e identificadores embebidos completos
  (mínimo seis dígitos) 99%. Umbral mínimo: 95%. La simulación también compara con Enter.
- `ConfirmedAlertProcessor` procesa una coincidencia confirmada entregada por una fuente
  autorizada: solicita la alarma local, encola Telegram con el ID persistente del celular y
  guarda persona, tipo, valor, severidad, confianza, app/fuente y hasta 240 caracteres del
  fragmento autorizado en el historial. La exportación CSV incluye identidad del celular,
  persona y confianza, pero omite el fragmento. No tiene un llamador de producción todavía;
  la pantalla simulada no lo invoca ni envía alertas.
- Las reglas admiten `persona_id`, `nombre_principal` y `variantes`; `BlacklistPerson`
  agrupa reglas de nombre/alias/CBU/CVU/DNI del mismo sujeto. La plantilla y exportación
  CSV incluyen estas columnas, y la simulación usa el matcher agrupado.
- El administrador puede colocar `sentinel_blacklist_provision.csv` en el directorio
  privado de archivos de la app. SENTINEL valida el archivo antes de escribir, agrega o
  actualiza por tipo + valor normalizado, permite desactivar una regla con `activo=no` y
  borra el archivo solo tras importarlo correctamente. Las reglas omitidas se conservan;
  no se reemplaza ni borra toda la lista.
  No existe botón de importación para el operador. Esta vía exige acceso administrativo al
  dispositivo y no sustituye Android Enterprise.
- El nombre se aprovisiona por separado en `sentinel_device_provision.json` con
  `{"deviceName":"Recaudación 01"}`; puede cambiarse sin tocar Telegram. El ID de
  instalación no se acepta desde este archivo ni se modifica desde la UI. El archivo se
  borra después de aplicarlo.
- Identidad de instalación persistente UUID y nombre descriptivo del administrador en
  `DeviceIdentityStore` (ejemplo en `device-provision.example.json`). Si no se asignó un
  nombre, se muestra `Celular <id corto>`. El historial guarda la identidad de instalación
  para eventos nuevos. Migraciones Room v3→v7 agregan identidad
  al historial y outbox, metadatos de persona y fragmento/confianza sin borrar los datos
  existentes.
- Telegram ahora usa el texto solicitado: “Se detectó un intento de transferencia a
  [persona] desde el celular [nombre].”
- **Auditoría del celular:** Telegram ya era de solo lectura; token y destinos llegan por
  aprovisionamiento de administrador. La lista negra podía agregarse, editarse,
  activarse/desactivarse, eliminarse e importarse por CSV desde el teléfono. Se retiraron
  esos controles de las pantallas normales; la exportación CSV permanece y el administrador
  usa el aprovisionamiento privado descrito arriba. Es una
  restricción de interfaz, no una defensa contra root/ADB o desinstalación.
- Para impedir desinstalación o cambios de ajustes del SO hace falta Android Enterprise
  con un EMM/DPC y dispositivo totalmente administrado (device owner). Las políticas
  pueden bloquear la desinstalación; lock task mode es para equipos dedicados/kiosco. La
  inscripción se prepara como parte del enrolamiento inicial y puede requerir restablecer
  el equipo: no se hizo inscripción ni se agregaron políticas destructivas. Fuentes:
  [Android device control](https://developer.android.com/work/dpc/device-management),
  [fully managed policies](https://developers.google.com/android/management/policies/fully-managed-devices),
  [lock task mode](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode).

### Verificación del incremento

- Se actualizaron pruebas unitarias de nombres, falsos positivos, identificadores y
  formato Telegram; se intentó ejecutar `testDebugUnitTest` y `assembleDebug`.
- No llegó a iniciar Gradle: Windows denegó acceso al lock del wrapper en
  `%USERPROFILE%\.gradle\wrapper\dists\gradle-8.9-bin\...gradle-8.9-bin.zip.lck`.
  Se detuvo ahí, sin tocar el caché ni `app\build`. Antes del intento se verificó que el
  junction siguiera apuntando a `C:\SentinelBuild\build3`. La compilación y pruebas quedan
  pendientes de reintento cuando el wrapper sea accesible.

## Fase 1 — lo que funciona hoy

- Interfaz en español (Jetpack Compose) con Inicio y las 7 secciones.
  Las secciones aún no implementadas se muestran como **pendientes con su fase**,
  no como funciones terminadas.
- **Lista negra con Room**: consulta y búsqueda desde la UI del operador; edición por
  aprovisionamiento administrativo. Campos por regla: tipo, valor, descripción, severidad,
  estado y asociación opcional con persona.
- Normalización controlada para la futura comparación (alias/nombre: minúsculas,
  acentos y espacios; CBU/CVU/documento: solo dígitos, sin alterar el número).
- Detección de duplicados por tipo + valor normalizado al guardar.
- Ícono sobrio (escudo con "S"); la finalidad de supervisión es transparente.

## Pruebas realizadas (reales)

- `testDebugUnitTest`: **5 pruebas, 0 fallos** (`TextNormalizerTest`):
  mayúsculas/espacios en alias, acentos en nombres, CBU con separadores,
  CVU distintos no coinciden, documento con puntos.
- `assembleDebug` (Fase 1 y Fase 2): **BUILD SUCCESSFUL** desde la ubicación
  definitiva en OneDrive. APK generado.
- **Prueba en vivo de la alarma (arranque/detención en dispositivo): PENDIENTE.**
  No hay emulador ni dispositivo conectado (no hay system-images instaladas). La lógica
  de alarma no se puede validar con pruebas unitarias JVM (depende de audio/vibración
  del sistema); requiere prueba manual en un equipo o emulador con la pantalla
  "Prueba de alarma".

## Toolchain verificado

- JDK 21 (`~/.jdks/jbr-21.0.11`) · Android SDK en `%LOCALAPPDATA%\Android\Sdk`
- AGP 8.7.3 · Gradle 8.9 · Kotlin 2.0.21 · compileSdk 35 · minSdk 26

## Cómo compilar

```bash
# Con JAVA_HOME apuntando a un JDK 17 o 21:
gradlew.bat assembleDebug        # genera el APK debug
gradlew.bat testDebugUnitTest    # corre las pruebas unitarias
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`

## Limitaciones conocidas

- Todavía **no hay fuente de detección activa** (Fase 5): la pantalla de Inicio lo
  informa como "REQUIERE ATENCIÓN" en lugar de aparentar que monitorea.
- Telegram, historial e importación CSV son placeholders hasta sus fases.
- La alarma (Fase 2) está implementada y compila, pero su funcionamiento en vivo
  (sonido, vibración, pantalla sobre el bloqueo) todavía no se probó en un equipo real.
- En Windows, evitá rutas de proyecto muy largas (límite de 260 caracteres). La ruta
  actual en `Documents\SENTINEL` es corta y compila sin problema.

## Bloqueo de compilación por OneDrive — causa y solución (RESUELTO)

**Síntoma:** Gradle/Android Studio falla con
`Unable to delete directory '...\app\build\intermediates\...'`.

**Causa real:** la carpeta del proyecto está dentro de OneDrive. OneDrive intenta
sincronizar en tiempo real los archivos que Gradle genera en `app\build` (dex,
intermediates, APK). Mientras OneDrive mantiene un archivo abierto para subirlo, Gradle
no puede borrarlo/reemplazarlo y aborta. Un daemon de Gradle vivo agrava el bloqueo.

**Solución aplicada (durable, sin tocar el código ni las fuentes):**

1. Se detuvo el daemon de Gradle del proyecto de forma controlada (`gradlew --stop`),
   sin cerrar Android Studio ni procesos ajenos.
2. Se limpió únicamente `app\build`.
3. Se convirtió `app\build` en un **junction** que apunta fuera de OneDrive:

   ```
   C:\Users\diazm\OneDrive\Documents\SENTINEL\app\build  -->  C:\SentinelBuild\build
   ```

   OneDrive **no sincroniza reparse points** (junctions), por lo que ya no bloquea los
   temporales de compilación. La ruta oficial del APK **no cambia**: sigue siendo
   `app\build\outputs\apk\debug\app-debug.apk` (resuelve a través del junction).
   El código fuente y la configuración siguen en OneDrive, sincronizados normalmente.

**Resultado:** `assembleDebug` + `testDebugUnitTest` → BUILD SUCCESSFUL, 5/5 pruebas OK.

**Si alguna vez se rompe el junction** (p. ej. al borrar `app\build` desde el Explorador
o un "Invalidate Caches"), recrearlo con (CMD, sin admin):

```
rmdir /S /Q "C:\Users\diazm\OneDrive\Documents\SENTINEL\app\build"
mkdir "C:\SentinelBuild\build"
mklink /J "C:\Users\diazm\OneDrive\Documents\SENTINEL\app\build" "C:\SentinelBuild\build"
```

Alternativa sin junction: pausar la sincronización de OneDrive mientras se compila.

---

## 2026-10-23 — Persona como unidad primaria, identidad de dispositivo y alerta nueva

Ver `PROCEDIMIENTO_PERSONAS.md` para el procedimiento completo (CSV, identidad, formato).

- `BlacklistPersonMatcher.resolve(text, persons)` → `ResolvedMatch(matchType, matchedValue,
  confidence, severity, personId?, personName?, ambiguous)`. Detecta ambigüedad (mismo
  identificador en >1 persona) y no elige arbitrariamente.
- `AlertMessage.alert(device, matchedValue, personName, typeLabel, now)` genera el formato 🚨.
  `UNRESOLVED` = "Sin resolver"; `AMBIGUOUS` = "Sin resolver (asociación ambigua)".
- `ConfirmedAlertProcessor.process(app, resolved, source, eventKey, …)` mantiene dedup, alarma,
  cola Telegram con reintentos e historial (`IncidentRepository.logConfirmedMatch` con
  personId/personName/matchType/matchedValue/confianza/severidad y la identidad del dispositivo).
- `DeviceIdentityStore`: id estable, nombre de administrador, `status`, `registeredAt`,
  `updatedAt`, `appVersion` (sincronizada en `SentinelApp.onCreate`). Sin base de datos nueva ni
  servidor; se persiste en SharedPreferences `sentinel_device`.
- Room: **sin cambios de esquema** (se reutilizan columnas de persona ya presentes en
  `BlacklistEntity`/`IncidentEntity`); no se reinicia ni se pierde el historial.
- Pruebas verdes: `PersonResolutionTest`, `CsvTest`, `TelegramClientTest`, `DetectionPipelineTest`.
- APK `app-debug.apk` SHA-256 `f6ece917500871dd585187cb3f7a07e8dcac4b3bd071c3f70245195929cc32c8`.
- Build con JDK 21 (`C:\Users\diazm\.jdks\jbr-21.0.11`) + init-script `reloc7.init.gradle.kts`
  (la JBR de Android Studio quedó en JDK 25, incompatible con Gradle 8.9). Sin `gradlew clean`.

### 2026-10-23b — Vista previa de alerta en «Verificación de texto»

`EvaluateTextActivity` ahora muestra una **vista previa local** de la alerta usando el modelo
real: `BlacklistPersonMatcher.resolve()` + `DeviceIdentityStore.snapshot()` +
`AlertMessage.alert()` (el **mismo generador** del circuito real; no se duplica el formato). Muestra
dispositivo, dato detectado, persona, tipo y fecha/hora local. Si la persona no resuelve →
«Sin resolver»; si es ambigua → «Sin resolver (asociación ambigua)»; si el dispositivo está sin
registrar → se indica el nombre provisional. Es estrictamente local: la resolución es pura (no toca
el deduplicador real) y **no** crea incidente, **no** activa alarma, **no** encola ni envía Telegram.
- APK: `C:\SentinelBuild\pkgout8\app\outputs\apk\debug\app-debug.apk`
  SHA-256 `86c6ab425f09092de192c09670aa84b94371431174616b9f00994fcb1550b28a`.
- Pruebas: 61 tests, 0 fallos, 0 errores (`testDebugUnitTest`). Sin cambios de esquema/migraciones.

### 2026-10-23c — Prueba final integral del circuito real (pkgout9)

**Disparador de prueba aislado (solo debug)**: `app/src/debug/.../RealAlertTestActivity.kt`
(+ `app/src/debug/AndroidManifest.xml`, `android:exported="false"`). NO existe en el APK release.
Se accede solo desde un ítem **solo-debug** en la pantalla principal (`HomeScreen`, guardado por
`BuildConfig.DEBUG`, lanzado por `ComponentName` sin dependencia de compilación). Pide **confirmación
explícita** dentro de la app y tiene **guardia de ejecución única**. Invoca el `ConfirmedAlertProcessor`
**real** (sin pipeline paralelo): matcher → persona → identidad → dedup → historial → alarma →
cola Telegram → envío. `TelegramClient.sendMessage` registra el `message_id` **solo en debug**
(dato público; el token va en la URL, nunca en el body ni en el log).

**Resultado de la prueba única** (regla real `patodm.nx`, dispositivo `Celular Pato`):
incidente #1 creado; alarma SOLICITADA (activada y detenida tras verificar); cola → **2 ENVIADO,
0 PENDIENTE**, sin duplicados (`dedupKey pato-1|ALIAS|patodm.nx` una sola vez); Telegram API
**ok, message_id=11**. Texto enviado con los 5 campos correctos. La recepción visual en el grupo
la confirma el operador. Datos preservados (lista negra, identidad, token cifrado); Gboard sin cambios.
- APK: `C:\SentinelBuild\pkgout9\app\outputs\apk\debug\app-debug.apk`
  SHA-256 `f19f893237399c3d3e33d04f11e3fe790c9f7b2e605f01678616ab38784be55a`.
- Pruebas: 61 tests, 0 fallos, 0 errores. Sin cambios de esquema/migraciones.
- Limitación: esta prueba valida el circuito de SENTINEL; NO demuestra cobertura automática de
  Mercado Pago, Ualá, Brubank u otras billeteras (requieren control complementario).

### 2026-10-23d — Cierre de versión candidata a producción (pkgout10)

**Rediseño del teclado SENTINEL** (vistas nativas del IME, conservando el diseño actual):
- Barra de herramientas compacta sobre las teclas: **Pegar** (portapapeles; sin historial),
  **sugerencias** (3 chips) y acceso rápido a **cambiar de teclado** (🌐), más «Cambiar teclado» inferior.
- **Sugerencias/autocorrección locales** (`SpanishSuggester`): diccionario estático de español;
  **no** almacena lo escrito ni registra teclas. Se aplican **solo al tocar** una sugerencia (nunca
  automático), evitando reemplazos inesperados. No sugiere sobre datos (dígitos, `.`, `@`) ni propone
  identificadores de lista negra. Excluidas en contraseña/PIN/OTP/email/URI/numérico/`NO_SUGGESTIONS`
  (`InputFieldPolicy.suggestionsAllowed`).
- **Pegado**: inserta el contenido del portapapeles solo cuando el usuario toca Pegar; la evaluación
  de detección se dispara sola vía `onUpdateSelection` (no depende de Enter). Sin bucles ni borrados
  accidentales (la inspección usa `KeyboardEpisodeTracker`; el bloqueo solo limpia en coincidencia real).
- **Estado de detección**: aviso de coincidencia sobre las teclas (no tapa el campo); al estar activo,
  se ocultan las sugerencias.

**Separación debug/producción**: la herramienta de prueba real (`RealAlertTestActivity` + manifest)
vive en `src/debug` y **no aparece** en el manifest release (verificado). El botón de acceso está
guardado por `BuildConfig.DEBUG`; el log de `message_id` también. Sin componentes exportados.

**Build**: `assembleRelease` con R8/minify + shrinkResources. Se añadieron reglas ProGuard
(`-dontwarn com.google.errorprone.annotations.**` por Tink; `-keep` del Worker, IME y Application).

**Verificación**:
- Pruebas: **73 automatizadas, 0 fallos** (JDK 21) — incluye `SpanishSuggesterTest` y sugerencias en
  `InputFieldPolicyTest`.
- APK release (sin firmar): SHA-256 `4efb5971551963094d12764fb09736f76ef450b51d0624af2d9ffa6c59f6060a`.
- APK debug: SHA-256 `556de0413c4632c6a43cb5f08523d87a68cc1da7eb5cc6622c3dd909d3394572`.
- Permisos release: INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE(+SPECIAL_USE), POST_NOTIFICATIONS,
  VIBRATE, WAKE_LOCK, RECEIVE_BOOT_COMPLETED (todos justificados; sin permisos peligrosos extra).
- **No** se instaló en el teléfono ni se cambió el teclado predeterminado en esta tarea.

**Limitación clave (ver `COBERTURA.md`)**: la detección **no** es universal con un IME normal.
Solo se inspeccionan campos marcados (app propia o apps integradas). Billeteras de terceros
(Mercado Pago, Ualá, Brubank): escritura sí, inspección/prevención **no probadas y sin evidencia**.
El teclado nuevo (barra, pegar, sugerencias) está verificado por compilación + pruebas JVM; su
comportamiento en dispositivo **no** se probó en esta tarea (no se instaló).

**Archivos modificados/creados**:
- `input/SentinelInputMethodService.kt` (barra, pegar, sugerencias, gating de sensibles)
- `input/SpanishSuggester.kt` (nuevo)
- `detection/InputFieldPolicy.kt` (`suggestionsAllowed`)
- `telegram/TelegramClient.kt` (log de message_id solo-debug)
- `src/debug/...RealAlertTestActivity.kt` + `src/debug/AndroidManifest.xml` (nuevos, solo debug)
- `ui/home/HomeScreen.kt` (acceso debug guardado)
- `proguard-rules.pro` (reglas release)
- Pruebas: `SpanishSuggesterTest.kt` (nuevo), `InputFieldPolicyTest.kt` (ampliado)
- Docs: `COBERTURA.md`, `GUIA_OPERACION.md` (nuevos), este informe, `ENTREGA_SENTINEL.md`

### 2026-10-23e — Barra inteligente contextual (pkgout11)

La zona central de la barra del teclado ahora se comporta como un teclado Android convencional:
**no** muestra Pegar y sugerencias a la vez. Decisión en función pura testeable
(`KeyboardToolbarPlanner`), reutilizando `SpanishSuggester` y la política de campos (sin segundo
sistema).

- **Prioridad 1 — portapapeles con texto**: 📋 **Pegar centrado**, sugerencias ocultas. La
  disponibilidad se consulta por la **descripción** del portapapeles (`primaryClipDescription`),
  sin leer el contenido ni disparar el aviso de acceso; el contenido se lee solo al tocar Pegar.
- **Prioridad 2 — sin texto pegable**: hasta **3 sugerencias** (centro = la más probable, luego
  izquierda y derecha). Se aplican solo al tocar (nunca automático). Sin sugerencias útiles →
  barra limpia (no se inventan).
- **Prioridad 3 — campo sin sugerencias/sensible**: nunca sugerencias (respeta `NO_SUGGESTIONS`,
  contraseña, PIN, OTP, email, URI, numérico); Pegar puede aparecer si hay portapapeles.
- **Visual**: zona de 3 posiciones de igual tamaño (altura estable; alternar no mueve las teclas);
  🌐 separado a la derecha; aviso rojo de detección intacto. Diseño/teclas/distribución sin cambios.
- No se tocó el modelo de personas, Room, migraciones, identidad, historial, alarma, cola ni
  Telegram. El disparador de prueba sigue solo en `src/debug` (ausente del release, verificado).

**Verificación**: 80 pruebas automatizadas, 0 fallos (incluye `KeyboardToolbarPlannerTest`).
- APK debug: SHA-256 `878074adc534cfff105170ee0c9bc6b9aa0f7c1cf3ebaf70bbb9cb3ca239db41`.
- APK release (sin firmar): SHA-256 `bfcdbda416501c185ce166991288709720809b13244a9e2aa67751bec256598c`.
- Permisos release sin cambios (mínimos). Build con JDK 21 + init-script (sin `clean`, junction intacta).
- Validación en dispositivo: **pendiente** (el Moto G71 se desconectó durante la sesión).

### 2026-10-23f — Auditoría de producción y separación pruebas/corporativo (pkgout12)

Ver `AUDITORIA_PRODUCCION.md` (9 respuestas + matriz de cobertura) y `ALTA_DISPOSITIVO.md`.
- **Separación por build**: `BuildConfig.TEST_TOOLS` (debug=true/release=false) + placeholder de
  manifest `testToolsEnabled`. En release se ocultan «Prueba de alarma/texto» y `KeyboardPlaygroundActivity`
  queda `android:enabled="false"` (verificado en manifest fusionado). `RealAlertTestActivity` ausente.
- **Auditoría**: lista negra y Telegram son **solo lectura** en la UI; no hay renombrado de dispositivo
  por UI. Gap real: aprovisionamiento por carpeta externa (`getExternalFilesDir`) escribible → debe
  restringirse por MDM; falta autoridad central (no se simuló). TELÉFONO **no implementado**.
- **Release SIGUE SIN FIRMAR** (no se inventó keystore) → NO distribuible aún.
- APK debug SHA-256 `ab627114bd331bdc8ad12c677b5bda6b769e35e54b1f303040a011006d79a322`.
- APK release (sin firmar) SHA-256 `b4a1bf530506a3cc227f5e42195c48acd08d347a8d2143514c9690739d15b5f2`.
- 80 pruebas automatizadas, 0 fallos. Sin `clean`, junction intacta, sin reinstalar el Moto G71.

### 2026-10-24 — Validación del prototipo de lista negra + tipo TELÉFONO (pkgout13)

**ETAPA 1 (validación + TELÉFONO).** Ver `AUDITORIA_PRODUCCION.md` (matriz) y `COBERTURA_BILLETERAS.md`.
- `prototipo-lista-negra.csv` generado desde `SENTINEL LISTA NEGRA.txt` (original intacto): **24 personas,
  47 identificadores**, 0 errores, 0 duplicados normalizados. Pato Encargado fusiona `patodm.nx` + CBU +
  nombre bajo `pato-1` (no se duplica la regla existente). Hermana/Madre Roxy sin nombre completo (solo CBU).
- **Tipo TELÉFONO** agregado: `RuleType.TELEFONO`, normalización canónica AR en `TextNormalizer`
  (`normalizePhone`), `BlacklistCsv.mapType` (teléfono/tel/celular/cel/movil), import/export. **Sin cambio
  de esquema Room** (el tipo se guarda como String; valor nuevo del enum, retrocompatible).
- Coincidencia de teléfono **por grupo de dígitos completo** (no fragmentos) → sin falsos positivos.
  Limitación documentada: el prefijo 15 no se unifica con el 9 (prototipo).
- Pruebas: **91 automatizadas, 0 fallos** (+`PhoneDetectionTest` 8, +`ValidacionPrototipoTest` 3 que
  valida el CSV real: 24 personas, patodm.nx único, representación CBU/alias/nombre, sin identificadores
  compartidos → sin ambigüedad). Escritura vs pegado: ambas rutas evalúan el mismo texto (resolución);
  la distinción se validó en dispositivo para alias en sesiones previas. Sin Telegram real ni alarma.
- **No se cargó la lista en el Moto G71** (a la espera de autorización).
- APK debug SHA-256 `0a3407310d934725ae4b8d72b0e626848aafe54c780b2eec168a09ae53e943ec`.
- APK release (sin firmar) SHA-256 `66f0e4c7af9ea1963cefc6dc9dcf466538f234629bdbe15a4dd8142faa745b24`.

**ETAPA 2 (diagnóstico billeteras).** `COBERTURA_BILLETERAS.md`: la única vía viable para detectar/advertir
en Mercado Pago/Ualá/Brubank es un AccessibilityService consentido y acotado; el **bloqueo efectivo NO
está garantizado** (best-effort, depende de la app; pantallas con FLAG_SECURE pueden no verse). No implementado.

### 2026-10-24b — Detección por AccessibilityService, fase 1 (pkgout14)

Ver `DETECCION_ACCESIBILIDAD.md`. Primera versión **opt-in** de detección en billeteras autorizadas
(Mercado Pago `com.mercadopago.wallet`, Ualá `ar.com.bancar.uala`, Brubank `com.brubank` — paquetes
verificados en el dispositivo). **Solo detecta y avisa; NO bloquea.**
- Nuevos: `AccessibilityScanPolicy` (puro), `SentinelAccessibilityService`, config XML (sin
  `canPerformGestures`), `AccessibilitySetupScreen` (opt-in), entrada en Home. Servicio `exported=false`.
- **Reutiliza** `BlacklistPersonMatcher` + `ConfirmedAlertProcessor` (mismo incidente/alarma/Telegram;
  sin segundo matcher ni pipeline). Dedup por destinatario; excluye contraseñas; no almacena pantallas;
  a Telegram solo los campos mínimos; formato de alerta acordado e identidad del dispositivo reutilizados.
- Pruebas: **99 automatizadas, 0 fallos** (+`AccessibilityScanPolicyTest`). APK debug SHA-256
  `52d86c3b57326109f98152535977301dca3725762a7ff17f9306d8cbe2524559`.
- **Pendiente en dispositivo**: exposición real de nodos por app (no se instaló/activó). Sin cobertura
  declarada de ninguna billetera hasta probar en el teléfono. No se tocó el Moto G71.

### 2026-10-24c — Validación en dispositivo del AccessibilityService (pkgout15)

Ver `DETECCION_ACCESIBILIDAD.md` (matriz con resultados reales). Probado en el Moto G71 (ZY22DZ4BHM).
- **Mercado Pago: DETECTA** — `patodm.nx → Pato Encargado` detectado desde el campo de destinatario por
  accesibilidad; circuito completo: 1 incidente (#3, «Accesibilidad · Mercado Pago»), 1 alerta real
  (Telegram message_id 13), cola ENVIADO sin duplicados, alarma activada y detenida.
- **Ualá y Brubank: NO VERIFICADO** — en login (huella/contraseña); no se ingresó. Pendiente probar la
  pantalla de destinatarios una vez logueadas.
- Se agregó log **solo-debug** de eventos (sin texto) para evidencia por app. Sin defectos de código.
- Conteos: incidentes 2→3 (+1), cola 3→4 ENVIADO (+1). Preservado: lista negra (no reemplazada),
  identidad `Celular Pato`, token, historial. Gboard sin cambios. Token no en logs.
- APK debug SHA-256 `ec0f1e07b0329ddb280c188d03a9cdeb18f97cd25528b05f5301e5340cd16bba`.
- APK release (sin firmar) SHA-256 `8ee0680c1ca89d9f0f2e02f0f630832ddb7cd6051d74a038df811cb58760b2e9`.

### 2026-10-24d — Validación de 8 billeteras por accesibilidad (pkgout16)

Ver `DETECCION_ACCESIBILIDAD.md` (matriz de 8 apps). Allowlist ampliada a 8 paquetes reales verificados.
Modo diagnóstico SOLO DEBUG (archivo-bandera) para probar sin enviar alertas al grupo.
- **DETECTA (4)**: Mercado Pago, ARQ (com.dolarapp), Buepp (com.buepp), Lemon (com.applemoncash) —
  el servicio leyó el campo de destinatario (CBU/CVU/alias) y resolvió `patodm.nx → Pato Encargado`.
- **NO VERIFICADO (4)**: Ualá, Brubank, Personal Pay, AstroPay — bloqueadas por login (huella/PIN).
- **Cero alertas** durante el diagnóstico (incidentes 3→3, cola 4→4). Lista negra, identidad, token,
  historial preservados. Gboard sin cambios. Sin transferencias. Sin leer credenciales.
- Pruebas: 99 automatizadas, 0 fallos. APK debug SHA `d9742802…b868c`; release (sin firmar) `7536bef0…5931`.

### 2026-10-24e — 8/8 billeteras DETECTAN por accesibilidad (pkgout16)

Completada la validación en dispositivo de las 8 apps autorizadas (el propietario inició sesión en las
que lo requerían). **Todas DETECTAN** `patodm.nx → Pato Encargado` en el campo de destinatario, en modo
diagnóstico (sin alertas): Mercado Pago, ARQ, Buepp, Lemon, Personal Pay, Ualá, Brubank, AstroPay.
- **Cero alertas nuevas**: incidentes 3→3, cola 4→4 ENVIADO. Lista negra/identidad/token/historial
  preservados. Alarma 0, accesibilidad activa, Gboard sin cambios.
- Circuito completo validado una sola vez (Mercado Pago, msg 13, sesión previa). Resto en diagnóstico.
- 99 pruebas automatizadas, 0 fallos. APK debug SHA `d9742802…b868c`; release (sin firmar) `7536bef0…5931`.
- Sigue siendo **detección/aviso, no bloqueo**; SENTINEL no está listo para producción.

### 2026-10-24f — Importación controlada de la lista negra multiperfil (aplicada)

Ver `INFORME_IMPORTACION.md`. Importadas **25 personas / 76 identificadores** (CBU+alias, cantidad
variable) desde `SENTINEL LISTA NEGRA.txt`. En sitio, idempotente, sin borrar ni cambiar esquema Room.
- Antes→después: reglas 1→101, personas 1→25, ids 1→76; `patodm.nx → pato-1` conservado sin duplicar;
  0 conflictos; 40 CBU 22-díg (ceros conservados). Persiste tras reiniciar la app.
- 105 pruebas automatizadas, 0 fallos (+`ImportacionListaNegraTest`). Backup verificable previo.
- Incidentes 3→3, cola 4→4 ENVIADO (sin alertas/alarmas). Identidad/token/historial preservados. Gboard sin cambios.
