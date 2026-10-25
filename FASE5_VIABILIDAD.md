# Fase 5 — Informe técnico de viabilidad y limitaciones (detección)

Este informe evalúa, **antes de implementar**, qué detección es técnicamente viable y
legítima en Android, y qué limitaciones reales existen. Principio rector del proyecto:

> La detección debe usar **fuentes autorizadas y verificables**. **No** se implementa
> extracción silenciosa de contenido financiero de aplicaciones bancarias de terceros para
> retransmitirlo remotamente. El objetivo es detectar, advertir y notificar sobre un
> dispositivo corporativo de recaudación, con supervisión transparente para sus usuarios.

---

## 1. Qué puede exponer AccessibilityService

Un `AccessibilityService` recibe eventos (`AccessibilityEvent`) y puede recorrer el árbol de
nodos (`AccessibilityNodeInfo`) de la ventana en primer plano. En la práctica puede obtener:

- Texto visible de muchos controles (`text`, `contentDescription`) **cuando la app no lo
  bloquea**.
- El `packageName` de la app en primer plano (identifica qué aplicación está activa).
- Eventos de cambio de ventana/contenido (`TYPE_WINDOW_STATE_CHANGED`,
  `TYPE_WINDOW_CONTENT_CHANGED`) y de foco.
- En campos de texto, a veces el contenido mientras se escribe (`TYPE_VIEW_TEXT_CHANGED`).

Requiere que **el usuario active manualmente el servicio** en Ajustes → Accesibilidad, y
Android muestra una advertencia clara de lo que el servicio podrá observar. Esto es
compatible con la supervisión **transparente** de un dispositivo corporativo.

## 2. Qué NO puede leer de forma fiable

- **Apps que marcan sus vistas como seguras** (`FLAG_SECURE`) o que dibujan con Canvas/OpenGL
  o Web/Flutter sin exponer nodos: muchos **bancos y billeteras** ocultan o no exponen el
  texto sensible a accesibilidad. No hay garantía de leer destinatario/CBU/importe.
- Campos de **contraseña** y contenido marcado como no accesible.
- Texto renderizado como **imagen** (no hay nodo de texto).
- El contenido puede venir **fragmentado** en varios nodos; reconstruirlo es heurístico.
- No se puede **interceptar universalmente** cada pulsación de "Siguiente"/Enter ni
  garantizar el contexto (si una coincidencia corresponde a una transferencia real).

**Conclusión:** depender de accesibilidad para espiar apps bancarias de terceros no es
fiable **ni** es el enfoque que adopta este proyecto. Queda descartado como mecanismo de
extracción silenciosa.

## 3. Cuándo haría falta OCR y qué autorizaciones exige

Si el contenido no está disponible por accesibilidad, la única vía sería OCR sobre una
captura de pantalla. En Android eso exige **MediaProjection**, que:

- Requiere **consentimiento explícito del usuario** mediante un diálogo del sistema cada vez
  que se inicia la sesión de captura (no es un permiso permanente y silencioso).
- En Android 14+ el usuario puede elegir compartir **una sola app o toda la pantalla**, y la
  sesión debe asociarse a un servicio en primer plano de tipo `mediaProjection`.
- El OCR (p. ej. ML Kit Text Recognition) debe ejecutarse **localmente**; **no** se
  transmiten capturas a Telegram.

**Conclusión:** OCR sólo es admisible con consentimiento visible y recurrente. No permite
vigilancia encubierta. Para la primera versión **no** se implementa captura de pantalla.

## 4. Fuentes de detección autorizadas y verificables (lo que SÍ se hará)

1. **Contenido de prueba / pantalla propia:** una pantalla dentro de SENTINEL donde se
   ingresa o pega texto (o se simula el contenido de un campo de destinatario) y el motor de
   coincidencias evalúa contra la lista negra. Totalmente verificable y sin datos de terceros.
2. **App de recaudación propia e instrumentada:** si la empresa usa su propia app/flujo de
   cobro, puede enviar a SENTINEL el valor del destinatario (por `Intent`/API local) para
   validarlo antes de confirmar. Es la fuente **más fiable** y la recomendada.
3. **Accesibilidad limitada y declarada**, únicamente sobre apps propias o expresamente
   autorizadas y con el servicio activado por el usuario, documentando por app qué expone.

## 5. Motor de coincidencias (diseño, ya preparado para implementar)

La base ya existe y está probada: `detection/TextNormalizer.kt` normaliza de forma
controlada (alias/nombre: minúsculas, acentos, espacios; CBU/CVU/documento: sólo dígitos sin
alterar el número). El motor `MatchEngine` (a implementar) haría:

- Para cada regla activa, comparar el texto normalizado:
  - **coincidencia exacta** (igualdad del valor normalizado) o
  - **coincidencia parcial** (el valor aparece dentro del texto), según la regla.
- Contemplar texto **fragmentado** uniendo nodos antes de comparar.
- Asignar un **nivel de confianza**: mayor si el valor aparece en un campo de destinatario y
  hay un avance posterior; menor si sólo aparece en pantalla sin contexto.
- Al coincidir: registrar **incidente** (historial), activar **alarma local** y **encolar
  Telegram** con deduplicación. Todo esto ya está construido y probado en las fases 2–4.

El motor es una función pura y **testeable** (coincidencia exacta/parcial, mayúsculas,
espacios, CBU/CVU, reglas desactivadas, duplicados), tal como pide el plan de pruebas.

## 6. Servicio en segundo plano y sus límites

- Un `AccessibilityService` corre mientras esté activado; el sistema puede detenerlo.
- Un `ForegroundService` (ya usado para la alarma) permite trabajo visible en segundo plano,
  pero Android 12+ restringe **iniciarlo** desde segundo plano.
- Restricciones del fabricante (batería, "apps en suspensión") pueden pausar el servicio.
- **No se puede** garantizar funcionamiento tras cierre forzado, revocación de permisos o
  desinstalación. SENTINEL debe **reflejar en la interfaz** si el monitor no está activo
  (ya lo hace: la pantalla de Inicio muestra "REQUIERE ATENCIÓN" cuando no hay fuente activa).

## 7. Qué integración adicional haría falta

- Para la fuente (2), definir el contrato (Intent/acción local) con la app de recaudación
  propia y coordinarlo con quien la desarrolle.
- Si se usara accesibilidad, declarar `accessibility_service_config.xml`, pedir activación
  manual y documentar, **por cada app probada**, qué texto expone y qué no.
- Para OCR, integrar ML Kit y el flujo de consentimiento de MediaProjection, con
  procesamiento local.

## 8. Recomendación

Implementar primero el **motor de coincidencias** validado contra **contenido de prueba**
(fuente 1), que es verificable de punta a punta y no toca datos de terceros, y en paralelo
definir la integración con la **app de recaudación propia** (fuente 2) como camino fiable y
legítimo. Accesibilidad/OCR quedan como opciones acotadas, declaradas y con consentimiento,
nunca como extracción silenciosa.
