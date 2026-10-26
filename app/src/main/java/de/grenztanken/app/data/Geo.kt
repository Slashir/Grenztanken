package de.grenztanken.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

object Geo {

    /** Ort oder PLZ in Deutschland → (lat, lng). Nutzt den Geocoder des Geräts. */
    suspend fun geocode(context: Context, query: String): Pair<Double, Double>? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.GERMANY)
        val q = "${query.trim()}, Deutschland"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(15_000L) {
                suspendCancellableCoroutine<Pair<Double, Double>?> { cont ->
                    geocoder.getFromLocationName(q, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            val a = addresses.firstOrNull()
                            if (cont.isActive) cont.resume(a?.let { it.latitude to it.longitude })
                        }

                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume(null)
                        }
                    })
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(q, 1)?.firstOrNull()?.let { it.latitude to it.longitude }
                }.getOrNull()
            }
        }
    }

    /** Ungefährer aktueller Standort. Berechtigung muss vorher erteilt sein. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            for (provider in providers) {
                val location = withTimeoutOrNull(10_000L) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val signal = CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        try {
                            lm.getCurrentLocation(provider, signal, context.mainExecutor) { loc ->
                                if (cont.isActive) cont.resume(loc)
                            }
                        } catch (e: Exception) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }
                if (location != null) return location
            }
        }
        return providers
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }
}
