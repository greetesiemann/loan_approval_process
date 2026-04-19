package ee.cooppank.loanapprovalprocess.repository;

import ee.cooppank.loanapprovalprocess.entity.PaymentSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PaymentScheduleRepository extends JpaRepository<PaymentSchedule, UUID> {
    void deleteByLoanApplicationId(UUID loanApplicationId);
}
