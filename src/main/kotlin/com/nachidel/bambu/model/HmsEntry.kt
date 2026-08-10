package com.nachidel.bambu.model

data class HmsEntry(
    val attr: Long? = null,
    val code: Long? = null
) {

    val fullCode: BambuErrorCode?
        get() {

            val attr =
                attr ?: return null

            val code =
                code ?: return null

            return BambuErrorCode.fromHex(
                "%08X%08X".format(
                    attr and 0xFFFFFFFFL,
                    code and 0xFFFFFFFFL
                )
            )
        }
}