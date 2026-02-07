#!/bin/bash

# ==============================================
# Flash Sale Application - Startup Script
# ==============================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_header() {
    echo -e "${BLUE}"
    echo "╔═══════════════════════════════════════════════════════════╗"
    echo "║           🔥 Flash Sale Application Launcher 🔥           ║"
    echo "╚═══════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${BLUE}→ $1${NC}"
}

# Get the directory where the script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

print_header

# Load environment variables if .env file exists
if [ -f ".env" ]; then
    print_info "Loading environment variables from .env file..."
    set -a
    source .env
    set +a
    print_success "Environment variables loaded"
else
    print_warning "No .env file found. Using default values."
    print_info "Copy .env.example to .env and update values if needed."
fi

# Display current configuration
echo ""
echo -e "${BLUE}Current Configuration:${NC}"
echo "  • Database: ${SPRING_DATASOURCE_URL:-jdbc:mysql://localhost:3306/flashsale}"
echo "  • Redis: ${SPRING_REDIS_HOST:-localhost}:${SPRING_REDIS_PORT:-6379}"
echo "  • Kafka: ${SPRING_KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"
echo ""

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Check for required tools
print_info "Checking prerequisites..."

if ! command_exists java; then
    print_error "Java is not installed. Please install JDK 17+."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    print_error "Java 17+ is required. Current version: $JAVA_VERSION"
    exit 1
fi
print_success "Java $JAVA_VERSION detected"

if ! command_exists mvn; then
    print_warning "Maven not found in PATH. Will use ./mvnw if available."
    if [ -f "./mvnw" ]; then
        MVN_CMD="./mvnw"
        print_success "Using Maven Wrapper"
    else
        print_error "Neither mvn nor mvnw found. Please install Maven."
        exit 1
    fi
else
    MVN_CMD="mvn"
    print_success "Maven detected"
fi

# Parse command line arguments
MODE="${1:-run}"

case "$MODE" in
    "docker")
        print_info "Starting with Docker Compose..."
        if ! command_exists docker; then
            print_error "Docker is not installed."
            exit 1
        fi
        
        if ! command_exists docker-compose && ! docker compose version &>/dev/null; then
            print_error "Docker Compose is not installed."
            exit 1
        fi
        
        print_success "Docker detected"
        echo ""
        print_info "Building and starting containers..."
        
        # Use docker compose (v2) or docker-compose (v1)
        if docker compose version &>/dev/null; then
            docker compose up --build
        else
            docker-compose up --build
        fi
        ;;
    
    "build")
        print_info "Building application..."
        $MVN_CMD clean package -DskipTests
        print_success "Build complete! JAR file at target/flashsale-0.0.1-SNAPSHOT.jar"
        ;;
    
    "test")
        print_info "Running tests..."
        $MVN_CMD test
        ;;
    
    "run"|"")
        print_info "Starting application in development mode..."
        echo ""
        print_warning "Ensure MySQL, Redis, and Kafka are running!"
        echo ""
        $MVN_CMD spring-boot:run
        ;;
    
    "jar")
        if [ ! -f "target/flashsale-0.0.1-SNAPSHOT.jar" ]; then
            print_warning "JAR not found. Building first..."
            $MVN_CMD clean package -DskipTests
        fi
        print_info "Running JAR file..."
        java $JAVA_OPTS -jar target/flashsale-0.0.1-SNAPSHOT.jar
        ;;
    
    "help"|"--help"|"-h")
        echo "Usage: ./start.sh [command]"
        echo ""
        echo "Commands:"
        echo "  run     - Run with Maven (default, development mode)"
        echo "  docker  - Start with Docker Compose (all dependencies included)"
        echo "  build   - Build the application JAR"
        echo "  jar     - Run the pre-built JAR file"
        echo "  test    - Run tests"
        echo "  help    - Show this help message"
        echo ""
        echo "Examples:"
        echo "  ./start.sh          # Start in dev mode"
        echo "  ./start.sh docker   # Start with Docker (recommended)"
        echo "  ./start.sh build    # Build JAR only"
        ;;
    
    *)
        print_error "Unknown command: $MODE"
        echo "Run './start.sh help' for usage information."
        exit 1
        ;;
esac
