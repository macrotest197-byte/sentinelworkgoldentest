package com.sentinel.recaudacion.input

import java.util.Locale

/**
 * Motor de sugerencias/autocorrección **local y sin estado**: no almacena lo que el usuario
 * escribe ni construye historial. Trabaja solo con la palabra actual que recibe como parámetro
 * (extraída de forma transitoria del campo) y un diccionario estático de español.
 *
 * Diseño conservador para evitar reemplazos inesperados:
 *  - Las sugerencias se APLICAN únicamente cuando el usuario toca una (nunca automáticamente).
 *  - Nunca propone identificadores de lista negra: solo palabras comunes del diccionario.
 *  - Es determinista (orden estable por frecuencia y alfabético).
 */
object SpanishSuggester {

    /** Devuelve hasta [max] sugerencias para [currentWord] (completados + correcciones). */
    fun suggest(currentWord: String, max: Int = 3): List<String> {
        val word = currentWord.trim()
        if (word.length < 2) return emptyList()
        val lower = word.lowercase(Locale.getDefault())
        // No sugerir sobre tokens que parecen datos (dígitos, puntos, @): no son palabras.
        if (lower.any { it.isDigit() } || lower.contains('@') || lower.contains('.')) return emptyList()

        val out = LinkedHashSet<String>()
        // 1) Completados por prefijo (la propia palabra no se sugiere a sí misma).
        DICTIONARY.asSequence()
            .filter { it.length > lower.length && it.startsWith(lower) }
            .sortedBy { it.length }
            .forEach { if (out.size < max) out.add(matchCase(word, it)) }
        // 2) Correcciones por distancia de edición 1 si la palabra no está en el diccionario.
        if (out.size < max && lower !in DICTIONARY) {
            DICTIONARY.asSequence()
                .filter { kotlin.math.abs(it.length - lower.length) <= 1 && editDistanceAtMost1(lower, it) }
                .sortedBy { it.length }
                .forEach { if (out.size < max) out.add(matchCase(word, it)) }
        }
        return out.toList()
    }

    /** Mejor corrección para una palabra probablemente mal escrita, o null si no aplica. */
    fun correct(currentWord: String): String? {
        val lower = currentWord.trim().lowercase(Locale.getDefault())
        if (lower.length < 3 || lower in DICTIONARY) return null
        if (lower.any { it.isDigit() } || lower.contains('@') || lower.contains('.')) return null
        val best = DICTIONARY.asSequence()
            .filter { kotlin.math.abs(it.length - lower.length) <= 1 && editDistanceAtMost1(lower, it) }
            .minByOrNull { it.length } ?: return null
        return matchCase(currentWord, best)
    }

    /** Conserva mayúscula inicial si la palabra original la tenía. */
    private fun matchCase(original: String, suggestion: String): String =
        if (original.isNotEmpty() && original[0].isUpperCase())
            suggestion.replaceFirstChar { it.uppercaseChar() }
        else suggestion

    /** true si [a] y [b] difieren en a lo sumo una edición (inserción, borrado o sustitución). */
    private fun editDistanceAtMost1(a: String, b: String): Boolean {
        if (a == b) return true
        val la = a.length; val lb = b.length
        if (kotlin.math.abs(la - lb) > 1) return false
        var i = 0; var j = 0; var edits = 0
        while (i < la && j < lb) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            when {
                la == lb -> { i++; j++ }          // sustitución
                la > lb -> i++                     // borrado en a
                else -> j++                        // inserción en a
            }
        }
        if (i < la || j < lb) edits++              // carácter final sobrante
        return edits <= 1
    }

    /**
     * Diccionario estático de palabras comunes en español (minúsculas, sin tildes problemáticas
     * para prefijos). No es exhaustivo; sirve para completar y corregir texto corriente. No
     * contiene identificadores ni datos sensibles.
     */
    private val DICTIONARY: Set<String> = setOf(
        "hola", "buenas", "buenos", "dias", "tardes", "noches", "gracias", "favor", "por",
        "para", "pero", "porque", "como", "cuando", "donde", "quien", "cual", "esto", "esta",
        "este", "eso", "esa", "aqui", "alla", "ahora", "luego", "antes", "despues", "siempre",
        "nunca", "tambien", "tampoco", "mucho", "poco", "mas", "menos", "bien", "mal", "mejor",
        "peor", "todo", "nada", "algo", "alguien", "nadie", "cosa", "cosas", "persona", "gente",
        "dinero", "plata", "pago", "pagar", "pago", "cuenta", "banco", "transferencia", "transferir",
        "enviar", "envio", "recibir", "monto", "total", "numero", "nombre", "apellido", "documento",
        "telefono", "mensaje", "mensajes", "hablar", "decir", "hacer", "tener", "poder", "querer",
        "necesito", "necesitas", "quiero", "puedo", "puede", "vamos", "vamos", "vale", "dale",
        "listo", "lista", "perfecto", "claro", "seguro", "problema", "solucion", "ayuda", "ayudar",
        "trabajo", "trabajar", "oficina", "casa", "hoy", "ayer", "manana", "semana", "mes", "ano",
        "hora", "minuto", "momento", "rato", "tiempo", "primero", "segundo", "ultimo", "nuevo",
        "nueva", "viejo", "grande", "chico", "pequeno", "largo", "corto", "rapido", "lento",
        "facil", "dificil", "importante", "urgente", "cuidado", "atencion", "aviso", "alerta",
        "revisar", "verificar", "confirmar", "cancelar", "aceptar", "rechazar", "esperar", "llegar",
        "salir", "entrar", "volver", "venir", "estar", "andar", "seguir", "parar", "cambiar",
        "amor", "amiga", "amigo", "familia", "hijo", "hija", "madre", "padre", "hermano", "hermana",
        "comer", "beber", "dormir", "despertar", "recien", "temprano", "noche", "mediodia",
        "celular", "mensaje", "whatsapp", "telegram", "correo", "email", "usuario", "clave",
        "contrasena", "codigo", "seguridad", "sistema", "aplicacion", "pantalla", "teclado",
    )
}
