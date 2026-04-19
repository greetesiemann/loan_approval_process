package ee.cooppank.loanapprovalprocess.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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

    @OneToMany(mappedBy = "loanApplication", fetch = FetchType.EAGER)
    private List<PaymentSchedule> paymentSchedule;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = LocalDateTime.now();
    }
}
