#!/usr/bin/env bash
set -euo pipefail

# Keep the same Spring Boot 3.x version
# across all independently deployable services.
BOOT_VERSION="3.4.3"
BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WRAPPER_TEMPLATE="${HOME}/.m2/wrapper-template"

to_camel_case() {
  local input="$1"
  local result=""
  IFS='-' read -ra parts <<< "$input"
  for part in "${parts[@]}"; do
    result+="$(tr '[:lower:]' '[:upper:]' <<< "${part:0:1}")${part:1}"
  done
  echo "$result"
}

generate_service() {
  local SERVICE_NAME="$1"
  local PACKAGE_NAME="$2"
  local DEPENDENCIES="$3"
  local TARGET_DIR="$4"

  echo "Generating ${SERVICE_NAME}..."
  mkdir -p "$TARGET_DIR"

  local PACKAGE_PATH="${PACKAGE_NAME//.//}"
  local CLASS_PREFIX
  CLASS_PREFIX="$(to_camel_case "$SERVICE_NAME")"
  local APP_CLASS="${CLASS_PREFIX}Application"
  local TEST_CLASS="${CLASS_PREFIX}ApplicationTests"

  mkdir -p "${TARGET_DIR}/src/main/java/${PACKAGE_PATH}"
  mkdir -p "${TARGET_DIR}/src/main/resources"
  mkdir -p "${TARGET_DIR}/src/test/java/${PACKAGE_PATH}"

  # 1. Generate pom.xml
  local DEPS_XML=""
  if [[ "$DEPENDENCIES" =~ (web) ]]; then
    DEPS_XML+='
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-web</artifactId>
		</dependency>'
  fi
  if [[ "$DEPENDENCIES" =~ (actuator) ]]; then
    DEPS_XML+='
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-actuator</artifactId>
		</dependency>'
  fi
  if [[ "$DEPENDENCIES" =~ (security) ]]; then
    DEPS_XML+='
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security</artifactId>
		</dependency>'
  fi
  if [[ "$DEPENDENCIES" =~ (validation) ]]; then
    DEPS_XML+='
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation</artifactId>
		</dependency>'
  fi
  if [[ "$DEPENDENCIES" =~ (data-jpa) ]]; then
    DEPS_XML+='
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa</artifactId>
		</dependency>'
  fi
  if [[ "$DEPENDENCIES" =~ (postgresql) ]]; then
    DEPS_XML+='
		<dependency>
			<groupId>org.postgresql</groupId>
			<artifactId>postgresql</artifactId>
			<scope>runtime</scope>
		</dependency>'
  fi
  if [[ "$DEPENDENCIES" =~ (websocket) ]]; then
    DEPS_XML+='
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-websocket</artifactId>
		</dependency>'
  fi
  if [[ "$DEPENDENCIES" =~ (amqp|rabbitmq) ]]; then
    DEPS_XML+='
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-amqp</artifactId>
		</dependency>'
  fi

  local TEST_DEPS_XML='
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-test</artifactId>
			<scope>test</scope>
		</dependency>'
  if [[ "$DEPENDENCIES" =~ (security) ]]; then
    TEST_DEPS_XML+='
		<dependency>
			<groupId>org.springframework.security</groupId>
			<artifactId>spring-security-test</artifactId>
			<scope>test</scope>
		</dependency>'
  fi
  if [[ "$DEPENDENCIES" =~ (amqp|rabbitmq) ]]; then
    TEST_DEPS_XML+='
		<dependency>
			<groupId>org.springframework.amqp</groupId>
			<artifactId>spring-rabbit-test</artifactId>
			<scope>test</scope>
		</dependency>'
  fi

  cat > "${TARGET_DIR}/pom.xml" <<POM_EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>${BOOT_VERSION}</version>
		<relativePath/> <!-- lookup parent from repository -->
	</parent>
	<groupId>com.swiftdrop</groupId>
	<artifactId>${SERVICE_NAME}</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<name>${SERVICE_NAME}</name>
	<description>${SERVICE_NAME} for SwiftDrop</description>
	<properties>
		<java.version>21</java.version>
	</properties>
	<dependencies>${DEPS_XML}${TEST_DEPS_XML}
	</dependencies>

	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>
		</plugins>
	</build>
</project>
POM_EOF

  # 2. Generate Application Class
  cat > "${TARGET_DIR}/src/main/java/${PACKAGE_PATH}/${APP_CLASS}.java" <<APP_EOF
package ${PACKAGE_NAME};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ${APP_CLASS} {

    public static void main(String[] args) {
        SpringApplication.run(${APP_CLASS}.class, args);
    }
}
APP_EOF

  # 3. Generate Application Test Class
  cat > "${TARGET_DIR}/src/test/java/${PACKAGE_PATH}/${TEST_CLASS}.java" <<TEST_EOF
package ${PACKAGE_NAME};

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ${TEST_CLASS} {

    @Test
    void contextLoads() {
    }
}
TEST_EOF

  # 4. Generate application.properties
  cat > "${TARGET_DIR}/src/main/resources/application.properties" <<PROP_EOF
spring.application.name=${SERVICE_NAME}
PROP_EOF

  # 5. Copy Maven Wrapper
  if [ -d "$WRAPPER_TEMPLATE" ]; then
    cp -a "${WRAPPER_TEMPLATE}/mvnw" "${TARGET_DIR}/"
    cp -a "${WRAPPER_TEMPLATE}/mvnw.cmd" "${TARGET_DIR}/"
    cp -a "${WRAPPER_TEMPLATE}/.mvn" "${TARGET_DIR}/"
  fi
  chmod +x "${TARGET_DIR}/mvnw"

  # 6. Service .gitignore
  cat > "${TARGET_DIR}/.gitignore" <<'GIT_EOF'
target/
!.mvn/wrapper/maven-wrapper.jar
!**/src/main/**/target/
!**/src/test/**/target/

### STS ###
.apt_generated
.classpath
.factorypath
.project
.settings
.springBeans
.sts4-cache

### IntelliJ IDEA ###
.idea
*.iws
*.iml
*.ipr

### NetBeans ###
/nbproject/private/
/nbbuild/
/dist/
/nbproject/load-impl.xml
/nbproject/packaging-impl.xml

### VS Code ###
.vscode/
GIT_EOF

  echo "Completed: ${SERVICE_NAME}"
}

