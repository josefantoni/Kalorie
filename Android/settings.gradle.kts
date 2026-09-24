pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Kalorie"

includeBuild("../MacroKit") {
    dependencySubstitution {
        substitute(module("kalorie:MacroKit")).using(project(":"))
    }
}
includeBuild("../MealKit") {
    dependencySubstitution {
        substitute(module("kalorie:MealKit")).using(project(":"))
    }
}
includeBuild("../TextKit") {
    dependencySubstitution {
        substitute(module("kalorie:TextKit")).using(project(":"))
    }
}
includeBuild("../ExportKit") {
    dependencySubstitution {
        substitute(module("kalorie:ExportKit")).using(project(":"))
    }
}

include(":app")
