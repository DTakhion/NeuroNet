package com.pyamsoft.tetherfi.service.net

import android.net.Network
import androidx.annotation.CheckResult

/**
 * * Preferencia de red aguas arriba (Upstream).
 * Define que red preferimos usar para salir a internet.
 */
enum class  UpstreamPref {
    WIFI, // Preferir Wi-Fi (Modo Repetidor/Bridge)
    CELL  // Preferir Datos Moviles (Modo Hotspot tradicional)
}

/**
 * Contrato para el selector de red.
 * * Permite adquirir una red especifica y liberarla cuando ya no se use.
 */
interface  UpstreamNetworkSelector {

    /**
     * Intenta adquirir una red basada en la preferencia.
     *
     * @param preferred La red que el usuario quiere usar idealmente.
     * @param fallback La red de respaldo si la preferida falla (null = no usar fallback).
     * @return La red (Android Network) lista para usar, o null si falla.
     */
    @CheckResult
    suspend fun  acquire(
        preferred: UpstreamPref,
        fallback: UpstreamPref? = UpstreamPref.CELL
    ): Network?

    /**
     * Libera los recursos y callbacks de red registrados.
     * Debe llamarse cuando el proxy se detiene.
     */
    fun release()
}
