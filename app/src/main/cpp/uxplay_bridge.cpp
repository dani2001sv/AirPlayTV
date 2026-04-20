/**
 * uxplay_bridge.cpp
 * Puente JNI entre la capa Kotlin y la librería C++ de UxPlay.
 *
 * Cada método "native" declarado en UxPlayBridge.kt tiene
 * su implementación aquí con la firma exacta que espera JNI.
 */

#include <jni.h>
#include <android/log.h>
#include <string>
#include <memory>
#include <functional>

// Headers de UxPlay
#include "uxplay.h"

#define TAG "AirPlayTV-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ─── Estructura que agrupa el estado del servidor ──────────────────────────────
struct ServerContext {
    JavaVM*   jvm        = nullptr;
    jobject   callbacks  = nullptr;   // UxPlayBridge.ServerCallbacks
    jmethodID onConnected    = nullptr;
    jmethodID onDisconnected = nullptr;
    jmethodID onError        = nullptr;
    UxPlay*   server     = nullptr;
};

// ─── Helpers JNI ──────────────────────────────────────────────────────────────
static JNIEnv* getEnv(JavaVM* jvm) {
    JNIEnv* env = nullptr;
    int status = jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (status == JNI_EDETACHED) {
        jvm->AttachCurrentThread(&env, nullptr);
    }
    return env;
}

// ─── Callbacks llamados desde UxPlay (hilo nativo) ───────────────────────────
static void onClientConnectedCb(const char* clientName, void* userdata) {
    auto* ctx = static_cast<ServerContext*>(userdata);
    JNIEnv* env = getEnv(ctx->jvm);
    if (!env || !ctx->callbacks) return;

    jstring jName = env->NewStringUTF(clientName ? clientName : "iPhone");
    env->CallVoidMethod(ctx->callbacks, ctx->onConnected, jName);
    env->DeleteLocalRef(jName);

    LOGI("Client connected: %s", clientName);
}

static void onClientDisconnectedCb(void* userdata) {
    auto* ctx = static_cast<ServerContext*>(userdata);
    JNIEnv* env = getEnv(ctx->jvm);
    if (!env || !ctx->callbacks) return;

    env->CallVoidMethod(ctx->callbacks, ctx->onDisconnected);
    LOGI("Client disconnected");
}

static void onErrorCb(const char* message, void* userdata) {
    auto* ctx = static_cast<ServerContext*>(userdata);
    JNIEnv* env = getEnv(ctx->jvm);
    if (!env || !ctx->callbacks) return;

    jstring jMsg = env->NewStringUTF(message ? message : "Unknown error");
    env->CallVoidMethod(ctx->callbacks, ctx->onError, jMsg);
    env->DeleteLocalRef(jMsg);

    LOGE("Server error: %s", message);
}

// ─── JNI: nativeStartServer ───────────────────────────────────────────────────
extern "C" JNIEXPORT jlong JNICALL
Java_com_airplaytv_UxPlayBridge_nativeStartServer(
        JNIEnv*   env,
        jobject   /* thiz */,
        jstring   jDeviceName,
        jint      port,
        jobject   jCallbacks)
{
    LOGI("nativeStartServer called, port=%d", (int)port);

    auto* ctx = new ServerContext();

    // Guardar referencia a la JVM (para callbacks desde hilos nativos)
    env->GetJavaVM(&ctx->jvm);

    // Referencia global a los callbacks (sobrevive fuera de este frame JNI)
    ctx->callbacks = env->NewGlobalRef(jCallbacks);

    // Resolver los métodos del interface ServerCallbacks
    jclass clazz = env->GetObjectClass(jCallbacks);
    ctx->onConnected    = env->GetMethodID(clazz, "onConnected",    "(Ljava/lang/String;)V");
    ctx->onDisconnected = env->GetMethodID(clazz, "onDisconnected", "()V");
    ctx->onError        = env->GetMethodID(clazz, "onError",        "(Ljava/lang/String;)V");

    if (!ctx->onConnected || !ctx->onDisconnected || !ctx->onError) {
        LOGE("Failed to resolve callback methods");
        delete ctx;
        return 0L;
    }

    // Convertir nombre de dispositivo
    const char* deviceNameCStr = env->GetStringUTFChars(jDeviceName, nullptr);
    std::string deviceName(deviceNameCStr);
    env->ReleaseStringUTFChars(jDeviceName, deviceNameCStr);

    // Configurar y arrancar UxPlay
    UxPlayConfig config;
    config.deviceName        = deviceName.c_str();
    config.port              = (int)port;
    config.onConnected       = onClientConnectedCb;
    config.onDisconnected    = onClientDisconnectedCb;
    config.onError           = onErrorCb;
    config.userdata          = ctx;
    config.maxFPS            = 60;
    config.audioEnabled      = true;
    config.videoMirrorEnabled = true;

    ctx->server = UxPlay::create(config);

    if (!ctx->server || !ctx->server->start()) {
        LOGE("Failed to start UxPlay server");
        env->DeleteGlobalRef(ctx->callbacks);
        delete ctx;
        return 0L;
    }

    LOGI("UxPlay server started: %s on port %d", deviceName.c_str(), (int)port);
    return reinterpret_cast<jlong>(ctx);
}

// ─── JNI: nativeStopServer ────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_com_airplaytv_UxPlayBridge_nativeStopServer(
        JNIEnv*   env,
        jobject   /* thiz */,
        jlong     serverPtr)
{
    if (serverPtr == 0L) return;

    auto* ctx = reinterpret_cast<ServerContext*>(serverPtr);

    if (ctx->server) {
        ctx->server->stop();
        delete ctx->server;
        ctx->server = nullptr;
    }

    if (ctx->callbacks) {
        env->DeleteGlobalRef(ctx->callbacks);
        ctx->callbacks = nullptr;
    }

    delete ctx;
    LOGI("UxPlay server stopped and resources freed");
}
