# Deployment Guide for Packing List Application

This Spring Boot application can be deployed in several ways. Choose the method that best fits your needs.

## Prerequisites

- Java 17 or higher
- Maven 3.6+ (or use the included `./mvnw` wrapper)
- Docker (for containerized deployments)
- Docker Compose (for local development)

## Quick Start

Use the provided deployment script for the easiest deployment:

```bash
# Deploy locally with Docker Compose (recommended)
./deploy.sh local

# Build executable JAR
./deploy.sh jar

# Build Docker image only
./deploy.sh docker
```

## Deployment Options

### 1. Local Development (Docker Compose) - Recommended

This is the easiest way to run the application locally:

```bash
# Start the application
docker-compose up -d

# View logs
docker-compose logs -f

# Stop the application
docker-compose down
```

The application will be available at: http://localhost:8080

### 2. JAR Deployment

Build and run as a standalone JAR file:

```bash
# Build the JAR
./mvnw clean package

# Run the application
java -jar target/packinglist-0.0.1-SNAPSHOT.jar
```

**Production considerations:**
```bash
# Run with production profile and custom port
java -jar -Dspring.profiles.active=prod -Dserver.port=8080 target/packinglist-0.0.1-SNAPSHOT.jar

# Run as background service
nohup java -jar target/packinglist-0.0.1-SNAPSHOT.jar > app.log 2>&1 &
```

### 3. Docker Deployment

Build and run with Docker:

```bash
# Build the Docker image
docker build -t packinglist-app:latest .

# Run the container
docker run -d \
  --name packinglist \
  -p 8080:8080 \
  -v $(pwd)/uploads:/app/uploads \
  packinglist-app:latest

# View logs
docker logs -f packinglist

# Stop and remove container
docker stop packinglist && docker rm packinglist
```

### 4. Production Deployment

#### Option A: Traditional Server Deployment

1. **Prepare the server:**
   ```bash
   # Install Java 17
   sudo apt update
   sudo apt install openjdk-17-jdk
   
   # Verify installation
   java -version
   ```

2. **Deploy the application:**
   ```bash
   # Copy JAR to server
   scp target/packinglist-0.0.1-SNAPSHOT.jar user@server:/opt/packinglist/
   
   # SSH to server and run
   ssh user@server
   cd /opt/packinglist
   java -jar packinglist-0.0.1-SNAPSHOT.jar
   ```

3. **Create systemd service (Linux):**
   ```bash
   sudo nano /etc/systemd/system/packinglist.service
   ```
   
   ```ini
   [Unit]
   Description=Packing List Application
   After=network.target
   
   [Service]
   Type=simple
   User=packinglist
   WorkingDirectory=/opt/packinglist
   ExecStart=/usr/bin/java -jar packinglist-0.0.1-SNAPSHOT.jar
   Restart=always
   RestartSec=10
   
   [Install]
   WantedBy=multi-user.target
   ```
   
   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable packinglist
   sudo systemctl start packinglist
   ```

#### Option B: Cloud Deployment

**AWS EC2:**
1. Launch EC2 instance with Java 17
2. Upload JAR file
3. Run with security groups allowing port 8080

**Google Cloud Platform:**
1. Use Google Cloud Run for serverless deployment
2. Build container image and push to GCR
3. Deploy from container image

**Azure:**
1. Use Azure Container Instances
2. Or deploy to Azure App Service

**Heroku:**
1. Add `Procfile`:
   ```
   web: java -jar target/packinglist-0.0.1-SNAPSHOT.jar --server.port=$PORT
   ```
2. Deploy with Git

#### Option C: Kubernetes Deployment

1. **Create Kubernetes manifests:**

   `k8s/deployment.yaml`:
   ```yaml
   apiVersion: apps/v1
   kind: Deployment
   metadata:
     name: packinglist-app
   spec:
     replicas: 2
     selector:
       matchLabels:
         app: packinglist-app
     template:
       metadata:
         labels:
           app: packinglist-app
       spec:
         containers:
         - name: packinglist-app
           image: packinglist-app:latest
           ports:
           - containerPort: 8080
           env:
           - name: SPRING_PROFILES_ACTIVE
             value: "k8s"
           livenessProbe:
             httpGet:
               path: /actuator/health
               port: 8080
             initialDelaySeconds: 30
             periodSeconds: 10
   ---
   apiVersion: v1
   kind: Service
   metadata:
     name: packinglist-service
   spec:
     selector:
       app: packinglist-app
     ports:
     - port: 80
       targetPort: 8080
     type: LoadBalancer
   ```

2. **Deploy to Kubernetes:**
   ```bash
   kubectl apply -f k8s/
   ```

## Environment Configuration

### Application Properties

Create different `application-{profile}.properties` files for different environments:

**application-prod.properties:**
```properties
server.port=8080
logging.level.root=WARN
logging.level.com.example.packinglist=INFO
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB
```

**application-docker.properties:**
```properties
server.port=8080
logging.level.root=INFO
```

### Environment Variables

The application supports these environment variables:

- `SERVER_PORT`: Server port (default: 8080)
- `SPRING_PROFILES_ACTIVE`: Active profile (dev, prod, docker)
- `JAVA_OPTS`: JVM options

## Monitoring and Health Checks

The application includes Spring Boot Actuator endpoints:

- Health check: `GET /actuator/health`
- Application info: `GET /actuator/info`
- Metrics: `GET /actuator/metrics`

## Troubleshooting

### Common Issues

1. **Port already in use:**
   ```bash
   # Find process using port 8080
   lsof -i :8080
   # Kill the process
   kill -9 <PID>
   ```

2. **Java version issues:**
   ```bash
   # Check Java version
   java -version
   # Should be 17 or higher
   ```

3. **Memory issues:**
   ```bash
   # Increase heap size
   java -Xmx2g -jar packinglist-0.0.1-SNAPSHOT.jar
   ```

4. **OCR functionality issues:**
   - Ensure proper fonts are installed
   - Check file permissions for uploaded files
   - Verify Aspose.OCR license (if required)

### Logs

- Application logs: Check console output or `app.log`
- Docker logs: `docker logs packinglist`
- Docker Compose logs: `docker-compose logs -f`

## Security Considerations

1. **Change default ports** in production
2. **Use HTTPS** with proper SSL certificates
3. **Implement authentication** if needed
4. **Limit file upload sizes** appropriately
5. **Use environment variables** for sensitive configuration
6. **Keep dependencies updated** regularly

## Performance Optimization

1. **JVM tuning:**
   ```bash
   java -Xms512m -Xmx2g -XX:+UseG1GC -jar packinglist-0.0.1-SNAPSHOT.jar
   ```

2. **Enable compression:**
   ```properties
   server.compression.enabled=true
   server.compression.mime-types=text/html,text/xml,text/plain,text/css,text/javascript,application/javascript,application/json
   ```

3. **Use reverse proxy** (Nginx) for static content and load balancing

## Backup and Recovery

1. **Application data:** Backup uploaded files and any persistent data
2. **Configuration:** Keep environment-specific configurations in version control
3. **Database:** If using a database, implement regular backups

---

For more help, check the application logs or contact the development team.