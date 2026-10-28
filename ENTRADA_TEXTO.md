# Entrada de texto a SENTINEL — evaluación e implementación

El comparador (matcher de personas) ya existía. Este documento resuelve **cómo llega el
texto** a analizar, evaluando las opciones reales de Android y eligiendo la más viable y
legítima para un prototipo funcional, con **consentimiento y límites**.

## 1 y 2. Opciones evaluadas (viabilidad, límites, permisos, seguridad)

| Opción | Cómo obtiene texto | Consentimiento / visibilidad | Fiabilidad | Límites reales | Riesgo |
|---|---|---|---|---|---|
| **AccessibilityService** | Lee nodos de la app en primer plano | Se activa en Ajustes (aviso de Android), pero **corre en segundo plano sin indicador por lectura** | Media; depende de que la app exponga nodos | **No** lee apps con vistas seguras/`FLAG_SECURE`, ni texto dibujado como imagen; texto fragmentado; no intercepta "Siguiente" universalmente | **Alto**: capacidad de lectura amplia y poco transparente; cercano a captura encubierta |
| **MediaProjection + OCR** (ML Kit) | Captura de pantalla + OCR local | Android **obliga** diálogo de consentimiento por sesión **y** un **indicador visible** permanente | Media; OCR falla con texto pequeño/estilizado | **No** captura pantallas con `FLAG_SECURE` (muchos bancos/billeteras ⇒ **frame negro**); consume batería; sesión debe renovarse | Medio: consentida y visible, pero inútil justo en apps protegidas |
| **Integración directa / acción del operador** (`ACTION_PROCESS_TEXT`, Compartir, o API local de una app propia) | El operador **selecciona/comparte** el texto, o una app autorizada lo envía | **Explícito y por operación**; sin lectura de fondo | **Alta** (el texto llega tal cual) | Requiere una **acción manual** del operador (o integración de la app de cobro propia) | **Bajo**: acotado, consentido, sin vigilancia oculta |

Conclusión del análisis: en Android **no existe** una forma fiable, automática y transparente
de leer el destinatario dentro de apps bancarias de terceros (el `FLAG_SECURE` bloquea OCR y
la accesibilidad es intransparente y poco fiable). La opción **viable, legítima y verificable**
para el prototipo es la **entrada consentida por acción del operador**.

## 3. Qué quedó implementado

**Entrada de texto consentida por el operador** (`EvaluateTextActivity`):
- **`ACTION_PROCESS_TEXT`**: al seleccionar texto en cualquier app, aparece **"SENTINEL — verificar"** en el menú de selección. El operador toca y SENTINEL analiza ese texto.
- **Compartir (`ACTION_SEND`)**: desde "Compartir" de cualquier app, elegir SENTINEL.
- El texto pasa por el **matcher actual** (`DetectionPipeline` → `BlacklistPersonMatcher`) y muestra la **coincidencia identificable** (persona, tipo, confianza) o "sin coincidencias".
- **Deduplicación**: el mismo texto recibido varias veces **no** repite la coincidencia (ventana temporal), evitando incidentes repetidos.
- Esta pantalla **no** activa alarma ni envía Telegram (es verificación). El procesamiento operativo real (incidente + alarma + Telegram) se hace por el circuito autorizado `ConfirmedAlertProcessor`.
- **No** hay keylogging, **no** hay captura oculta, **no** hay lectura de fondo de apps de terceros, **no** se recolectan contraseñas/chats.

## 4. La entrada pasa por el matcher
Sí: `DetectionPipeline.analyze(text, personas, dedup)` usa `BlacklistPersonMatcher` y
devuelve coincidencias con persona/tipo/confianza, deduplicando por evento.

## 5. Prueba E2E controlada (sin efectos reales)
`DetectionPipelineTest` (JVM): con un **destinatario ficticio** en una lista de prueba
(`patodm.nx` / "Juan Perez"), simula el texto recibido y verifica:
- detección por **alias** y por **nombre** (con variante),
- **deduplicación** del mismo texto repetido,
- ausencia de coincidencia para texto no listado.
No envía Telegram ni activa alarmas. *(Resultado real: ver INFORME.md / sección de tests.)*

Prueba manual en la app: abrir cualquier app con texto (p. ej. Notas), escribir un alias de la
lista negra de prueba, **seleccionarlo → "SENTINEL — verificar"**, y ver la coincidencia.

## 6. Flujo manual (lo más directo posible) y limitaciones
- **Flujo**: el operador **selecciona** el alias/nombre/CBU del destinatario (o usa Compartir) y toca **SENTINEL — verificar** antes de confirmar la transferencia. Un toque.
- **Limitación registrada**: si una app **impide seleccionar/compartir** su texto (p. ej. render seguro), este método no obtiene el dato. **No se elude.** Para esos casos, la vía es la **integración directa** con la app de cobro propia (ver §9).

## 7. Preservación
No se tocó la configuración de Telegram, el historial, las migraciones ni el junction.
No se ejecutó `gradlew clean`.

## 8. Permisos que necesita
- **Ninguno especial** para `ACTION_PROCESS_TEXT`/Compartir (es interacción del usuario).
- (La alarma usa notificaciones/FGS ya declarados; Telegram usa INTERNET ya declarado.)

## 9. Qué falta para usarlo en los celulares de recaudación
1. **Decidir la fuente operativa**:
   - **Recomendado**: integrar la **app de cobro propia** para que envíe el destinatario a SENTINEL por `Intent`/API local antes de confirmar (fiable, sin acción manual, sin tocar apps de terceros). Falta definir ese contrato con quien desarrolle esa app.
   - **Alternativa inmediata (ya implementada)**: que el operador **seleccione/comparta** el texto del destinatario hacia SENTINEL. Requiere la acción manual descrita.
2. **Conectar la entrada al circuito operativo**: hoy la verificación muestra la coincidencia; para que en producción genere incidente+alarma+Telegram automáticamente, enrutar la coincidencia confirmada a `ConfirmedAlertProcessor` desde el adaptador autorizado (queda a un paso).
3. **Si se requiere OCR** para apps que no exponen texto pero **sí** permiten captura: implementar MediaProjection + ML Kit con su diálogo de consentimiento e indicador visible. **No** sirve para apps con `FLAG_SECURE`.
4. **No** se implementará lectura encubierta ni accesibilidad silenciosa de apps bancarias de terceros.
