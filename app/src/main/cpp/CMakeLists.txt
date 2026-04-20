cmake_minimum_required(VERSION 3.22.1)
project("airplaytv")

# ── Dependencias del sistema Android ──────────────────────────────────────────
find_library(log-lib       log)
find_library(android-lib   android)
find_library(OpenSLES-lib  OpenSLES)
find_library(EGL-lib       EGL)
find_library(GLESv2-lib    GLESv2)

# ── UxPlay (fuentes clonadas en el paso de CI) ────────────────────────────────
set(UXPLAY_DIR ${CMAKE_SOURCE_DIR}/uxplay)

# GStreamer — se descarga como binario precompilado para Android en CI
# Variable: GSTREAMER_ROOT_ANDROID apuntada desde el workflow de GitHub Actions
if(DEFINED GSTREAMER_ROOT_ANDROID)
    include(${GSTREAMER_ROOT_ANDROID}/arm64/share/gst-android/ndk-build/plugins.cmake)
    find_package(GStreamer REQUIRED
        COMPONENTS gstreamer gstaudio gstvideo gstnet gstpbutils)
endif()

# ── Librería nativa principal ─────────────────────────────────────────────────
add_library(
    airplaytv
    SHARED

    # Bridge JNI (nuestro código)
    uxplay_bridge.cpp

    # Core UxPlay
    ${UXPLAY_DIR}/lib/airplay_video_service.cpp
    ${UXPLAY_DIR}/lib/raop_service.cpp
    ${UXPLAY_DIR}/lib/mdns_service.cpp
    ${UXPLAY_DIR}/lib/uxplay.cpp
    ${UXPLAY_DIR}/lib/utils.cpp

    # Soporte de red / mDNS
    ${UXPLAY_DIR}/lib/mdns/mdns.c
    ${UXPLAY_DIR}/lib/mdns/mdnsd.c
)

target_include_directories(airplaytv PRIVATE
    ${UXPLAY_DIR}/include
    ${UXPLAY_DIR}/lib
    ${UXPLAY_DIR}/lib/mdns
)

target_compile_options(airplaytv PRIVATE
    -Wall
    -Wno-unused-variable
    -O2
)

target_link_libraries(airplaytv
    ${log-lib}
    ${android-lib}
    ${OpenSLES-lib}
    ${EGL-lib}
    ${GLESv2-lib}
)
