package com.booking.repository;

import com.booking.entity.DiscountCode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface DiscountCodeRepository extends JpaRepository<DiscountCode, Long> {
    Optional<DiscountCode> findByCodeIgnoreCase(String code);
}
