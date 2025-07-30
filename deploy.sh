#!/bin/bash

# Deployment script for Packing List Application
set -e

echo "🚀 Starting deployment process..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    print_error "Docker is not installed. Please install Docker first."
    exit 1
fi

# Check if Docker Compose is installed
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    print_error "Docker Compose is not installed. Please install Docker Compose first."
    exit 1
fi

# Parse command line arguments
DEPLOYMENT_TYPE=${1:-"local"}

case $DEPLOYMENT_TYPE in
    "local")
        print_status "Deploying locally with Docker Compose..."
        
        # Build and start the application
        docker-compose down --remove-orphans
        docker-compose build --no-cache
        docker-compose up -d
        
        print_status "Application is starting up..."
        print_status "Waiting for health check..."
        
        # Wait for the application to be healthy
        for i in {1..30}; do
            if curl -f http://localhost:8080/actuator/health >/dev/null 2>&1; then
                print_status "✅ Application is healthy and ready!"
                print_status "🌐 Access your application at: http://localhost:8080"
                exit 0
            fi
            echo -n "."
            sleep 2
        done
        
        print_warning "Application might still be starting up. Check logs with: docker-compose logs -f"
        ;;
        
    "jar")
        print_status "Building JAR file..."
        
        # Clean and build
        ./mvnw clean package -DskipTests
        
        if [ -f "target/packinglist-0.0.1-SNAPSHOT.jar" ]; then
            print_status "✅ JAR built successfully!"
            print_status "📦 JAR location: target/packinglist-0.0.1-SNAPSHOT.jar"
            print_status "🚀 Run with: java -jar target/packinglist-0.0.1-SNAPSHOT.jar"
        else
            print_error "JAR build failed!"
            exit 1
        fi
        ;;
        
    "docker")
        print_status "Building Docker image..."
        
        # Build Docker image
        docker build -t packinglist-app:latest .
        
        print_status "✅ Docker image built successfully!"
        print_status "🚀 Run with: docker run -p 8080:8080 packinglist-app:latest"
        ;;
        
    *)
        echo "Usage: $0 [local|jar|docker]"
        echo ""
        echo "Deployment options:"
        echo "  local  - Deploy with Docker Compose (default)"
        echo "  jar    - Build executable JAR file"
        echo "  docker - Build Docker image only"
        exit 1
        ;;
esac