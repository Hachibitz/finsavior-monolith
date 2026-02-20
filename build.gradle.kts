plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.4.4"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "1.9.25"
}

group = "br.com.finsavior.monolith"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
	maven { url = uri("https://repo.spring.io/snapshot") }
	maven { url = uri("https://repo.spring.io/milestone") }
}

extra["springCloudVersion"] = "2024.0.1"
val cloudStarterBootstrapVersion = "4.2.1"
val kotlinLoggingVersion = "2.0.11"
val jwtVersion = "0.12.1"
val mySqlConnectorVersion = "9.2.0"
val swaggerVersion = "2.8.6"
val rabbitMQMockVersion = "1.1.1"
val langChain4jVersion = "1.0.0-beta3"
val stripeJavaVersion = "29.0.0"
val firebaseAdminVersion = "9.4.3"
val wsSchildAudioConverterVersion = "3.5.0"
val pdfBoxVersion = "2.0.29"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.springframework.cloud:spring-cloud-starter-config")
	implementation("org.springframework.cloud:spring-cloud-starter-bootstrap:$cloudStarterBootstrapVersion")
	implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("io.jsonwebtoken:jjwt:$jwtVersion")
	implementation("com.mysql:mysql-connector-j:$mySqlConnectorVersion")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$swaggerVersion")
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.springframework.boot:spring-boot-starter-amqp")
	implementation("com.github.fridujo:rabbitmq-mock:$rabbitMQMockVersion")
	implementation("dev.langchain4j:langchain4j:$langChain4jVersion")
	implementation("dev.langchain4j:langchain4j-open-ai:$langChain4jVersion")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("com.stripe:stripe-java:$stripeJavaVersion")
	implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
	implementation("com.google.firebase:firebase-admin:$firebaseAdminVersion")
    implementation("ws.schild:jave-all-deps:$wsSchildAudioConverterVersion")
    implementation("org.apache.pdfbox:pdfbox:$pdfBoxVersion")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.springframework.security:spring-security-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
	}
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
