package ee.cooppank.loanapprovalprocess;

import ee.cooppank.loanapprovalprocess.entity.LoanApplication;
import ee.cooppank.loanapprovalprocess.entity.PaymentSchedule;
import ee.cooppank.loanapprovalprocess.entity.Settings;
import ee.cooppank.loanapprovalprocess.repository.LoanApplicationRepository;
import ee.cooppank.loanapprovalprocess.repository.PaymentScheduleRepository;
import ee.cooppank.loanapprovalprocess.repository.SettingsRepository;
import ee.cooppank.loanapprovalprocess.service.LoanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanServiceTests {

    @Mock
    private LoanApplicationRepository loanRepository;
    @Mock
    private PaymentScheduleRepository paymentScheduleRepository;
    @Mock
    private SettingsRepository settingsRepository;

    @InjectMocks
    private LoanService loanService;
    private LoanApplication sampleApp;

    @BeforeEach
    void setUp() {
        sampleApp = new LoanApplication();
        sampleApp.setId(UUID.randomUUID());
        sampleApp.setPersonalCode("60510200222");
        sampleApp.setLoanAmount(new BigDecimal("10000"));
        sampleApp.setLoanPeriodMonths(12);
        sampleApp.setInterestMargin(new BigDecimal("2.0"));
        sampleApp.setBaseInterestRate(new BigDecimal("3.85"));
    }

    @Test
    void testIsValidEstonianPersonalCode() {
        assertTrue(loanService.isValidEstonianPersonalCode("60510200222"));
        assertFalse(loanService.isValidEstonianPersonalCode("60510200221"));
        assertFalse(loanService.isValidEstonianPersonalCode("6051020"));
    }

    @Test
    void testAgeControl_Success() {
        // Settings @AllArgsConstructor nõuab 3 argumenti: key, value, description
        when(settingsRepository.findById("MAX_AGE"))
                .thenReturn(Optional.of(new Settings("MAX_AGE", "70", null)));
        when(settingsRepository.findById("MIN_AGE"))
                .thenReturn(Optional.of(new Settings("MIN_AGE", "18", null)));

        boolean result = loanService.ageControl(sampleApp);
        assertTrue(result);
    }

    @Test
    void testAgeControl_TooYoung() {
        sampleApp.setPersonalCode("51001010007"); // Sündinud 2010 (liiga noor)

        when(settingsRepository.findById("MAX_AGE"))
                .thenReturn(Optional.of(new Settings("MAX_AGE", "70", null)));
        when(settingsRepository.findById("MIN_AGE"))
                .thenReturn(Optional.of(new Settings("MIN_AGE", "18", null)));

        boolean result = loanService.ageControl(sampleApp);
        assertFalse(result);
        assertEquals("REJECTED", sampleApp.getStatus());
        assertEquals("CUSTOMER_TOO_YOUNG", sampleApp.getRejectionReason());
    }

    @Test
    void testGeneratePaymentSchedule_correctNumberOfPayments() {
        // 12-kuuline laen peab genereerima täpselt 12 makset
        List<PaymentSchedule> schedule = loanService.generatePaymentSchedule(sampleApp);
        assertEquals(12, schedule.size());
    }

    @Test
    void testGeneratePaymentSchedule_paymentNumbersAreSequential() {
        // Maksete numbrid peavad olema 1, 2, 3, ...
        List<PaymentSchedule> schedule = loanService.generatePaymentSchedule(sampleApp);
        for (int i = 0; i < schedule.size(); i++) {
            assertEquals(i + 1, schedule.get(i).getPaymentNr());
        }
    }

    @Test
    void testGeneratePaymentSchedule_allPaymentsArePositive() {
        // Kõik summad peavad olema positiivsed
        List<PaymentSchedule> schedule = loanService.generatePaymentSchedule(sampleApp);
        for (PaymentSchedule entry : schedule) {
            assertTrue(entry.getTotalPayment().compareTo(BigDecimal.ZERO) > 0);
            assertTrue(entry.getPrincipal().compareTo(BigDecimal.ZERO) > 0);
            assertTrue(entry.getInterest().compareTo(BigDecimal.ZERO) > 0);
        }
    }

    @Test
    void testGeneratePaymentSchedule_remainingBalanceDecreasesOverTime() {
        // Jääk peab iga maksega vähenema
        List<PaymentSchedule> schedule = loanService.generatePaymentSchedule(sampleApp);
        for (int i = 1; i < schedule.size(); i++) {
            assertTrue(
                    schedule.get(i).getRemainingBalance()
                            .compareTo(schedule.get(i - 1).getRemainingBalance()) < 0
            );
        }
    }

    @Test
    void testGeneratePaymentSchedule_lastPaymentRemainingBalanceIsZero() {
        // Viimase makse järel peab jääk olema 0
        List<PaymentSchedule> schedule = loanService.generatePaymentSchedule(sampleApp);
        PaymentSchedule lastPayment = schedule.getLast();
        assertEquals(0, lastPayment.getRemainingBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void testGeneratePaymentSchedule_totalPaidApproximatesLoanAmount() {
        // Kõigi põhiosamaksete summa peab ligikaudu võrduma laenusummaga
        List<PaymentSchedule> schedule = loanService.generatePaymentSchedule(sampleApp);
        BigDecimal totalPrincipal = schedule.stream()
                .map(PaymentSchedule::getPrincipal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Lubame 1 euro suuruse ümardamisvea
        BigDecimal difference = totalPrincipal.subtract(sampleApp.getLoanAmount()).abs();
        assertTrue(difference.compareTo(new BigDecimal("1.00")) <= 0,
                "Põhiosamaksete summa erineb laenusummast rohkem kui 1 euro: " + difference);
    }

    @Test
    void testGeneratePaymentSchedule_monthlyPaymentIsConsistent() {
        // Annuiteetlaenu puhul peab igakuine makse olema sama (v.a. viimane makse)
        List<PaymentSchedule> schedule = loanService.generatePaymentSchedule(sampleApp);
        BigDecimal firstPayment = schedule.getFirst().getTotalPayment();
        for (int i = 0; i < schedule.size() - 1; i++) {
            assertEquals(0, schedule.get(i).getTotalPayment().compareTo(firstPayment),
                    "Makse nr " + (i + 1) + " erineb esimesest maksest");
        }
    }
}
