FROM eclipse-temurin:21-jre-alpine

# Set working directory
WORKDIR /app

# Copy the application JAR
COPY build/libs/devops-agent-1.0.0.jar app.jar

# Expose the application port
EXPOSE 8080

# Set environment variables (can be overridden at runtime)
ENV AWS_REGION=us-east-1
ENV JAVA_OPTS=""

# Run the application
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
