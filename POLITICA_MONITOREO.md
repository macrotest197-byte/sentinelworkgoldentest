# SENTINEL — Política de monitoreo

Versión 1. El texto de la app sale de `detection/MonitoringPolicy.kt`; este documento y esa
constante deben decir lo mismo. Cambiar el texto obliga a subir `MonitoringPolicy.VERSION`.

## Qué es esto
SENTINEL está instalado en teléfonos de trabajo. Detecta cuando el destinatario de una
transferencia coincide con una cuenta de la lista de la operación, y avisa.

## Qué lee
Solo ocho aplicaciones de billetera, y solo mientras están en pantalla: el campo de
destinatario y el texto visible de esa pantalla.

## Qué NO lee
Contraseñas, PIN y códigos. Chats, correo, contactos y navegación. Saldos e importes. No
registra las teclas que se escriben.

## Qué se guarda en el teléfono
Historial de coincidencias: qué se detectó, a quién corresponde, cuándo y en qué aplicación.
No se guarda el texto completo de las pantallas.

## Qué se envía y a dónde
Un mensaje por coincidencia a un grupo de Telegram de la operación, con dispositivo, operador,
dato detectado, persona, tipo de dato y hora. Sin capturas y sin importes.

## Quién lo ve
Los administradores del grupo.

## Cómo se revoca
Ajustes → Accesibilidad → desactivar SENTINEL. Ajustes → Teclados → desactivar el teclado
SENTINEL. En cualquier momento, sin avisar.

## Qué pasa si se revoca
El teléfono deja de reportar. SENTINEL nunca bloquea una transferencia: solo avisa.

## Titularidad de la cuenta
Si la billetera es una cuenta personal y no de la operación, queda declarado en la pantalla
de inicio.

## Registro de aceptación
Cada teléfono guarda localmente la versión aceptada, la fecha y su identificador de
instalación (`sentinel_consent`).