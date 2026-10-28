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
    "SelcukFlix",
    "InatTV",
    "DiziFilmizle",
    "BeyazElma",
    "Streamed",
    "TrGoals",
    "Betmatik",
    "Dizibox",
    "SafakStreamHub",
    "MahsunSports",
    "PapazSports",
    "SafakStreamCinema",
    "Dizilla",
    "SineWix"
)
