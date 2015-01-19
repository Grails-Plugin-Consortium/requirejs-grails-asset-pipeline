grails.project.work.dir = 'target'

grails.project.dependency.resolver = "maven"
grails.project.dependency.resolution = {
    inherits 'global'
    log 'warn'

    repositories {
        grailsCentral()
        grailsPlugins()
        mavenCentral()
    }

    plugins {
        runtime ":asset-pipeline:1.9.5"

        build(":release:3.0.1",
                ":rest-client-builder:2.0.3") {
            export = false
        }
    }
}
