ARG REGISTRY="azsdkengsys.azurecr.io"
FROM ${REGISTRY}/java/jdk-mariner-mvn:jdk11-latest as builder
WORKDIR application
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=layertools -jar application.jar extract

FROM mcr.microsoft.com/openjdk/jdk:11-mariner
WORKDIR application
COPY --from=builder application/dependencies/ ./
COPY --from=builder application/spring-boot-loader/ ./
COPY --from=builder application/snapshot-dependencies/ ./
COPY --from=builder application/application/ ./
COPY login.conf login.conf
COPY configure-kerberos-client.sh /usr/local/bin/configure-kerberos-client.sh
 ## expose the port for remote debugging the Java app.
EXPOSE 7777 7777

## configure kerberos client and run the Java app in the conatiner.
ENTRYPOINT /usr/local/bin/configure-kerberos-client.sh && java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:7777 org.springframework.boot.loader.JarLauncher --scenario=KerberosProxyHttpClientScenario
