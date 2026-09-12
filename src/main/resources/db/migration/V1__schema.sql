CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) COLLATE utf8mb4_bin NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    full_name VARCHAR(255),
    role VARCHAR(255)
);
CREATE TABLE products (
    id VARCHAR(64) COLLATE utf8mb4_bin PRIMARY KEY,
    name VARCHAR(255),
    price DECIMAL(19,2) NOT NULL,
    image_url VARCHAR(255),
    description VARCHAR(1000)
);
CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    user_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    item_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    price DECIMAL(19,2) NOT NULL,
    order_time DATETIME(6) NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    delivered_at DATETIME(6),
    CONSTRAINT unique_purchase UNIQUE (user_id, item_id),
    INDEX pending_orders (published, id)
);
