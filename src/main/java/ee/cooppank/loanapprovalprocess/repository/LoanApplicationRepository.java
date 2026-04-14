package ee.cooppank.loanapprovalprocess.repository;

import ee.cooppank.loanapprovalprocess.entity.LoanApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {
}
