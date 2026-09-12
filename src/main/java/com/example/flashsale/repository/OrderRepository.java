package com.example.flashsale.repository;

import com.example.flashsale.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByUserIdAndItemId(String userId, String itemId);
    Optional<Order> findByEventId(String eventId);
    boolean existsByItemId(String itemId);
    List<Order> findTop20ByPublishedFalseOrderByIdAsc();

    @Transactional
    @Modifying
    @Query("update Order o set o.published = true where o.eventId = :eventId")
    int markPublished(String eventId);

    @Transactional
    @Modifying
    @Query("update Order o set o.deliveredAt = :now where o.eventId = :eventId and o.deliveredAt is null")
    int markDelivered(String eventId, LocalDateTime now);
}
