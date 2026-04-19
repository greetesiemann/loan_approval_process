package ee.cooppank.loanapprovalprocess.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "payment_schedule")
@Data
public class PaymentSchedule {

    @Id
    private UUID id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "loan_application_id")
    private LoanApplication loanApplication;

    @NotNull
    private Integer paymentNr;
    @NotNull
    private LocalDate paymentDate;
    @NotNull
    private BigDecimal totalPayment;
    @NotNull
    private BigDecimal principal;
    @NotNull
    private BigDecimal interest;
    @NotNull
    private BigDecimal remainingBalance;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }
}
