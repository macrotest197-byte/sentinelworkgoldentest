# SENTINEL — Medición en pantalla de las ocho billeteras

Registro de la medición hecha en dispositivo con el visor de diagnóstico (`a11y_diag` +
`a11y_last_screen.txt`), recorriendo las ocho billeteras de la lista permitida con un CBU y un alias
de la lista negra. Sirve de fundamento para cada umbral y cada regla del matcher de pantalla.

- Dispositivo: Celular Pato (`ZY22DZ4BHM`), paquete `com.sentinel.recaudacion.debug`.
- Fecha: 29/10/2026.
- Vía medida: pantalla completa con retardo de 1200 ms, umbral propio por vía.

## Resultado general

Las ocho billeteras exponen el nombre del titular en al menos una pantalla previa a confirmar la
transferencia. La vía pantalla es viable en todas, con distinto grado de limpieza del texto.

## Detalle por billetera

| Billetera | Dónde aparece el titular | Particularidad | Regla que exigió |
|---|---|---|---|
| Mercado Pago | Pantalla de destinatario, tras resolver el CBU | Nodo «Recientes» con nombres ajenos al destinatario; un CBU inventado se rechaza con «Ingresá un dato válido» | Umbral de identificador 0.99; el CBU de prueba tiene que ser real |
| Ualá | Confirmación de destinatario con nombre, alias, CBU y CUIT | Texto limpio y en orden natural | Ninguna extra |
| Brubank | Titular de la cuenta junto al CUIT | Apellido truncado | Tolerancia a último token truncado; umbral de nombre 0.96 |
| Personal Pay | Pantalla de monto con nombre y CUIT | Iniciales «PH» en un nodo | Tolerancia a iniciales |
| Buepp | Titular de la cuenta | Nombre invertido y banco entre paréntesis | Tolerancia a inversión y a paréntesis |
| Lemon | Titular de la cuenta | Sin particularidad registrada | Ninguna extra |
| AstroPay | Titular de la cuenta | Sin particularidad registrada | Ninguna extra |
| ARQ | Titular de la cuenta | Sin particularidad registrada | Ninguna extra |

## Separadores en documentos

El CUIT aparece con espacios en varias pantallas. El normalizador los colapsa antes de comparar, así
que un documento con separadores matchea igual que uno corrido.

## Umbrales derivados

- `MIN_CONFIDENCE` = 0.95: piso general, por debajo no se alerta por ninguna vía.
- `MIN_NAME_CONFIDENCE` = 0.96: piso para nombres en pantalla, porque el apellido truncado de Brubank
  deja la coincidencia por debajo de 0.99.
- `MIN_IDENTIFIER_CONFIDENCE` = 0.99: identificadores (CBU, CVU, alias, CUIT) exigen coincidencia casi
  exacta para no alertar por un número parecido.
- `ALLOW_REVERSED_ORDER`: tolera el orden invertido del nombre (Buepp).
- `ALLOW_ABBREVIATED_FIRST_NAME`: tolera el nombre abreviado o en iniciales (Personal Pay).
- `ALLOW_TRUNCATED_LAST_NAME`: tolera el apellido truncado, con un mínimo de 3 letras (Brubank).
- `ALLOW_WIDE_SPAN`: admite palabras de relleno entre nombre y apellido.
- Umbral propio de la vía pantalla: más alto que el del campo, porque la pantalla trae texto de
  relleno.

## Deduplicación

La clave de evento lleva un número de sesión que sube en cada cambio de ventana. Dentro de una
pantalla, alias, CBU y nombre de la misma persona producen un solo mensaje. Al cambiar de pantalla,
de billetera o al reabrir la app, la clave es nueva y vuelve a alertar, para no tragarse un reintento.

## Límites medidos

- La vía pantalla depende de los nodos de cada versión: una actualización de la billetera puede
  cambiarlos.
- No hay bloqueo: solo aviso. El operador puede confirmar igual.
- En pantallas con `FLAG_SECURE` o vistas que no exponen texto, el escaneo no ve nada.
- El mensaje de Telegram incluye la billetera resuelta por paquete en foco.