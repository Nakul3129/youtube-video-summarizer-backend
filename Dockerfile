# Use an official OpenJDK runtime as a parent image
FROM eclipse-temurin:17-jdk

# Set the working directory in the container
WORKDIR /app

# Copy Maven wrapper and pom.xml first to leverage Docker cache
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Give execution permission to the Maven wrapper
RUN chmod +x ./mvnw

# Download dependencies (speeds up builds by caching dependencies)
RUN ./mvnw dependency:go-offline

# Copy the entire project
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests

# Expose port 8080
EXPOSE 8080

# Run the application
CMD ["java", "-jar", "target/YoutubeSummarizer-0.0.1-SNAPSHOT.jar"]

