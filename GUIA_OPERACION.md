# SENTINEL — Guía de instalación, configuración, actualización y recuperación

## 0. Versiones y hashes (candidato a producción)
- **APK release (candidato, sin firmar)**: `app-release-unsigned.apk`
  SHA-256 `b4a1bf530506a3cc227f5e42195c48acd08d347a8d2143514c9690739d15b5f2`
- **APK debug (pruebas)**: `app-debug.apk`
  SHA-256 `ab627114bd331bdc8ad12c677b5bda6b769e35e54b1f303040a011006d79a322`
- Ubicación: `C:\SentinelBuild\pkgout12\app\outputs\apk\{release,debug}\`

> El APK release está **sin firmar**: antes de instalarlo en producción hay que firmarlo con la
> keystore de la organización. No se incluye ninguna keystore ni contraseña en el proyecto.

## 1. Firma del APK release (previo a instalar)
```bash
# Generar keystore (una sola vez; guardar la contraseña fuera del repositorio)
keytool -genkey -v -keystore sentinel-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias sentinel
# Firmar y alinear
zipalign -v 4 app-release-unsigned.apk app-release.apk
apksigner sign --ks sentinel-release.jks app-release.apk
apksigner verify app-release.apk
```

## 2. Instalación
```bash
adb install app-release.apk           # primera instalación (firmado)
```
Luego, en el teléfono:
1. Activar el teclado SENTINEL: Ajustes → Sistema → Teclados → Administrar teclados → activar
   «SENTINEL». (El teclado predeterminado puede seguir siendo Gboard; se cambia solo cuando se
   quiere usar SENTINEL para escribir.)
2. Conceder notificaciones cuando la app lo pida (para alarma/alertas).

## 3. Configuración (solo administrador, fuera de la interfaz del operador)
Todo se aprovisiona dejando un archivo en el directorio privado de la app
(`Android/data/<applicationId>/files/`) que la app importa al iniciar y **borra** tras consumirlo.

- **Telegram** (`sentinel_telegram_provision.json`): token + chat id del grupo. Ver
  `PROCEDIMIENTO_TELEGRAM.md`. El token se cifra (Keystore) y nunca se muestra.
- **Dispositivo** (`sentinel_device_provision.json`): `{"deviceName":"Celular Pato"}`.
- **Lista negra / personas** (`sentinel_blacklist_provision.csv`): ver `PROCEDIMIENTO_PERSONAS.md`
  (columnas `tipo,valor,descripcion,severidad,activo,persona_id,nombre_principal,variantes`).

La interfaz del operador es **solo lectura**: no puede editar Telegram ni la lista.

## 4. Actualización (sin perder datos)
```bash
adb install -r app-release.apk        # reinstala conservando datos (misma firma)
```
- `install -r` preserva la base de datos (Room), el historial, la lista negra, la identidad del
  dispositivo y el token cifrado. **No** usar `-r` con una firma distinta (fallaría); mantener
  siempre la misma keystore.
- Room aplica migraciones automáticamente; no se borra la base.

## 5. Recuperación / respaldo
- **Reglas de lista negra**: exportables a CSV desde «Exportar lista» (copia de seguridad y
  plantilla). Reimportables por el CSV de administración.
- **Telegram**: re-aprovisionar con el JSON si se pierde la configuración.
- **Identidad del dispositivo**: el id interno es estable; el nombre visible se re-registra con el
  JSON de dispositivo. 
- **Backups del código**: `C:\SentinelBuild\backup-canonico-*` (copias verificables por fecha).

### Si se reinstala desde cero (datos borrados)
1. Instalar y abrir SENTINEL.
2. Re-aprovisionar Telegram, dispositivo y lista negra (pasos de la sección 3).
3. Verificar en «Permisos y diagnóstico» que el servicio esté operativo.

## 6. Alcance y límites (leer antes de desplegar)
SENTINEL previene en **campos integrados** (su propia app o apps que adopten el marcador) y permite
**escritura normal** en el resto. **No** inspecciona billeteras de terceros (Mercado Pago, Ualá,
Brubank) ni garantiza detección universal. Ver `COBERTURA.md` para la matriz y el mecanismo legítimo
más cercano a una cobertura amplia.