# API Gateway
generate_service   "api-gateway"   "com.swiftdrop.gateway"   "web,actuator"   "${BASE_DIR}/api-gateway"

# Business microservices
generate_service   "auth-service"   "com.swiftdrop.auth"   "web,actuator,security,validation"   "${BASE_DIR}/services/auth-service"

generate_service   "parcel-service"   "com.swiftdrop.parcel"   "web,actuator,data-jpa,postgresql,validation,amqp"   "${BASE_DIR}/services/parcel-service"

generate_service   "pricing-service"   "com.swiftdrop.pricing"   "web,actuator,data-jpa,postgresql,validation"   "${BASE_DIR}/services/pricing-service"

generate_service   "merchant-service"   "com.swiftdrop.merchant"   "web,actuator,data-jpa,postgresql,security,validation"   "${BASE_DIR}/services/merchant-service"

generate_service   "dispatch-service"   "com.swiftdrop.dispatch"   "web,actuator,data-jpa,postgresql,validation,amqp"   "${BASE_DIR}/services/dispatch-service"

generate_service   "courier-service"   "com.swiftdrop.courier"   "web,actuator,data-jpa,postgresql,security,validation"   "${BASE_DIR}/services/courier-service"

generate_service   "tracking-service"   "com.swiftdrop.tracking"   "web,actuator,websocket,amqp"   "${BASE_DIR}/services/tracking-service"

generate_service   "notification-service"   "com.swiftdrop.notification"   "web,actuator,amqp"   "${BASE_DIR}/services/notification-service"

generate_service   "webhook-service"   "com.swiftdrop.webhook"   "web,actuator,amqp"   "${BASE_DIR}/services/webhook-service"

generate_service   "reporting-service"   "com.swiftdrop.reporting"   "web,actuator,data-jpa,postgresql"   "${BASE_DIR}/services/reporting-service"

echo ""
echo "All SwiftDrop Spring Boot applications generated."
