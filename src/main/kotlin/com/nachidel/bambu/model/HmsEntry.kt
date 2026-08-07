package com.nachidel.bambu.model

data class HmsEntry(
    val attr: Long? = null,
    val code: Long? = null
) {

    val fullCode: String?
        get() {

            val attr =
                attr ?: return null

            val code =
                code ?: return null

            return "%08X%08X".format(
                attr,
                code
            )
        }
}