FROM openjdk:8-alpine

COPY target/uberjar/collaborative-todo-app.jar /collaborative-todo-app/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/collaborative-todo-app/app.jar"]
