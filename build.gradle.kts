import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.gradle.DokkaTask

group = "org.litote"
val kotlinpoet: String by project

plugins {
    kotlin("jvm") version "1.3.10"
    `maven-publish`
    signing
}

repositories {
    mavenLocal()
    jcenter()
}

dependencies {
    compile(kotlin("stdlib"))
    compile(kotlin("reflect"))
    compile("com.squareup", "kotlinpoet", kotlinpoet)
}

buildscript {
    dependencies {
        classpath("org.jetbrains.dokka", "dokka-gradle-plugin", "0.9.17")
    }
}

apply {
    plugin("org.jetbrains.dokka")
}

tasks.withType<DokkaTask> {
    outputFormat = "html"
    outputDirectory = "$buildDir/docs/kdoc"
    externalDocumentationLink(delegateClosureOf<DokkaConfiguration.ExternalDocumentationLink.Builder> {
        url = uri("https://docs.spring.io/spring-framework/docs/5.1.0.BUILD-SNAPSHOT/javadoc-api/").toURL()
    })
}

val sourcesJar by tasks.registering(Jar::class) {
    classifier = "sources"
    from(sourceSets.main.get().allSource)
}

val javadocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.withType<DokkaTask>() )
    classifier = "javadoc"
    from("$buildDir/docs/kdoc")
}

publishing {
    repositories {
        maven {
            val releasesRepoUrl = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2")
            val snapshotsRepoUrl = uri("https://oss.sonatype.org/content/repositories/snapshots")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials {
                username = property("sonatypeUsername").toString()
                password = property("sonatypePassword").toString()
            }
        }
    }
    publications {
        register("mavenJava", MavenPublication::class) {
            from(components["java"])
            artifact(sourcesJar.get())
            artifact(javadocJar.get())
            pom {
                name.set("KGenerator")
                description.set("Utility library for kapt generation")
                url.set("https://github.com/Litote/kgenerator")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("zigzago")
                        name.set("Julien Buret")
                        email.set("zigzago@litote.org")
                    }
                }
                scm {
                    connection.set("scm:git:git@github.com:Litote/kgenerator.git")
                    developerConnection.set("scm:git:git@github.com:Litote/kgenerator.git")
                    url.set("git@github.com:Litote/kgenerator.git")
                }
            }
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}

