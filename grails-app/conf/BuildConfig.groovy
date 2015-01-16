grails.project.work.dir = 'target'

grails.project.dependency.resolver = "maven"
grails.project.dependency.resolution = {
    inherits 'global'
    log 'warn'

    repositories {
        mavenRepo "http://aftprod00.corp.pgcore.com:8081/artifactory/repo"
        grailsCentral()
        grailsPlugins()
        mavenCentral()
    }

    plugins {
        runtime ":asset-pipeline:1.9.5"

        build(":release:3.0.1") {
            export = false
        }
    }
}
