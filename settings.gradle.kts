pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "SafakStream-Extensions"

include(
    "YouTube",
    "CineStream",
    "DiziPal",
    "HDFilmCehennemi",
    "StreamingCommunity",
    "SelcukFlix",
    "NetMirror"
)
