package ee.cooppank.loanapprovalprocess.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "LoanApplications")
@Data
public class LoanApplication {

    @Id
    private UUID id;

    private String firstName;
    private String lastName;
    private String personalCode;
    private BigDecimal loanAmount;
    private Integer loanPeriodMonths;
    private BigDecimal interestMargin;
    private BigDecimal baseInterestRate;

    private String status; // Näiteks: "APPROVED", "REJECTED", "MANUAL_REVIEW"
    private String rejectionReason;
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = LocalDateTime.now();
    }
}
