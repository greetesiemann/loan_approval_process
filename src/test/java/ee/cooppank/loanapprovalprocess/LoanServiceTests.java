package ee.cooppank.loanapprovalprocess;

import ee.cooppank.loanapprovalprocess.entity.LoanApplication;
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
}
