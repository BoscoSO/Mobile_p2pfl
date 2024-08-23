pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
//        maven {
//            name = "ossrh-snapshot"  // Only for snapshot artifacts
//            url = uri("https://oss.sonatype.org/content/repositories/snapshots")
//        }
        mavenLocal()
        gradlePluginPortal()
//        flatDir {
//            dirs("libs")
//        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
//        maven {
//            name = "ossrh-snapshot"  // Only for snapshot artifacts
//            url = uri("https://oss.sonatype.org/content/repositories/snapshots")
//        }
        mavenLocal()
    }
}

rootProject.name = "Mobile_p2pfl"
include(":app")


