# SENTINEL — Auditoría e importación de lista negra multiperfil (VISTA PREVIA, no aplicada)

Fuente: `C:\Users\diazm\Downloads\SENTINEL LISTA NEGRA.txt`. **Nada se escribió en la base ni en el
dispositivo.** Archivos generados en el proyecto: `import_preview.csv`, `import_preview.json`.

## 1. Auditoría
- **25 personas**, **76 identificadores** (40 CBU + 36 alias) + 25 nombres de titular.
- **Cantidad variable** de identificadores por persona: **3 a 7** (p. ej. Pato 2 CBU + 2 alias;
  Sol 3 CBU + 3 alias; Nahi 3 CBU). Sin máximo artificial.
- **CBU**: 40, todos de **22 dígitos**, ceros iniciales conservados (32 empiezan con 0).
- **Duplicados dentro de persona: 0.** **Conflictos (id en ≥2 personas): 0.**
- **3 personas «Roxy» distintas** (NO fusionadas, distinto titular legal):
  Hermana Roxy Toledo · Madre Roxy Toledo · Roxy Operadora (tres titulares legales distintos).
- Rol operativo y titular legal se mantienen **separados** (rol = `nombre_principal`/descripción;
  titular = identificador de tipo Nombre).

### Para revisión manual (1)
- **`20000000017.CVU.TP`** (Tamara Operadora): cadena con «CVU», tratada como **ALIAS** (no son 22
  dígitos). Confirmar si debe quedar como Alias o etiquetarse como CVU (la detección es equivalente).

## 2. Modelo de datos (compatible, sin cambio de esquema)
El modelo actual **ya** soporta múltiples identificadores por persona: cada identificador es una fila
`BlacklistEntity` vinculada por `persona_id`; `BlacklistRepository.activePersons()` los agrupa y
`BlacklistPersonMatcher.resolve()` arma candidatos con **todos** los identificadores + el nombre y sus
variantes. No hace falta cambiar Room. ID estable por persona (`persona_id`), `pato-1` preservado.

## 3. Vista previa de importación
- `import_preview.csv`: **101 filas** (76 identificadores + 25 nombres), 0 errores de importación.
- Formato existente: `tipo,valor,descripcion,severidad,activo,persona_id,nombre_principal,variantes`.
- Cada CBU/alias/nombre = una fila con el mismo `persona_id` → resuelven a la misma persona.

## 4. Comparación con lo instalado y preservación
- Instalado hoy en el Moto G71: **una sola regla**, `patodm.nx → pato-1 (Pato Encargado)`.
- En el archivo nuevo, `patodm.nx` figura como alias de Pato → **se conserva, no se duplica** (la
  importación actualiza en sitio por `(tipo, valor normalizado)`). `pato-1` suma: 2 CBU, alias
  `patriciodm.mp` y el nombre `Patricio Hugo Diaz Madrid`.
- No hay registros instalados que queden fuera del archivo nuevo (el único, `patodm.nx`, está incluido).
- El historial, la identidad `Celular Pato`, el token cifrado y la cola **no se tocan** con la importación.

## 5. Plan de aplicación (SOLO tras tu confirmación) y recuperación
1. Backup verificable de la base (`run-as ... cat sentinel.db*` a archivo) **antes** de aplicar.
2. Agregar pruebas automatizadas (persona con 1 alias+1 CBU; con varios de cada; resolución por cada
   identificador; ceros iniciales; normalización de alias; duplicados/conflictos; import repetida sin
   duplicar; preservación de `pato-1`/`patodm.nx`; persistencia tras reiniciar). Ejecutar toda la batería.
3. Aprovisionar `import_preview.csv` como CSV de administración (mecanismo existente, en sitio, sin
   borrar). Verificar conteos: 25 personas, 76 identificadores, `patodm.nx` sin duplicar.
4. Recuperación: si algo sale mal, restaurar la base desde el backup (push del `.db` por run-as) — sin
   desinstalar la app ni borrar datos.

**No se aplica nada hasta que confirmes** que las asociaciones son correctas (especialmente el punto de
revisión de Tamara y las 3 Roxy).

## 6. RESULTADO REAL DE LA IMPORTACIÓN (aplicada 2026-10-24)
Tras tu confirmación. Backup previo: `C:\SentinelBuild\backup-db-import-20261024-135317`
(sentinel.db SHA-256 `943d571e…286d`, +wal/shm/prefs). Checkout: `backup-import-*`.

- **Pruebas previas**: 105 automatizadas, 0 fallos (incluye `ImportacionListaNegraTest`, 6: 25 personas /
  76 ids, sin conflictos, variabilidad, CBU 22 díg con ceros, `pato-1` conserva `patodm.nx` y resuelve
  por cada identificador, normalización de alias en mayúsculas, 3 Roxy separadas).
- **Aplicación**: `import_preview.csv` aprovisionado como CSV de administración (en sitio, idempotente,
  sin borrar). Archivo consumido tras importar.
- **Verificación en base (antes → después)**:
  | Métrica | Antes | Después |
  |---|---|---|
  | Reglas (filas) | 1 | **101** |
  | Personas | 1 | **25** |
  | Identificadores (CBU+alias) | 1 | **76** |
  | CBU 22 díg / con ceros | — | **40 / 32** |
  | Conflictos | 0 | **0** |
  | `patodm.nx` | →pato-1 (1) | →pato-1 (**1**, sin duplicar) |
  | Incidentes | 3 | **3** (sin nuevos) |
  | Cola Telegram | 4 ENVIADO | **4 ENVIADO** (sin nuevos) |
- **Persistencia tras reiniciar la app**: 101/25/76 se mantienen ✓.
- **Preservado**: identidad `Celular Pato`, token cifrado, historial y cola intactos. Sin alarmas ni
  alertas durante la importación. Teclado predeterminado Gboard sin cambios.
- **Resolución**: validada por las pruebas automatizadas sobre el CSV exacto importado (cada
  identificador → su persona); el mecanismo de detección en dispositivo ya estaba probado (8 billeteras
  con `patodm.nx`). Una verificación puntual en dispositivo de un identificador nuevo quedó interrumpida
  por cortes de USB (reproducible a pedido).
- **Discrepancias**: ninguna; los conteos coinciden exactamente con la vista previa.
- **Esquema Room**: sin cambios (el modelo ya soportaba múltiples identificadores por persona).
