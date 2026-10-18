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
    "DiziPal",
    "HDFilmCehennemi",
    "StreamingCommunity",
    "SelcukFlix",
    "InatTV",
    "DiziFilmizle",
    "BeyazElma",
    "Streamed",
    "TrGoals",
    "Betmatik",
    "Dizibox",
    "Filmmakinesi",
    "SafakStreamHub"
)
include("MahsunSports")
include("PapazSports")
