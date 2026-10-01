package com.orderstream.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "products")
public class Product {

    @Id
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Version
    private long version;

    protected Product() {
    }

    public Product(String sku, String name, int availableQuantity) {
        this.sku = sku;
        this.name = name;
        this.availableQuantity = availableQuantity;
    }

    public boolean canReserve(int quantity) {
        return availableQuantity >= quantity;
    }

    public void reserve(int quantity) {
        if (!canReserve(quantity)) {
            throw new IllegalStateException("Insufficient stock for " + sku);
        }
        availableQuantity -= quantity;
    }

    public void release(int quantity) {
        availableQuantity += quantity;
    }

    public void restock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Restock quantity must be positive");
        }
        availableQuantity += quantity;
    }

    public String getSku() { return sku; }
    public String getName() { return name; }
    public int getAvailableQuantity() { return availableQuantity; }
}
