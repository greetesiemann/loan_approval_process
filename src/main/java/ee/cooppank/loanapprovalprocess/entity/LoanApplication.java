package ee.cooppank.loanapprovalprocess.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_application")
@Data
public class LoanApplication {

    @Id
    private UUID id;

    @NotBlank
    @Size(max = 32)
    private String firstName;
    @NotBlank
    @Size(max = 32)
    private String lastName;
    @NotBlank
    @Pattern(regexp = "\\d{11}")
    private String personalCode;

    @NotNull
    @DecimalMin("5000")
    private BigDecimal loanAmount;
    @NotNull
    @Min(6) @Max(360)
    private Integer loanPeriodMonths;
    @NotNull
    @DecimalMin("0")
    private BigDecimal interestMargin;
    @NotNull
    private BigDecimal baseInterestRate;

    private String status; // Näiteks: "APPROVED", "REJECTED"
    private String rejectionReason;
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = LocalDateTime.now();
    }
}
