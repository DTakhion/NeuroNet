package com.pyamsoft.tetherfi.server

/**
 * Representa el rol operativo del dispositivo dentro de la cadena de proxys.
 */
enum class ProxyRole {
  /** Dispositivo origen: comparte Internet, no necesita upstream. */
  SERVER_ONLY,

  /** Dispositivo repetidor: consume upstream de un nodo anterior y reexpone proxy. */
  RELAY,

  /** Dispositivo terminal: actúa como cliente, no expone proxy. */
  CLIENT_ONLY,
}
