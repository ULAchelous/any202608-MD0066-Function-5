package io.ula.aiy.mb.utils

/**
 * Temporary in-memory holder for passing large data between activities
 * without exceeding the Binder transaction size limit.
 *
 * Use [putImageBase64] to store data before launching an activity, and [takeImageBase64] to
 * retrieve (and clear) it inside the target activity's onCreate.
 */
object SharedDataHolder {

    private var imageData: String? = null

    fun putImageBase64(data: String) {
        imageData = data
    }

    /**
     * Returns the stored image data and clears the reference so memory
     * is not held longer than necessary.
     */
    fun takeImageBase64(): String? {
        val data = imageData
        imageData = null
        return data
    }
}
