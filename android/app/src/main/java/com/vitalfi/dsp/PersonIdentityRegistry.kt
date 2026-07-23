package com.vitalfi.dsp

/**
 * Asigna firmas estables Persona A/B/C y nombres manuales del rescatista.
 * No es identificación biométrica real — solo etiquetas de campo.
 */
class PersonIdentityRegistry {
    private val customNames = linkedMapOf<Int, String>()
    private val signatureLetter = linkedMapOf<Int, Char>()
    private var nextLetter = 'A'

    fun reset() {
        customNames.clear()
        signatureLetter.clear()
        nextLetter = 'A'
    }

    fun ensureSignature(id: Int): Char =
        signatureLetter.getOrPut(id) {
            val letter = nextLetter
            if (nextLetter < 'Z') nextLetter = nextLetter + 1
            letter
        }

    fun displayLabel(id: Int): String {
        customNames[id]?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return "Persona ${ensureSignature(id)}"
    }

    fun shortCode(id: Int): String = "P${ensureSignature(id)}"

    fun rename(id: Int, name: String) {
        val cleaned = name.trim().take(28)
        if (cleaned.isEmpty()) {
            customNames.remove(id)
        } else {
            ensureSignature(id)
            customNames[id] = cleaned
        }
    }

    fun customName(id: Int): String? = customNames[id]
}
