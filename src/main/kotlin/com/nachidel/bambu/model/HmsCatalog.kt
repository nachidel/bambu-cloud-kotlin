package com.nachidel.bambu.model

class HmsCatalog(
    entries: Collection<HmsCatalogEntry>
) {

    private val entriesByCode =
        entries.groupBy {
            it.code.value
        }

    /**
     * Retourne toutes les entrées connues
     * pour ce code, toutes familles confondues.
     */
    fun resolveAll(
        code: BambuErrorCode?
    ): List<HmsCatalogEntry> {

        if (
            code == null ||
            code.isNone
        ) {
            return emptyList()
        }

        return entriesByCode[
            code.value
        ].orEmpty()
    }

    /**
     * Résolution pour une famille précise.
     *
     * À utiliser uniquement lorsque nous connaîtrons
     * réellement la famille HMS correspondant
     * à l'imprimante.
     */
    fun resolve(
        code: BambuErrorCode?,
        family: String
    ): HmsCatalogEntry? {

        return resolveAll(code)
            .firstOrNull {
                it.family.equals(
                    family,
                    ignoreCase = true
                )
            }
    }

    /**
     * Retourne les différents textes connus
     * pour un même code.
     */
    fun messages(
        code: BambuErrorCode?
    ): List<String> {

        return resolveAll(code)
            .map {
                it.message
            }
            .distinct()
    }

    /**
     * Retourne un message uniquement si
     * toutes les familles disponibles sont
     * d'accord sur exactement le même texte.
     *
     * Sinon null : aucune décision arbitraire.
     */
    fun unambiguousMessage(
        code: BambuErrorCode?
    ): String? {

        val messages =
            messages(code)

        return if (messages.size == 1) {
            messages.first()
        } else {
            null
        }
    }
}