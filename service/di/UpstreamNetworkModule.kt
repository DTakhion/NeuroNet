package com.pyamsoft.tetherfi.service.di

import android.content.Context
import android.net.ConnectivityManager
import com.pyamsoft.tetherfi.service.net.AndroidUpstreamNetworkSelector
import com.pyamsoft.tetherfi.service.net.UpstreamNetworkSelector
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UpstreamNetworkModule {

    /**
     * BINDING (Vinculación):
     * Enseña a Hilt que la implementación real de 'UpstreamNetworkSelector'
     * es la clase 'AndroidUpstreamNetworkSelector' que creamos en el Paso 2.
     */
    @Binds
    @Singleton
    internal abstract fun bindUpstreamSelector(
        impl: AndroidUpstreamNetworkSelector
    ): UpstreamNetworkSelector

    companion object {
        /**
         * PROVIDER (Proveedor):
         * Enseña a Hilt cómo obtener el 'ConnectivityManager' del sistema Android,
         * necesario para que nuestro selector funcione.
         */
        @Provides
        @Singleton
        @JvmStatic // Optimización para Dagger/Hilt
        internal fun provideConnectivityManager(
            @ApplicationContext context: Context
        ): ConnectivityManager {
            // Obtenemos el servicio de sistema de forma segura usando el Contexto de la Aplicación
            val service = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            return requireNotNull(service as? ConnectivityManager) {
                "ConnectivityManager no está disponible en este dispositivo"
            }
        }
    }
}