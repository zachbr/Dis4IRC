# build stage
FROM adoptopenjdk/openjdk11:alpine AS BUILD

# install git
RUN apk add git

# copy source into the container
COPY ./ ./

# build with gradle
RUN ./gradlew build 

# find the project version and copy the correct jar to the root
RUN cp build/libs/Dis4IRC-$(./gradlew properties -q | grep "version:" | awk '{print $2}').jar dis4irc.jar

# reset the container so we don't have source in the final build
FROM adoptopenjdk/openjdk11:alpine

# copy the jar
COPY --from=BUILD dis4irc.jar /srv/dis4irc.jar

# set the working dir
WORKDIR /srv

# set the startup command
CMD ["java", "-jar", "dis4irc.jar", "-c", "config/config.hocon"]
