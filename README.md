# BlackFriday-Engine

## Prerequisites

Java 17, Maven 3.9, MySQL 8.0, Redis 7.2, and Kafka 3.5.
Docker with Compose v2 for container startup.

## Configuration

Copy `.env.example` to `.env` and set:

| Variable | Value |
| --- | --- |
| `DB_PASSWORD` | Compose application database password |
| `DB_ROOT_PASSWORD` | Compose database root password |
| `SPRING_DATASOURCE_URL` | MySQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | Application database username |
| `SPRING_DATASOURCE_PASSWORD` | Same application password as `DB_PASSWORD` |
| `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT` | Redis address |
| `SPRING_DATA_REDIS_PASSWORD` | Redis password, when required |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap address |
| `SESSION_COOKIE_SECURE` | `false` for local HTTP; `true` for HTTPS |

Flyway initializes an empty database; Hibernate validates its schema.
Provision administrator roles through controlled database administration.
Create products through the admin page and initialize stock with authenticated
`POST /api/inventory/{itemId}/initialize?stock=100`, including the CSRF token
from `GET /api/auth/csrf`.

## Local startup

```bash
cp .env.example .env
# Set the configuration above and start MySQL, Redis, and Kafka.
./start.sh run
```

Open http://localhost:8080/home.html.

## Compose startup

```bash
docker compose up --build
```

## Tests

```bash
mvn test
./start.sh integration
```
