package org.mozilla.guardian.tunnel

import android.content.Intent
import android.os.Parcel
import android.util.Base64

fun Intent.toBase64(): String{
    val parcel = Parcel.obtain()
    this.writeToParcel(parcel, 0)
    val bytes = parcel.marshall()
    parcel.recycle()

    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
    return base64
}