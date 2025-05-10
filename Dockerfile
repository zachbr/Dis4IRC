FROM eclipse-temurin:21-jre-alpine
COPY build/libs/Dis4IRC-*.jar /opt/dis4irc/app.jar
RUN mkdir /data
WORKDIR /data
VOLUME ["/data"]
ENV JAVA_OPTS="-Xmx512m"
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /opt/dis4irc/app.jar"]
