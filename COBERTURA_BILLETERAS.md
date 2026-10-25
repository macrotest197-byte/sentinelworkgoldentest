# SENTINEL — Cobertura de billeteras (Mercado Pago, Ualá, Brubank) — DIAGNÓSTICO

Documento de **solo diagnóstico** (ETAPA 2). No se implementa nada: ni servicio de accesibilidad, ni
cambios de permisos, teclado predeterminado o configuración del teléfono. Separa explícitamente
**detección**, **advertencia** y **bloqueo efectivo**, y no afirma que una transferencia pueda
impedirse salvo que esté demostrado (no lo está).

## Punto de partida
El teclado SENTINEL (IME) solo inspecciona **campos marcados** por la app dueña. Las billeteras de
terceros **no** marcan sus campos → con el IME se puede **escribir** pero **no detectar ni bloquear**
ahí. Para cubrir billeteras hace falta otro mecanismo.

## Alternativas técnicas (Android, transparentes y consentidas)

### 1) AccessibilityService consentido y acotado  ← única vía viable para detectar en billeteras
Un servicio de accesibilidad que el usuario/administrador **activa a sabiendas**, declarado y limitado
a las apps en alcance.
- **Detección: SÍ (viable).** Puede leer el texto de los campos (eventos `TYPE_VIEW_TEXT_CHANGED` /
  `TYPE_WINDOW_CONTENT_CHANGED` y el árbol de nodos) y comparar con la lista negra al escribir o pegar.
- **Advertencia: SÍ (robusta).** Puede mostrar overlay, notificación, vibración o la alarma local.
- **Bloqueo efectivo: BEST-EFFORT, NO garantizado.** Puede intentar borrar el campo
  (`ACTION_SET_TEXT` si el nodo es editable y la app lo permite), pero **no** puede deshabilitar de
  forma fiable el botón «confirmar/transferir» ni interceptar el toque. El resultado depende de cada
  app y versión.
- **Limitaciones serias:**
  - Muchas pantallas bancarias usan `FLAG_SECURE` o bloquean accesibilidad en vistas sensibles → el
    servicio **puede no ver nada** del campo de destinatario.
  - Apps con contenido en **WebView/Flutter/custom canvas** pueden no exponer el texto por accesibilidad.
  - Se rompe con actualizaciones de la billetera (los nodos cambian).
  - Requiere **activación manual** del usuario (consentimiento explícito) y es revocable.
- **Consentimiento/transparencia:** apto solo si es visible, declarado, acotado a apps concretas, sin
  exfiltración más allá de la alerta ya existente. No es un keylogger encubierto.

### 2) Teclado SENTINEL (IME) — ya existe
- Detección/advertencia/bloqueo: **solo en campos marcados** (app propia o integradas). En billeteras:
  escritura sí, **detección no**. No cubre el objetivo por sí solo.

### 3) Monitoreo del portapapeles en segundo plano — NO viable
- Android 10+ restringe la lectura del portapapeles a la app con foco o al IME activo. No hay monitor
  global legítimo. Además, copiar/pegar no es el único vector (escritura a mano, elegir un contacto).

### 4) Integración de la billetera (que la app añada el marcador de SENTINEL) — ideal pero no realista
- Requiere cooperación de Mercado Pago/Ualá/Brubank. No está bajo nuestro control.

### 5) Inspección de red (VPN local / proxy) — NO (ni legítimo ni posible)
- Las billeteras usan TLS con certificate pinning. No se puede inspeccionar el destinatario ni es
  aceptable hacerlo.

### 6) MDM / Device Owner — complementario, no resuelve la detección de contenido
- Un MDM puede gestionar la flota y restringir apps, pero **no** puede leer el campo de destinatario
  dentro de una billetera. Sirve para aprovisionar SENTINEL de forma segura, no para detectar.

## Matriz resumen por capacidad

| Mecanismo | Detección en billeteras | Advertencia | Bloqueo efectivo | Consentido/transparente | Viable hoy |
|---|:--:|:--:|:--:|:--:|:--:|
| IME SENTINEL (actual) | ❌ (solo campos marcados) | ❌ | ❌ | ✅ | ✅ |
| AccessibilityService consentido | ⚠️ parcial (según app) | ✅ | ⚠️ best-effort, **no garantizado** | ✅ (si declarado) | ⚠️ a construir |
| Monitor de portapapeles | ❌ | — | ❌ | — | ❌ |
| Integración de la app | ✅ | ✅ | ✅ | ✅ | ❌ (no controlamos la app) |
| Inspección de red | ❌ | — | ❌ | ❌ | ❌ |
| MDM / Device Owner | ❌ (no lee contenido) | — | — | ✅ | ✅ (complementario) |

## Conclusión (honesta)
- La **única vía técnicamente viable** para **detectar y advertir** destinatarios de la lista negra
  dentro de Mercado Pago/Ualá/Brubank es un **AccessibilityService consentido y acotado**.
- El **bloqueo efectivo de la transferencia NO está garantizado**: es best-effort y depende de cada
  app; en pantallas protegidas (`FLAG_SECURE`/anti-accesibilidad) puede no detectar nada.
- No se debe prometer que «la billetera dará error» de forma universal. Lo honesto es ofrecer
  **detección + advertencia** donde sea técnicamente posible, documentando por app qué se logra.
- **No implementado**: si se decide avanzar, el siguiente paso sería un diseño detallado del servicio
  (apps en alcance, consentimiento, qué nodos se leen, qué se intenta bloquear, pruebas por app),
  antes de escribir código.
