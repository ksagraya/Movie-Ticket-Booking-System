package com.booking.repository;

import com.booking.entity.RefundPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RefundPolicyRepository extends JpaRepository<RefundPolicy, Long> {
    List<RefundPolicy> findByActiveTrueOrderByHoursBeforeShowDesc();
}
