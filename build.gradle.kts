import com.android.build.api.dsl.LibraryExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:9.1.1")
        classpath("com.github.recloudstream.gradle:com.lagradost.cloudstream3.gradle.gradle.plugin:-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

subprojects {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
        }
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) =
    extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: LibraryExtension.() -> Unit) {
    extensions.getByName<LibraryExtension>("android").apply { configuration() }
}

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/SafakStream/SafakStream-Extensions")
        authors = listOf("SafakStream")

        // SelcukFlix özel metadata
        if (project.name == "SelcukFlix") {
            description = "SelcukFlix - Türkçe dizi ve film izleme platformu"
            language = "tr"
            tvTypes = listOf("TvSeries", "Movie")
            iconUrl = "https://selcukflix.app/assets/favicon/apple-touch-icon.png"
            version = 1
        }
        // CNCVerse özel metadata
        if (project.name == "CNCVerse") {
            description = "CNC Verse - Netflix, Hotstar, Prime Video ve Disney+ mirror eklentisi (NetMirror/NewTV backend)"
            language = "en"
            tvTypes = listOf("Movie", "TvSeries")
            iconUrl = "https://raw.githubusercontent.com/SafakStream/SafakStream-Extensions/builds/icons/CNCVerse.png"
            version = 1
        }
        // DiziBox özel metadata
        if (project.name == "Dizibox") {
            description = "DiziBox - Türkçe dizi ve film izleme platformu"
            language = "tr"
            tvTypes = listOf("TvSeries", "Movie")
            iconUrl = "https://raw.githubusercontent.com/SafakStream/SafakStream-Extensions/builds/icons/Dizibox.png"
            version = 1
        }
        // DiziPal özel metadata
        if (project.name == "DiziPal") {
            description = "DiziPal - Türkçe dizi ve film izleme platformu"
            language = "tr"
            tvTypes = listOf("TvSeries", "Movie")
            iconUrl = "https://dizipal1822.com/assets/favicon/apple-touch-icon.png"
            version = 1
        }

        // SafakStream Cinema özel metadata
        if (project.name == "SafakStreamCinema") {
            description = "SafakStream Cinema - Binlerce film ve diziyi Full HD kalitesinde, Türkçe altyazı ve dublaj seçenekleriyle keşfedin."
            language = "tr"
            tvTypes = listOf("TvSeries", "Movie")
            iconUrl = "https://raw.githubusercontent.com/SafakStream/SafakStream-Extensions/builds/icons/SafakStreamCinema.png"
            version = 1
        }
    }

    android {
        namespace = "com.safakstream.${project.name.lowercase()}"

        // Termux'taki aapt2, android-36 platformundaki android.jar'ı okuyamıyor.
        // SafakStreamTV için compileSdk/targetSdk 34'e düşürüldü.
        if (project.name == "SafakStreamTV") {
            compileSdk = 34
        } else {
            compileSdk = 36
        }

        defaultConfig {
            minSdk = 21
        }

        lint {
            if (project.name == "SafakStreamTV") {
                targetSdk = 34
            } else {
                targetSdk = 36
            }
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }

        tasks.withType<KotlinJvmCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11)
                freeCompilerArgs.addAll(
                    "-Xno-call-assertions",
                    "-Xno-param-assertions",
                    "-Xno-receiver-assertions",
                    "-Xannotation-default-target=param-property"
                )
            }
        }
    }

    dependencies {
        val implementation by configurations
        val cloudstream by configurations

        cloudstream("com.lagradost:cloudstream3:pre-release")

        implementation(kotlin("stdlib"))
        implementation("com.github.Blatzar:NiceHttp:0.4.11")
        implementation("org.jsoup:jsoup:1.18.1")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.0")
        implementation("com.fasterxml.jackson.core:jackson-databind:2.16.0")

        implementation("androidx.appcompat:appcompat:1.6.1")
        implementation("androidx.core:core-ktx:1.12.0")
        implementation("com.google.android.material:material:1.11.0")
        implementation("androidx.fragment:fragment-ktx:1.6.2")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
