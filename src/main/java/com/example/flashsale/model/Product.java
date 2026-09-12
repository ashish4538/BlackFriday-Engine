package com.example.flashsale.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {
    @Id
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}")
    @Column(length = 64)
    private String id;
    @NotBlank @Size(max = 255)
    private String name;
    @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2)
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;
    @NotBlank @Pattern(regexp = "https://[^\\s<>\\\"']+") @Size(max = 255)
    private String imageUrl;
    @NotBlank @Size(max = 1000)
    @Column(length = 1000)
    private String description;

    public Product() {}

    public Product(String id, String name, BigDecimal price, String imageUrl, String description) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.imageUrl = imageUrl;
        this.description = description;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
