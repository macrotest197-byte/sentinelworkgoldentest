# SENTINEL — Guía de alta inicial de un dispositivo corporativo

Proceso que realiza el **administrador** (no el usuario final) para poner en servicio un teléfono.
Requiere el **APK release FIRMADO** con la keystore corporativa (ver `GUIA_OPERACION.md` §1).

## 0. Prerrequisitos
- APK release firmado (no el de pruebas `.debug`, no el sin firmar).
- Datos del dispositivo: nombre corporativo (p. ej. `Celular Operador 02`).
- Lista negra vigente en CSV (formato de `PROCEDIMIENTO_PERSONAS.md`).
- Configuración de Telegram corporativa (token + chat id del grupo).
- Idealmente, una solución **MDM** para empujar el aprovisionamiento fuera del alcance del usuario.

## 1. Instalar
```bash
adb install app-release.apk      # firmado; instalación nueva (arranca vacío)
```

## 2. Aprovisionar (solo administrador)
Colocar en `Android/data/com.sentinel.recaudacion/files/` (vía MDM o, transitoriamente, adb):
- `sentinel_device_provision.json` → `{"deviceName":"Celular Operador 02"}`
- `sentinel_telegram_provision.json` → token + groupChatId (ver `PROCEDIMIENTO_TELEGRAM.md`)
- `sentinel_blacklist_provision.csv` → reglas por persona (ver `PROCEDIMIENTO_PERSONAS.md`)
Al abrir la app, importa cada archivo, **cifra** el token (Keystore), aplica las reglas y **borra**
los archivos. El operador queda con una interfaz de **solo lectura**.

## 3. Activar el teclado
Ajustes → Sistema → Teclados → activar «SENTINEL · teclado de prevención» y seleccionarlo en los
campos a vigilar (los que integren el marcador de SENTINEL).

## 4. Verificar (sin enviar alertas reales salvo autorización)
- «Lista negra»: reglas correctas, en solo lectura.
- «Telegram»: «configurado», destino grupo, token enmascarado.
- «Permisos y diagnóstico»: servicio operativo.
- Identidad del dispositivo con el nombre corporativo asignado.

## 5. NO distribuir el entorno de pruebas
- No copiar la base de datos, el token ni los archivos del Moto G71 a otros teléfonos.
- Cada dispositivo se aprovisiona por separado. Una instalación nueva **no** hereda datos de otro.

## Límite conocido
Mientras no exista una autoridad central que autentique dispositivos y entregue reglas firmadas, el
aprovisionamiento por archivos **debe** restringirse vía MDM: de lo contrario, alguien con acceso a la
carpeta externa de la app podría alterar reglas, Telegram o el nombre del dispositivo. Ver
`AUDITORIA_PRODUCCION.md`.
