package com.orderstream.inventory.api;

import com.orderstream.inventory.domain.Product;
import com.orderstream.inventory.domain.ProductRepository;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/products")
public class ProductController {

    public record ProductResponse(String sku, String name, int availableQuantity) {
        static ProductResponse from(Product p) {
            return new ProductResponse(p.getSku(), p.getName(), p.getAvailableQuantity());
        }
    }

    public record RestockRequest(@Min(1) int quantity) {
    }

    private final ProductRepository products;

    public ProductController(ProductRepository products) {
        this.products = products;
    }

    @GetMapping
    public List<ProductResponse> all() {
        return products.findAll().stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/{sku}")
    public ProductResponse one(@PathVariable String sku) {
        return products.findById(sku).map(ProductResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown product " + sku));
    }

    @PostMapping("/{sku}/restock")
    @Transactional
    public ProductResponse restock(@PathVariable String sku, @RequestBody @jakarta.validation.Valid RestockRequest request) {
        Product product = products.lockAllBySku(List.of(sku)).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown product " + sku));
        product.restock(request.quantity());
        return ProductResponse.from(product);
    }
}
