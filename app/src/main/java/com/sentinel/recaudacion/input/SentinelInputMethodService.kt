package com.sentinel.recaudacion.input

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import android.widget.TextView
import com.sentinel.recaudacion.BuildConfig
import com.sentinel.recaudacion.SentinelApp
import com.sentinel.recaudacion.data.model.BlacklistPerson
import com.sentinel.recaudacion.data.model.PersonIdentifier
import com.sentinel.recaudacion.data.model.RuleType
import com.sentinel.recaudacion.detection.BlacklistPersonMatcher
import com.sentinel.recaudacion.detection.ConfirmedAlertProcessor
import com.sentinel.recaudacion.detection.InputFieldPolicy
import com.sentinel.recaudacion.detection.KeyboardEpisodeTracker
import com.sentinel.recaudacion.detection.ResolvedMatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Teclado SENTINEL (opt-in). Solo inspecciona campos que la app dueña marca explícitamente
 * (ver [InputFieldPolicy], fail-closed); excluye contraseñas/PIN/OTP. No guarda el texto ni
 * registra teclas. Al detectar un destinatario de la lista negra, en un campo marcado:
 *  - **previene** la entrada (limpia el dato cuando la plataforma lo permite),
 *  - avisa al operador fuera de las teclas,
 *  - y dispara el circuito original (incidente + alarma + Telegram) vía [ConfirmedAlertProcessor].
 * El campo de demostración interno no dispara alarma ni Telegram.
 */
class SentinelInputMethodService : InputMethodService() {
    private enum class Layer { LETTERS, NUMBERS, SYMBOLS }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var persons: List<BlacklistPerson> = emptyList()
    private var rulesLoaded = false
    private var fieldAllowed = false
    private var demoField = false
    private var suggestionsAllowed = false
    private val episode = KeyboardEpisodeTracker()
    private var uppercase = false
    private var layer = Layer.LETTERS
    private var keyRows: LinearLayout? = null
    private var switchLabel: TextView? = null
    private var warningLabel: TextView? = null
    private var smartSlots: List<TextView> = emptyList()

