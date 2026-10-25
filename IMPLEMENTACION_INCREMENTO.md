# SENTINEL — reporte del incremento

Fecha: 21/10/2026

## Cambios

- Comparador de alta precisión para nombres, aliases y números; normaliza acentos, caja y
  espacios, acepta segundos nombres/iniciales con nombre y apellido exactos, devuelve
  confianza con umbral de 95% y evita subcadenas triviales. Pruebas positivas y negativas
  actualizadas.
- Modelo de persona con ID, nombre principal, variantes y varios identificadores. El CSV
  mantiene la asociación usando `persona_id`, `nombre_principal` y `variantes`.
- Aprovisionamiento administrativo por `sentinel_blacklist_provision.csv`: valida el
  archivo completo, agrega o actualiza por tipo/valor y conserva registros omitidos; puede
  desactivar una regla con `activo=no`. El archivo se borra tras importarse correctamente.
  La UI normal sigue sin controles de importación.
- La pantalla simulada activa comparación desde el botón o Enter y muestra confianza; no
  registra ni envía nada.
- Identidad UUID por instalación y nombre amigable provisionado por administrador. El
  historial y la cola Telegram admiten el ID de instalación. Migraciones Room 3→7 preservan
  registros existentes y agregan asociación de persona, confianza y fragmento autorizado.
- El nombre del dispositivo ahora se actualiza con un archivo administrativo separado,
  `sentinel_device_provision.json`, sin reaprovisionar Telegram ni permitir cambiar el UUID.
- Formato Telegram actualizado a la oración española solicitada.
- Lista negra y pantalla CSV son de solo lectura en la UI normal. `sentinel_blacklist_provision.csv`
  permite al administrador agregar/actualizar por clave y desactivar con `activo=no`; las
  reglas omitidas se conservan. El intento de reemplazar/borrar toda la lista fue rechazado
  por la revisión automática porque eliminaba todas las reglas en bloque; no se implementó.
- `ConfirmedAlertProcessor` registra una coincidencia confirmada, solicita la alarma y
  encola Telegram con identidad persistente. Aún no tiene una fuente autorizada conectada.
- El fragmento de origen queda solo en la base local y se limita a 240 caracteres. La
  revisión automática rechazó incluirlo en la exportación CSV porque podría enviarse a un
  destino arbitrario; el CSV conserva persona, dispositivo e ID, confianza y coincidencia.
- Añadida la pantalla **Prueba de texto simulada**. Solo compara en pantalla y nunca
  envía Telegram ni registra incidente.
- Auditoría: una contraseña interna de la app no impide que Android desinstale la app ni
  que se cambien ajustes del sistema. El camino adecuado es Android Enterprise / EMM con
  device owner fully managed; revisar las fuentes oficiales enlazadas en `INFORME.md`.
  No se inscribió ni restableció ningún dispositivo.

## Archivos principales

- `app/src/main/java/com/sentinel/recaudacion/detection/TextNormalizer.kt`
- `app/src/main/java/com/sentinel/recaudacion/data/settings/DeviceIdentityStore.kt`
- `app/src/main/java/com/sentinel/recaudacion/data/settings/DeviceProvisioning.kt`
- `app/src/main/java/com/sentinel/recaudacion/detection/BlacklistPersonMatcher.kt`
- `app/src/main/java/com/sentinel/recaudacion/detection/ConfirmedAlertProcessor.kt`
- `app/src/main/java/com/sentinel/recaudacion/csv/BlacklistProvisioning.kt`
- `app/src/main/java/com/sentinel/recaudacion/data/db/AppDatabase.kt`
- `app/src/main/java/com/sentinel/recaudacion/data/db/IncidentEntity.kt`
- `app/src/main/java/com/sentinel/recaudacion/data/db/OutboxEntity.kt`
- `app/src/main/java/com/sentinel/recaudacion/csv/IncidentCsv.kt`
- `app/src/main/java/com/sentinel/recaudacion/ui/alarm/SimulatedTextTestScreen.kt`
- `app/src/main/java/com/sentinel/recaudacion/telegram/AlertMessage.kt`
- `app/src/test/java/com/sentinel/recaudacion/TextNormalizerTest.kt`
- `app/src/test/java/com/sentinel/recaudacion/DeviceProvisioningTest.kt`
- `INFORME.md`

## Verificación

Se solicitó ejecutar `testDebugUnitTest` y `assembleDebug`; no se completaron. En dos
intentos, el wrapper falló antes de iniciar Gradle con acceso denegado al archivo de bloqueo
`%USERPROFILE%\.gradle\wrapper\dists\gradle-8.9-bin\...gradle-8.9-bin.zip.lck`.
Se detuvo sin limpiar ni borrar datos. `app/build` se verificó como junction a
`C:\SentinelBuild\build3` y se dejó intacto.

## Próximos pasos exactos

1. Resolver permisos/acceso del usuario al wrapper Gradle 8.9 y volver a correr
   `testDebugUnitTest` y `assembleDebug` sin `clean`.
2. Si el build pasa, instalar en un dispositivo de prueba y confirmar historial, alarma y
   entrega Telegram ya existente; no usar texto simulado como disparador de alertas.
3. Antes de producción, definir el EMM/Android Enterprise y el proceso de enrolamiento
   fully managed; probarlo solo con un equipo de prueba autorizado.
4. Elegir e integrar una fuente autorizada de texto que invoque el coordinador, sin leer
   texto global ni conectar la prueba simulada. La app todavía no detecta transferencias
   reales automáticamente.
5. Definir el EMM y, con autorización específica, probar enrollment fully managed en un
   dispositivo de prueba.
6. Las reglas no se borran desde el CSV administrativo: deben desactivarse con `activo=no`.
