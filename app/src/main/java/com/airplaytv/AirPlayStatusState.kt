package com.airplaytv

object AirPlayStatusState {
    const val IDLE = "IDLE"
    const val STARTING = "STARTING"
    const val WAITING = "WAITING"     // Servidor activo, esperando conexión
    const val CONNECTED = "CONNECTED" // iPhone conectado
    const val ERROR = "ERROR"
}
