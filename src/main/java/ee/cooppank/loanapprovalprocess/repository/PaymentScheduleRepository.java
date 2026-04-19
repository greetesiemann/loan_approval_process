package ee.cooppank.loanapprovalprocess.repository;

import ee.cooppank.loanapprovalprocess.entity.LoanApplication;
import ee.cooppank.loanapprovalprocess.entity.PaymentSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentScheduleRepository extends JpaRepository<PaymentSchedule, UUID> {

}
