pluginManagement {
    includeBuild("build-logic")
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
        maven("https://jitpack.io") {
            content { includeGroup("com.github.SeineEloquenz.compose-kit") }
        }
    }
}

rootProject.name = "openmaps"

include(
    ":app:android",
    ":ui",
    ":map",
    ":core:model",
    ":core:platform",
    ":data:transit",
    ":data:geocoding",
    ":data:storage",
    ":data:routing",
    ":data:fuel",
    ":data:weather",
    ":data:reviews",
    ":data:flights",
)

val localComposeKitPath = providers.environmentVariable("LOCAL_COMPOSE_KIT").orNull?.takeIf { it.isNotBlank() }
if (localComposeKitPath != null) {
    includeBuild(localComposeKitPath) {
        dependencySubstitution {
            substitute(module("com.github.SeineEloquenz.compose-kit:lib")).using(project(":lib"))
        }
    }
}
