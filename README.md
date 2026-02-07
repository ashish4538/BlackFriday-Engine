# 🔥 Flash Sale Application

A high-throughput, scalable Flash Sale system built with **Spring Boot 3.2**, designed to handle massive concurrent purchase requests while maintaining data consistency and preventing overselling.

---

## 🏗️ Architecture Overview

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Browser   │───▶│  Spring Boot│───▶│    Kafka    │
│   Client    │    │    App      │    │   (Events)  │
└─────────────┘    └──────┬──────┘    └─────────────┘
                          │
            ┌─────────────┼─────────────┐
            ▼             ▼             ▼
      ┌──────────┐  ┌──────────┐  ┌──────────┐
      │  MySQL   │  │  Redis   │  │  Kafka   │
      │(Orders & │  │ (Stock & │  │(Order    │
      │Products) │  │  Locks)  │  │ Events)  │
      └──────────┘  └──────────┘  └──────────┘
```

---

## ✨ Key Features

### 🛒 Core Functionality
- **Product Management** - Full CRUD operations for products (Admin-only create/update/delete)
- **Flash Sale Purchases** - High-concurrency purchase handling with atomic stock operations
- **Order Processing** - Persistent order storage with event-driven architecture

### 🔒 Concurrency & Reliability
- **Distributed Locking** - Redis-based locks via Redisson prevent race conditions
- **Atomic Stock Management** - Redis atomic counters ensure accurate inventory
- **Event-Driven Processing** - Kafka integration for reliable order event handling

### 🔐 Security
- **Spring Security** - Form-based and HTTP Basic authentication
- **Role-Based Access** - Admin and User roles with `@PreAuthorize` annotations
- **Secure Password Storage** - BCrypt password encoding

### 💳 Payment System
- **Strategy Pattern** - Pluggable payment methods
- **Supported Methods**: Credit Card, Cryptocurrency

---

## 🛠️ Tech Stack

| Category | Technology |
|----------|------------|
| **Framework** | Spring Boot 3.2.2 |
| **Language** | Java 17 |
| **Database** | MySQL 8.0 |
| **Caching/Locks** | Redis (Redisson) |
| **Messaging** | Apache Kafka |
| **Security** | Spring Security |
| **ORM** | Spring Data JPA / Hibernate |
| **Build** | Maven |
| **Containerization** | Docker & Docker Compose |
| **Orchestration** | Helm Charts (Kubernetes) |

---

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Docker & Docker Compose (recommended)
- OR: MySQL, Redis, Kafka running locally

### Option 1: Docker (Recommended) 🐳

```bash
# Clone the repository
git clone <repository-url>
cd flash-sale-app

# Start all services
./start.sh docker
```

This starts:
- Flash Sale Application on `http://localhost:8080`
- MySQL on port `3306`
- Redis on port `6379`
- Kafka on port `9092`

### Option 2: Local Development

1. **Setup environment:**
   ```bash
   cp .env.example .env
   # Edit .env with your configuration
   ```

2. **Start dependencies** (MySQL, Redis, Kafka)

3. **Run the application:**
   ```bash
   ./start.sh run
   ```

### Start Script Options

```bash
./start.sh          # Run in development mode (Maven)
./start.sh docker   # Start with Docker Compose
./start.sh build    # Build JAR only
./start.sh jar      # Run pre-built JAR
./start.sh test     # Run tests
./start.sh help     # Show all options
```

---

## 📡 API Reference

### Authentication
- **Login**: `POST /login` (form-based)
- **Logout**: `POST /logout`

### Products API

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| `GET` | `/api/products` | List all products | Public |
| `GET` | `/api/products/{id}` | Get product details | Public |
| `POST` | `/api/products` | Create product | Admin |
| `PUT` | `/api/products/{id}` | Update product | Admin |
| `DELETE` | `/api/products/{id}` | Delete product | Admin |

### Orders API

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| `POST` | `/purchase` | Make a purchase | User |
| `POST` | `/api/orders` | Make a purchase (alias) | User |
| `GET` | `/api/inventory/stock/{itemId}` | Check stock level | Public |

### Purchase Request Example

```bash
curl -X POST "http://localhost:8080/purchase" \
  -u "user:password" \
  -d "userId=user1" \
  -d "itemId=item1" \
  -d "paymentType=creditCard" \
  -d "price=1999.99"
```

---

## ⚙️ Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/flashsale` | MySQL connection URL |
| `SPRING_DATASOURCE_USERNAME` | `root` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `password` | Database password |
| `SPRING_REDIS_HOST` | `localhost` | Redis server host |
| `SPRING_REDIS_PORT` | `6379` | Redis server port |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka bootstrap servers |

---

## 📦 Project Structure

```
flash-sale-app/
├── src/main/java/com/example/flashsale/
│   ├── FlashSaleApplication.java      # Main entry point
│   ├── config/
│   │   └── SecurityConfig.java        # Spring Security configuration
│   ├── controller/
│   │   ├── AuthController.java        # Authentication endpoints
│   │   ├── OrderController.java       # Purchase & stock endpoints
│   │   └── ProductController.java     # Product CRUD endpoints
│   ├── model/
│   │   ├── Order.java                 # Order entity
│   │   ├── OrderEvent.java            # Kafka event model
│   │   ├── Product.java               # Product entity
│   │   └── User.java                  # User entity
│   ├── producer/
│   │   └── KafkaOrderProducer.java    # Kafka event publisher
│   ├── repository/
│   │   ├── OrderRepository.java       # Order JPA repository
│   │   ├── ProductRepository.java     # Product JPA repository
│   │   └── UserRepository.java        # User JPA repository
│   ├── security/
│   │   └── CustomUserDetailsService.java  # User authentication
│   ├── service/
│   │   └── InventoryService.java      # Core purchase logic
│   └── strategy/
│       ├── PaymentStrategy.java       # Payment interface
│       ├── CreditCardPayment.java     # Credit card implementation
│       └── CryptoPayment.java         # Cryptocurrency implementation
├── src/main/resources/
│   ├── application.properties         # Application config
│   └── static/                        # Frontend assets
├── charts/                            # Helm charts for Kubernetes
├── docker-compose.yml                 # Docker Compose config
├── Dockerfile                         # Multi-stage Docker build
├── start.sh                           # Startup script
├── .env.example                       # Environment template
└── pom.xml                            # Maven configuration
```

---

## 🧪 Testing

```bash
# Run all tests
./start.sh test

# Or using Maven directly
mvn test
```

### Concurrency Test
The project includes `InventoryConcurrencyTest` to verify that the distributed locking mechanism prevents overselling under high concurrency.

---

## 🐳 Docker Deployment

### Build Image Only
```bash
docker build -t flash-sale-app .
```

### Run with Docker Compose
```bash
docker-compose up --build
```

### Stop Services
```bash
docker-compose down
```

---

## ☸️ Kubernetes Deployment

Helm charts are available in the `charts/` directory:

```bash
helm install flash-sale ./charts/flash-sale
```

---

## 📄 License

This project is open-source and available under the [MIT License](LICENSE).

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request
