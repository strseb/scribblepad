package org.mozilla.guardian.tunnel

import android.content.Context
import android.content.SharedPreferences
import android.os.Parcel
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wireguard.crypto.Key

class EncryptedSettings(aPref: SharedPreferences){
    val pref: SharedPreferences = aPref;

    constructor(context: Context): this(openSharedPref(context)){}


    var lastConfiguration: TunnelConfig?
        get(){
            val string = pref.getString("TUNNEL_CONFIG", "") ?: return null;
            val bytes = Base64.decode(string, Base64.DEFAULT)
            val parcel = Parcel.obtain()
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            val config = TunnelConfig.CREATOR.createFromParcel(parcel)
            parcel.recycle()
            return config
        }
        set(config){
            if(config == null){
                pref.edit().remove("TUNNEL_CONFIG").apply()
                return
            }
            val parcel = Parcel.obtain();
            config.writeToParcel(parcel, TunnelConfig.PARCELABLE_WRITE_RETURN_VALUE)
            val bytes = parcel.marshall();
            parcel.recycle();
            val string = Base64.encodeToString(bytes, Base64.DEFAULT);
            pref.edit().apply{
                putString("TUNNEL_CONFIG", string)
            }.apply()
        }


    val wireguardPublicKey : com.wireguard.crypto.Key?
        get() {
            val pk = this.wireguardPrivateKey ?: return null;
            val set = com.wireguard.crypto.KeyPair(pk);
            return set.publicKey
        }
    var wireguardPrivateKey : com.wireguard.crypto.Key?
        get(){
            val keyBase64 = pref.getString("WG_PRIV_KEY", "") ?: return null;
            return Key.fromBase64(keyBase64);
        }
        set(key){
            if (key != null) {
                pref.edit().putString("WG_PRIV_KEY", key.toBase64()).apply()
            }
        }


    companion object{
        private fun openSharedPref(context: Context): SharedPreferences{
            try {
                val mainKey = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val sharedPrefsFile = "com.mozilla.vpn.secure.prefs"
                val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
                    context.applicationContext,
                    sharedPrefsFile,
                    mainKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
                return sharedPreferences
            } catch (e: Exception) {
                Log.e("Android-Prefs", "Getting Encryption Storage failed, plaintext fallback")
                return context.getSharedPreferences("com.mozilla.vpn.prefrences", Context.MODE_PRIVATE)
            }
        }
    }

}

