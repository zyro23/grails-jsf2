grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"

grails.project.dependency.resolution = {
	
    inherits "global"
	
    log "warn"
	
    repositories {
        grailsCentral()
        mavenCentral()
    }

	dependencies {
		compile "org.glassfish:javax.faces:2.1.20"
	}

    plugins {
        build(":tomcat:$grailsVersion", ":release:2.0.3", ":rest-client-builder:1.0.2") {
            export = false
        }
		compile(":hibernate:$grailsVersion") {
			export = false
		}
    }
	
}
