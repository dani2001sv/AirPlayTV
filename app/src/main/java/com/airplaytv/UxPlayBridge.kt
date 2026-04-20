package com.airplaytv

/**
 * Bridge JNI entre Kotlin y la librería nativa UxPlay (C++).
 * Todos los métodos nativos están implementados en uxplay_bridge.cpp
 */
object UxPlayBridge {

    init {
        System.loadLibrary("airplaytv")
    }

    /**
     * Inicia el servidor AirPlay.
     * @return puntero al servidor nativo (Long), o 0 si fallo
     */
    fun startServer(
        deviceName: String,
        port: Int,
        onClientConnected: (clientName: String) -> Unit,
        onClientDisconnected: () -> Unit,
        onError: (error: String) -> Unit
    ): Long {
        return nativeStartServer(deviceName, port, object : ServerCallbacks {
            override fun onConnected(clientName: String) = onClientConnected(clientName)
            override fun onDisconnected() = onClientDisconnected()
            override fun onError(message: String) = onError(message)
        })
    }

    fun stopServer(serverPtr: Long) {
        nativeStopServer(serverPtr)
    }

    // Métodos nativos — implementados en C++ (uxplay_bridge.cpp)
    private external fun nativeStartServer(
        deviceName: String,
        port: Int,
        callbacks: ServerCallbacks
    ): Long

    private external fun nativeStopServer(serverPtr: Long)

    /** Interface de callbacks invocada desde C++ via JNI */
    interface ServerCallbacks {
        fun onConnected(clientName: String)
        fun onDisconnected()
        fun onError(message: String)
    }
}
