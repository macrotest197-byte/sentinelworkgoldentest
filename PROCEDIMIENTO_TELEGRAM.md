# Procedimiento: rotar el token y configurar el grupo de Telegram (seguro)

Reutiliza la arquitectura existente (**no** hay un segundo cliente ni otro pipeline):
`SecureSettingsStore` (cifrado Keystore) · `TelegramProvisioning` (importa y borra el archivo)
· `TelegramClient` (envío) · `ConfirmedAlertProcessor` (incidente+alarma+Telegram) ·
`TelegramSendWorker` (reintentos). El token **nunca** vive en el código, recursos, logs ni APK.

## 1. Revocar y regenerar el token (BotFather)
Como el token anterior quedó expuesto:
1. En Telegram, abrí **@BotFather** → `/mybots` → elegí tu bot → **API Token** → **Revoke current token**.
2. BotFather genera un **token nuevo**. No lo pegues en este chat.

## 2. Obtener y VALIDAR el chat id del grupo (no el enlace)
El enlace de invitación (`t.me/+IcKzTwOpxn5iYjgx`) **no** es el chat id. Necesitás el **número**:
- Con el bot ya en el grupo, enviá un mensaje en el grupo y abrí en tu navegador
  `https://api.telegram.org/bot<TU_TOKEN_NUEVO>/getUpdates`; buscá
  `"chat":{"id":-XXXXXXXXXX,"title":"..."}`. El id de grupo es **negativo**
  (p. ej. `-5144820304` para grupo básico, o `-100XXXXXXXXXX` para supergrupo).
- SENTINEL ahora **valida** el formato: acepta `-?\d{5,}` o `@usuario`, y **rechaza** enlaces
  (`t.me`, `joinchat`, `http`, `+...`). Si el aprovisionamiento trae un enlace como id, se
  descarta (no se guarda una configuración que fallaría al enviar).

## 3. Aprovisionar en SENTINEL (mecanismo seguro)
1. Creá `sentinel_telegram_provision.json`:
   ```json
   {
     "token": "TOKEN_NUEVO_DE_BOTFATHER",
     "groupChatId": "-5144820304",
     "privateChatId": "",
     "sendToGroup": true,
     "sendToPrivate": false
   }
   ```
   (Guardá en UTF-8, fuera de OneDrive/git. No lo subas con el token real.)
2. Copialo al directorio privado de la app en el teléfono:
   `Android/data/com.sentinel.recaudacion.debug/files/sentinel_telegram_provision.json`
   (por ADB: `adb push ... /sdcard/Android/data/com.sentinel.recaudacion.debug/files/`).
3. Abrí SENTINEL. Al iniciar, importa la config, **la cifra (Keystore)** y **borra el archivo**.
   La pantalla de Telegram mostrará "configurado" y el token enmascarado.

> Alternativa sin escribir JSON a mano: la plantilla `telegram-provision.example.json` del
> proyecto. El operador no puede editar esta configuración (pantalla de solo lectura).

## 4. Verificar (sin revelar el token)
- En SENTINEL → Telegram: estado "configurado", destino "GRUPO", pendientes 0.
- Prueba real de envío: **solo con tu autorización** (botón "Enviar prueba"). Hasta entonces,
  no se envían mensajes reales.

## Seguridad
- El token queda cifrado en `sentinel_secure_prefs.xml` (EncryptedSharedPreferences/Keystore).
- `TelegramClient.redactSecrets()` borra cualquier token de los mensajes de error de red.
- Verificado por pruebas: `TelegramSecurityTest`, `TelegramChatIdTest`.
