# Build the WAR (no tests), then deploy it on Tomcat
FROM maven:3.9-eclipse-temurin-11 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B -Ppackage -DskipTests package

FROM tomcat:9.0-jre11-temurin
RUN rm -rf /usr/local/tomcat/webapps/*

COPY --from=build /app/target/sample-java-scim-server.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080
CMD ["catalina.sh", "run"]
