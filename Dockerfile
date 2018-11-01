FROM openjdk:9

# install 'stress' for GET/POST /stress
RUN apt-get update && apt-get install -y stress

# Run `mvn clean package` before
COPY target/de.jwausle.kubernetes.jar /docker-java-home/

EXPOSE 8080

WORKDIR /docker-java-home/
CMD ["java", "-jar", "de.jwausle.kubernetes.jar", "-XX:+PrintFlagsFinal", "-Xmx128m", "-Xms64m"]