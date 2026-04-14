FROM eclipse-temurin:21-jdk-jammy
WORKDIR /src
COPY target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