    private val demoPerson = BlacklistPerson(
        id = "demo-teclado",
        primaryName = "Destinatario de prueba",
        identifiers = listOf(PersonIdentifier(RuleType.ALIAS, DEMO_MATCH_VALUE)),
    )

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        episode.reset()
        uppercase = false
        layer = Layer.LETTERS
        persons = emptyList()
        rulesLoaded = false
        demoField = attribute?.let {
            it.packageName == BuildConfig.APPLICATION_ID && InputFieldPolicy.isDemoMarked(it.privateImeOptions)
        } == true
        fieldAllowed = attribute?.let {
            InputFieldPolicy.canInspect(it.inputType, it.imeOptions, it.privateImeOptions) &&
                (!InputFieldPolicy.isDemoMarked(it.privateImeOptions) || demoField)
        } == true
        suggestionsAllowed = attribute?.let {
            InputFieldPolicy.suggestionsAllowed(it.inputType, it.imeOptions)
        } == true
        setWarning(null)
        refreshKeyboardState()
        updateSuggestions()
        if (fieldAllowed) serviceScope.launch {
            val stored = withContext(Dispatchers.IO) {
                (application as SentinelApp).blacklistRepository.activePersons()
            }
            persons = if (demoField) stored + demoPerson else stored
            rulesLoaded = true
            refreshKeyboardState()
        }
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(8))
            setBackgroundColor(Color.rgb(38, 50, 56))
        }
        warningLabel = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(Color.rgb(176, 0, 32), dp(8))
            visibility = View.GONE
        }
        root.addView(warningLabel, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        root.addView(buildToolbar(), LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(4) })
        keyRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(keyRows)
        switchLabel = TextView(this).apply {
            text = "Cambiar teclado"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setOnClickListener { switchKeyboard() }
        }
        root.addView(switchLabel, LinearLayout.LayoutParams(-1, dp(52)))
        buildKeys()
        refreshKeyboardState()
        return root
    }

    /**
     * Barra inteligente sobre las teclas: una zona central de 3 posiciones (izquierda · centro ·
     * derecha) que muestra, según el contexto, el botón **Pegar** centrado (si hay portapapeles)
     * o hasta 3 **sugerencias** (centro = la más probable). El acceso 🌐 para cambiar de teclado
     * queda separado, a la derecha. Altura estable: alternar contenido no mueve las teclas.
     */
    private fun buildToolbar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        // 3 posiciones de igual tamaño (evita que las teclas se muevan al alternar Pegar/sugerencias).
        val slots = mutableListOf<TextView>()
        repeat(3) { i ->
            val slot = TextView(this).apply {
                textSize = 14f
                gravity = Gravity.CENTER
                maxLines = 1
                setTextColor(Color.WHITE)
                background = rounded(Color.rgb(64, 76, 82), dp(8))
                isClickable = true
                visibility = View.INVISIBLE
                setOnClickListener { handleSlot(i) }
            }
            slots.add(slot)
            bar.addView(slot, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        smartSlots = slots
        // 🌐 cambiar teclado, separado de la zona inteligente (posición fija a la derecha).
        val globe = TextView(this).apply {
            text = "🌐"
            textSize = 16f
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(48, 62, 68), dp(8))
            isClickable = true
            setOnClickListener { switchKeyboard() }
        }
        bar.addView(globe, LinearLayout.LayoutParams(0, dp(40), 0.5f).apply { setMargins(dp(3), 0, dp(2), 0) })
        return bar
    }

    /** Palabra actual (token alfabético inmediatamente antes del cursor), de forma transitoria. */
    private fun currentWord(): String {
        val before = currentInputConnection?.getTextBeforeCursor(48, 0)?.toString().orEmpty()
        return before.takeLastWhile { !it.isWhitespace() }
    }

    /** Resuelve la acción de una posición (Pegar o aplicar sugerencia), según su etiqueta. */
    private fun handleSlot(index: Int) {
        val action = smartSlots.getOrNull(index)?.tag as? String ?: return
        when {
            action == PASTE_TAG -> pasteFromClipboard()
            action.startsWith(SUG_PREFIX) -> applySuggestion(action.removePrefix(SUG_PREFIX))
        }
    }

    /**
     * Actualiza la zona inteligente según el contexto. La DECISIÓN está en
     * [KeyboardToolbarPlanner] (función pura, testeable). No almacena nada ni lee el portapapeles.
     */
    private fun updateSuggestions() {
        val slots = smartSlots
        if (slots.size < 3) return
        val warningVisible = warningLabel?.visibility == View.VISIBLE
        val suggestions = if (suggestionsAllowed && !warningVisible)
            SpanishSuggester.suggest(currentWord()) else emptyList()
        val plan = KeyboardToolbarPlanner.plan(
            hasClipText = clipboardHasText(),
            suggestionsAllowed = suggestionsAllowed,
            warningVisible = warningVisible,
            suggestions = suggestions,
        )
        // centro = slots[1]; izquierda = slots[0]; derecha = slots[2].
        applySlot(slots[0], plan.left?.let { SUG_PREFIX + it }, plan.left)
        if (plan.centerIsPaste) applySlot(slots[1], PASTE_TAG, "📋 Pegar")
        else applySlot(slots[1], plan.center?.let { SUG_PREFIX + it }, plan.center)
        applySlot(slots[2], plan.right?.let { SUG_PREFIX + it }, plan.right)
    }

    private fun applySlot(slot: TextView, tag: String?, label: String?) {
        if (tag == null || label == null) {
            slot.visibility = View.INVISIBLE
            slot.tag = null
        } else {
            slot.text = label
            slot.tag = tag
            slot.visibility = View.VISIBLE
        }
    }

    /**
     * ¿Hay texto pegable en el portapapeles? Consulta SOLO la descripción (tipo MIME), no el
     * contenido, por lo que no dispara el aviso de acceso al portapapeles ni lee datos. El
     * contenido se lee únicamente cuando el usuario toca Pegar.
     */
    private fun clipboardHasText(): Boolean {
        val cm = getSystemService(CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return false
        if (!cm.hasPrimaryClip()) return false
        val desc = cm.primaryClipDescription ?: return false
        return desc.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN) ||
            desc.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_HTML)
    }

    /** Reemplaza la palabra actual por la sugerencia elegida (solo al tocarla; nunca automático). */
    private fun applySuggestion(suggestion: String) {
        val connection = currentInputConnection ?: return
        val word = currentWord()
        connection.beginBatchEdit()
        if (word.isNotEmpty()) connection.deleteSurroundingText(word.length, 0)
        connection.commitText("$suggestion ", 1)
        connection.endBatchEdit()
        // La inspección se dispara sola vía onUpdateSelection; no se fuerza aquí (evita bucles).
    }

    /**
     * Pega el contenido actual del portapapeles. El IME puede leer el portapapeles mientras está
     * activo; se usa SOLO cuando el usuario toca Pegar (él decide). No se guarda historial.
     * La evaluación de detección se dispara sola (onUpdateSelection) si el campo está habilitado.
     */
    private fun pasteFromClipboard() {
        val connection = currentInputConnection ?: return
        val clip = (getSystemService(CLIPBOARD_SERVICE) as? android.content.ClipboardManager)
            ?.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (clip.isNotEmpty()) connection.commitText(clip, 1)
    }

    private fun buildKeys() {
        val rows = keyRows ?: return
        rows.removeAllViews()
        when (layer) {
            Layer.LETTERS -> buildLetterRows(rows)
            Layer.NUMBERS -> buildNumberRows(rows)
            Layer.SYMBOLS -> buildSymbolRows(rows)
        }
        val bottom = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        val leftMode = if (layer == Layer.LETTERS) "?123" else "ABC"
        bottom.addView(key(leftMode, special = true) {
            layer = if (layer == Layer.LETTERS) Layer.NUMBERS else Layer.LETTERS
            buildKeys()
        }, weight(1.4f))
        bottom.addView(key(",", special = true) { commit(",") }, weight(.8f))
        if (layer != Layer.LETTERS) bottom.addView(key("=\\<", special = true) {
            layer = if (layer == Layer.SYMBOLS) Layer.NUMBERS else Layer.SYMBOLS
            buildKeys()
        }, weight(.8f))
        bottom.addView(key("Español", special = true) { commit(" ") }, weight(3.4f))
        bottom.addView(key(".", special = true) { commit(".") }, weight(.8f))
        bottom.addView(key("✓", accent = true) { enter() }, weight(1.4f))
        rows.addView(bottom)
    }

    private fun buildLetterRows(rows: LinearLayout) {
        rows.addView(letterRow("qwertyuiop"))
        rows.addView(letterRow("asdfghjklñ"))
        val third = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        third.addView(key("⇧", special = true) { uppercase = !uppercase; buildKeys() }, weight(1.45f))
        "zxcvbnm".forEach { ch -> third.addView(key(charLabel(ch)) { commit(charLabel(ch)) }, weight(1f)) }
        third.addView(key("⌫", special = true) { backspace() }, weight(1.45f))
        rows.addView(third)
    }

    private fun letterRow(chars: String): LinearLayout =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
            chars.forEach { ch ->
                val shown = charLabel(ch)
                addView(key(shown) { commit(shown) }, weight())
            }
        }

    private fun buildNumberRows(rows: LinearLayout) {
        rows.addView(symbolRow(listOf("1","2","3","4","5","6","7","8","9","0")))
        rows.addView(symbolRow(listOf("@","#","$","_","&","-","+","(",")","/")))
        rows.addView(symbolRow(listOf("=\\<","*","\"","'",":",";","!","?"), leading = { layer = Layer.SYMBOLS; buildKeys() }, trailing = { backspace() }))
    }

    private fun buildSymbolRows(rows: LinearLayout) {
        rows.addView(symbolRow(listOf("~","`","|","•","√","π","÷","×","§","Δ")))
        rows.addView(symbolRow(listOf("£","¢","€","¥","^","°","=","{","}","\\")))
        rows.addView(symbolRow(listOf("?123","%","©","®","™","✓","[","]"), leading = { layer = Layer.NUMBERS; buildKeys() }, trailing = { backspace() }))
    }

    private fun symbolRow(symbols: List<String>, leading: (() -> Unit)? = null, trailing: (() -> Unit)? = null): LinearLayout =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
            if (leading != null) addView(key(symbols.first(), special = true, action = leading), weight(1.45f))
            val start = if (leading != null) 1 else 0
            for (i in start until symbols.size) {
                val symbol = symbols[i]
                addView(key(symbol) { commit(symbol) }, weight())
            }
            if (trailing != null) addView(key("⌫", special = true, action = trailing), weight(1.45f))
        }

    private fun key(
        label: String,
        special: Boolean = false,
        accent: Boolean = false,
        action: () -> Unit,
    ): TextView = TextView(this).apply {
        text = label
        textSize = when { label == "Español" -> 16f; label.length > 2 -> 13f; else -> 22f }
        gravity = Gravity.CENTER
        maxLines = 1
        setTextColor(if (accent) Color.WHITE else Color.rgb(230, 234, 236))
        typeface = if (accent) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        background = rounded(if (accent) Color.rgb(112, 177, 170) else if (special) Color.rgb(48, 62, 68) else Color.rgb(64, 76, 82), dp(8))
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun weight(value: Float = 1f) = LinearLayout.LayoutParams(0, dp(52), value).apply {
        setMargins(dp(2), dp(3), dp(2), dp(3))
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius.toFloat()
    }

    private fun charLabel(ch: Char): String = if (uppercase) ch.uppercaseChar().toString() else ch.toString()

    private fun refreshKeyboardState() {
        // El teclado SIEMPRE permite escribir (es un teclado). La inspección/bloqueo es
        // aparte y solo ocurre en campos marcados (fail-closed), vía inspectFocusedText().
        keyRows?.visibility = View.VISIBLE
        switchLabel?.visibility = View.VISIBLE
    }

    private fun setWarning(text: String?) {
        warningLabel?.apply {
            if (text.isNullOrBlank()) { visibility = View.GONE } else { this.text = text; visibility = View.VISIBLE }
        }
    }

    private fun switchKeyboard() {
        if (!switchToNextInputMethod(false)) {
            startActivity(android.content.Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun commit(value: String) {
        // Escribir siempre funciona; la inspección la dispara onUpdateSelection.
        currentInputConnection?.commitText(value, 1)
    }

    private fun backspace() {
        val connection = currentInputConnection ?: return
        val selected = connection.getSelectedText(0)
        if (!selected.isNullOrEmpty()) connection.commitText("", 1) else connection.deleteSurroundingText(1, 0)
    }

    private fun enter() {
        val connection = currentInputConnection ?: return
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) connection.performEditorAction(action)
        else {
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // Cubre escritura, PEGADO, selección, autocompletado y acciones del editor:
        // cualquier cambio del texto re-evalúa la coincidencia (no depende de Enter).
        inspectFocusedText()
        // Las sugerencias se recalculan tras la inspección (y se ocultan si hay aviso activo).
        updateSuggestions()
    }

    /** Analiza SOLO el campo actual (marcado). No se guarda el texto. */
    private fun inspectFocusedText() {
        if (!fieldAllowed || !rulesLoaded) return
        val connection: InputConnection = currentInputConnection ?: return
        val before = connection.getTextBeforeCursor(MAX_CONTEXT, 0)?.toString().orEmpty()
        val selected = connection.getSelectedText(0)?.toString().orEmpty()
        val after = connection.getTextAfterCursor(MAX_CONTEXT, 0)?.toString().orEmpty()
        val text = before + selected + after
        val resolved: ResolvedMatch? = BlacklistPersonMatcher.resolve(text, persons)
        val key = resolved?.let { "${it.personId ?: "amb"}|${it.matchType.name}|${it.matchedValue}" }
        val outcome = episode.onInspect(key, textEmpty = text.isBlank())
        if (outcome.newDetection && resolved != null) {
            val who = resolved.personName
                ?: if (resolved.ambiguous) "persona ambigua (sin resolver)" else "persona sin resolver"
            setWarning("⚠ Lista negra: $who — ${resolved.matchedValue}. Entrada bloqueada.")
            blockField(connection) // prevención best-effort: limpia el dato del campo
            if (!demoField) {
                serviceScope.launch {
                    runCatching {
                        ConfirmedAlertProcessor.process(
                            app = application as SentinelApp,
                            resolved = resolved,
                            source = "Teclado SENTINEL · campo marcado",
                            eventKey = key ?: resolved.matchedValue,
                            sourceApp = currentInputEditorInfo?.packageName,
                            authorizedExcerpt = null, // no se almacena el texto del operador
                        )
                    }
                }
            }
        }
        if (outcome.clearWarning) setWarning(null)
    }

    /** Limpia el contenido del campo marcado (prevención best-effort). */
    private fun blockField(connection: InputConnection) {
        runCatching {
            connection.beginBatchEdit()
            connection.performContextMenuAction(android.R.id.selectAll)
            connection.commitText("", 1)
            connection.finishComposingText()
            connection.endBatchEdit()
        }
    }

    override fun onFinishInput() {
        persons = emptyList()
        rulesLoaded = false
        episode.reset()
        setWarning(null)
        super.onFinishInput()
        refreshKeyboardState()
        updateSuggestions()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        persons = emptyList()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val MAX_CONTEXT = 1024
        const val DEMO_MATCH_VALUE = "sentinel.demo"
        // Etiquetas de acción de las posiciones de la barra inteligente.
        private const val PASTE_TAG = "PASTE"
        private const val SUG_PREFIX = "SUG:"
    }
}
