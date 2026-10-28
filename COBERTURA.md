# SENTINEL — Matriz de cobertura real por aplicación

Este documento distingue **tres capacidades distintas** para no sobreafirmar la protección:

1. **Teclado disponible**: SENTINEL permite escribir en el campo (es un teclado Android normal).
2. **Campo inspeccionable**: SENTINEL puede **evaluar** el texto de ese campo contra la lista negra.
3. **Prevención efectiva**: SENTINEL **detecta** la coincidencia y **logra impedir** que el dato
   continúe (limpieza del campo + alerta), con las limitaciones de la app.

> Regla de oro: **tener el teclado abierto en una app NO significa que la detección funcione ahí.**

## Cómo decide SENTINEL si inspecciona (política fail-closed)
SENTINEL **no** inspecciona por defecto. Solo evalúa un campo cuando la **app dueña del campo**
lo marca explícitamente con el marcador privado `com.sentinel.recaudacion.SAFE_RECIPIENT_TEXT`
(o el de demo), y aun así excluye contraseñas, PIN/OTP, email, URI, numéricos y autocompletado.
Esto es **intencional**: evita que el teclado se convierta en un registrador general del texto de
otras aplicaciones. Consecuencia directa: **las apps de terceros que no agregan ese marcador
quedan fuera de inspección**, aunque se pueda escribir en ellas.

## Matriz

| App | Campo | 1. Teclado | 2. Inspeccionable | 3. Prevención | Verificación |
|---|---|:--:|:--:|:--:|---|
| **SENTINEL** (app propia) | Campo marcado (demo / destinatario) | ✅ | ✅ | ✅ | **Dispositivo real** — `patodm.nx → Pato Encargado`, incidente + alarma + Telegram (pkgout9) |
| **SENTINEL** | Campo de contraseña/PIN/OTP | ✅ | ❌ (excluido) | ❌ | Automatizada (`InputFieldPolicyTest`) |
| **WhatsApp** | Mensaje de chat | ✅ | ❌ (sin marcador) | ❌ | **Dispositivo**: escritura verificada antes; inspección deshabilitada por diseño |
| **Navegador** (Chrome/otros) | Campos web / formularios | ✅ (esperado) | ❌ (sin marcador; además WebView/autocomplete) | ❌ | No probado |
| **Mercado Pago** | Alias/CBU/CVU del destinatario | ⚠️ (esperado, si acepta IME de terceros) | ❌ | ❌ | **No probado · sin evidencia** |
| **Ualá** | Alias/CBU/CVU del destinatario | ⚠️ (esperado) | ❌ | ❌ | **No probado · sin evidencia** |
| **Brubank** | Alias/CBU/CVU del destinatario | ⚠️ (esperado) | ❌ | ❌ | **No probado · sin evidencia** |

**Ninguna billetera de terceros está marcada como protegida**: no hay evidencia de detección ni
prevención en Mercado Pago, Ualá ni Brubank. El teclado puede abrirse y escribir, pero **no**
inspecciona esos campos.

## Límite técnico: ¿por qué no hay “detección universal” con un IME normal?
Un teclado Android (IME) solo puede leer el texto del **campo que está editando** mientras SENTINEL
es el teclado activo, a través de `InputConnection` (`getTextBeforeCursor`, `onUpdateSelection`,
pegado). **No** puede ver la pantalla de otras apps, ni campos en los que no se está escribiendo,
ni actuar cuando el usuario usa otro teclado (p. ej. Gboard). Técnicamente, el IME *sí* podría leer
el texto que se escribe en cualquier app, pero SENTINEL **elige no hacerlo** (fail-closed) para no
convertirse en un keylogger general. Por eso la detección fiable requiere **cooperación del campo**
(el marcador) y no es universal.

### Mecanismo legítimo más cercano a “cobertura amplia”
Sin usar servicios de accesibilidad encubiertos, captura global de pantalla ni registro de teclas,
las únicas vías legítimas para ampliar cobertura son:
1. **Integración de la app dueña**: que la app que se quiere vigilar agregue el marcador de SENTINEL
   a su campo de destinatario. Realista solo para apps propias/controladas, **no** para billeteras
   de terceros.
2. **AccessibilityService explícito y consentido**: un servicio de accesibilidad **visible**, que el
   usuario activa a sabiendas, acotado a apps/campos concretos, mostrando su actividad. Es una
   decisión de diseño separada y **no** se implementó aquí porque se descartó todo mecanismo
   encubierto. Si se quisiera cobertura en billeteras de terceros, este es el camino legítimo, con
   consentimiento informado y alcance declarado.

En el estado actual, SENTINEL ofrece **prevención efectiva en campos integrados** (su propia app o
apps que adopten el marcador) y **escritura normal** en el resto, sin prometer protección que no
puede cumplir.
