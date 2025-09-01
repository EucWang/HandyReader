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
        gradlePluginPortal()
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/apache-snapshots") }
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://jcenter.bintray.com")}
        maven { url = uri("https://repo1.maven.org/maven2/") }
        maven { url = uri("https://repo.spring.io/release") }
        maven { url = uri("https://repository.jboss.org/maven2") }
        maven { url = uri("https://repo.jenkins-ci.org/public/") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        gradlePluginPortal()
        maven { url = uri("https://jcenter.bintray.com")}
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/apache-snapshots") }
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://jcenter.bintray.com")}
        maven { url = uri("https://repo1.maven.org/maven2/") }
        maven { url = uri("https://repo.spring.io/release") }
        maven { url = uri("https://repository.jboss.org/maven2") }
        maven { url = uri("https://repo.jenkins-ci.org/public/") }
    }
}

rootProject.name = "handyreader"
include(":app")
include(":bookparser")
include(":mobi")
include(":bookread")
include(":base")
include(":jp2forandroid")
include(":text2speech")
