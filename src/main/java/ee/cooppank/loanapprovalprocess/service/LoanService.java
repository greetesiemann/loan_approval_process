package ee.cooppank.loanapprovalprocess.service;

import ee.cooppank.loanapprovalprocess.entity.LoanApplication;
import ee.cooppank.loanapprovalprocess.entity.PaymentSchedule;
import ee.cooppank.loanapprovalprocess.entity.Settings;
import ee.cooppank.loanapprovalprocess.exception.ActiveLoanException;
import ee.cooppank.loanapprovalprocess.exception.InvalidPersonalCodeException;
import ee.cooppank.loanapprovalprocess.exception.LoanNotFoundException;
import ee.cooppank.loanapprovalprocess.exception.ProcessFinishedException;
import ee.cooppank.loanapprovalprocess.repository.LoanApplicationRepository;
import ee.cooppank.loanapprovalprocess.repository.PaymentScheduleRepository;
import ee.cooppank.loanapprovalprocess.repository.SettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LoanService {

    private final PaymentScheduleRepository paymentScheduleRepository;
    private final LoanApplicationRepository loanRepository;
    private final SettingsRepository settingsRepository;


    public LoanService(PaymentScheduleRepository paymentScheduleRepository, LoanApplicationRepository loanRepository, SettingsRepository settingsRepository) {
        this.paymentScheduleRepository = paymentScheduleRepository;
        this.loanRepository = loanRepository;
        this.settingsRepository = settingsRepository;
    }

    public boolean isValidEstonianPersonalCode(String personalCode) {
        // Kontrollime, kas kood on olemas ja on täpselt 11 numbrit
        if (personalCode == null || !personalCode.matches("\\d{11}")) {
            return false;
        }

        // Kontrollime esimest numbrit
        int firstDigit = Character.getNumericValue(personalCode.charAt(0));
        if (firstDigit < 1 || firstDigit > 6) {
            return false;
        }

        // Kontrollnumbri arvutamine
        int lastDigit = Character.getNumericValue(personalCode.charAt(10));

        // I aste
        int[] weights1 = {1, 2, 3, 4, 5, 6, 7, 8, 9, 1};
        int sum1 = 0;
        for (int i = 0; i < 10; i++) {
            sum1 += Character.getNumericValue(personalCode.charAt(i)) * weights1[i];
        }

        int remainder = sum1 % 11;

        // Kui jääk ei ole 10, siis jääk peab võrduma viimase numbriga
        if (remainder < 10) {
            return remainder == lastDigit;
        }

        // II aste: kui jääk oli 10
        int[] weights2 = {3, 4, 5, 6, 7, 8, 9, 1, 2, 3};
        int sum2 = 0;
        for (int i = 0; i < 10; i++) {
            sum2 += Character.getNumericValue(personalCode.charAt(i)) * weights2[i];
        }

        remainder = sum2 % 11;

        // Kui ka nüüd on jääk 10, siis kontrollnumber on 0
        if (remainder == 10) {
            return lastDigit == 0;
        } else {
            return remainder == lastDigit;
        }
    }

    public void validateApplication(LoanApplication application) {
        if (!isValidEstonianPersonalCode(application.getPersonalCode())) {
            throw new InvalidPersonalCodeException("Vigane Eesti isikukood");
        }

        // Kontrollime aktiivse taotluse olemasolu
        // Kasutame juhendis toodud lõppolekuid "APPROVED" ja "REJECTED"
        List<String> closedStatuses = List.of("APPROVED", "REJECTED");
        if (loanRepository.existsByPersonalCodeAndStatusNotIn(application.getPersonalCode(), closedStatuses)) {
            throw new ActiveLoanException("Kliendil on juba aktiivne laenutaotlus.");
        }
    }

    public LoanApplication saveApplication(LoanApplication application) {
        // Kutsume kontrollid välja
        validateApplication(application);
        application.setStatus("STARTED");
        return loanRepository.save(application);
    }

    public LoanApplication processApplication(LoanApplication application) {
        String euriborValue = getSettingValue("EURIBOR_6M");
        application.setBaseInterestRate(new BigDecimal(euriborValue));

        if (ageControl(application)) {
            List<PaymentSchedule> schedules = generatePaymentSchedule(application);
            paymentScheduleRepository.saveAll(schedules);
            application.setStatus("IN_REVIEW");
        }
        return loanRepository.save(application);
    }

    public List<PaymentSchedule> generatePaymentSchedule (LoanApplication application) {
        BigDecimal r = application.getBaseInterestRate()
                .add(application.getInterestMargin())
                .divide(BigDecimal.valueOf(100 * 12), 10, RoundingMode.HALF_UP);

        double rDouble = r.doubleValue();
        int n = application.getLoanPeriodMonths();
        double pmt = application.getLoanAmount().doubleValue() * rDouble / (1 - Math.pow(1 + rDouble, -n));
        BigDecimal monthlyPayment = BigDecimal.valueOf(pmt).setScale(2, RoundingMode.HALF_UP);
        List<PaymentSchedule> schedule = new ArrayList<>();
        BigDecimal remainingBalance = application.getLoanAmount();

        for (int i = 1; i <= application.getLoanPeriodMonths(); i++) {
            BigDecimal interest = remainingBalance.multiply(r).setScale(2, RoundingMode.HALF_UP);
            BigDecimal principal = monthlyPayment.subtract(interest);
            remainingBalance = remainingBalance.subtract(principal);
            PaymentSchedule entry = new PaymentSchedule();
            entry.setLoanApplication(application);
            entry.setPaymentNr(i);
            entry.setPaymentDate(LocalDate.now().plusMonths(i - 1));
            entry.setTotalPayment(monthlyPayment);
            entry.setInterest(interest);
            entry.setPrincipal(principal);
            entry.setRemainingBalance(remainingBalance.max(BigDecimal.ZERO));
            schedule.add(entry);
        }
        return schedule;
    }

    public boolean ageControl(LoanApplication application) {
        int age = personsAge(application.getPersonalCode());
        int maxAgeFromDb = Integer.parseInt(getSettingValue("MAX_AGE"));
        int minAgeFromDb = Integer.parseInt(getSettingValue("MIN_AGE"));

        if (age > maxAgeFromDb) {
            application.setStatus("REJECTED");
            application.setRejectionReason("CUSTOMER_TOO_OLD");
            return false;
        } else if (age < minAgeFromDb) {  // checking if the person is an adult
            application.setStatus("REJECTED");
            application.setRejectionReason("CUSTOMER_TOO_YOUNG");
            return false;
        }
        return true;
    }

    public int personsAge(String personalCode) {
        personalCode = personalCode.substring(0, 7);
        int birthYear = 0;

        if ((personalCode.charAt(0) == '1') || (personalCode.charAt(0) == '2')) {
            birthYear = 1800 +  Integer.parseInt(personalCode.substring(1, 3));
        } else if ((personalCode.charAt(0) == '3') || (personalCode.charAt(0) == '4')) {
            birthYear = 1900 + Integer.parseInt(personalCode.substring(1, 3));
        } else if ((personalCode.charAt(0) == '5') || (personalCode.charAt(0) == '6')) {
            birthYear = 2000 + Integer.parseInt(personalCode.substring(1, 3));
        } else if ((personalCode.charAt(0) == '7') || (personalCode.charAt(0) == '8')) {
            birthYear = 2100 + Integer.parseInt(personalCode.substring(1, 3));
        } else {
            throw  new InvalidPersonalCodeException("Vigane Eesti isikukood");
        }
        int birthMonth = Integer.parseInt(personalCode.substring(3, 5));
        int birthDay = Integer.parseInt(personalCode.substring(5, 7));

        // Loome sünnikuupäeva objekti
        LocalDate birthDate = LocalDate.of(birthYear, birthMonth, birthDay);
        // Arvutame vanuse aastates võrreldes tänasega
        return (int) java.time.temporal.ChronoUnit.YEARS.between(birthDate, LocalDate.now());
    }

    public LoanApplication getApplication(UUID id) {
        return loanRepository.findById(id)
                .orElseThrow(() -> new LoanNotFoundException("Taotlust ei leitud"));
    }

    public LoanApplication approveLoan(UUID id) {
        LoanApplication application = getApplication(id);
        // Ainult IN_REVIEW staatuses saab kinnitada
        if (!"IN_REVIEW".equals(application.getStatus())) {
            throw new ProcessFinishedException(
                    "Taotlust saab kinnitada ainult IN_REVIEW staatuses. Praegune staatus: "
                            + application.getStatus());
        }
        application.setStatus("APPROVED");
        return loanRepository.save(application);
    }

    public LoanApplication rejectLoan(UUID id, String reason) {
        LoanApplication application = getApplication(id);
        // Ainult IN_REVIEW staatuses saab tagasi lükata
        if (!"IN_REVIEW".equals(application.getStatus())) {
            throw new ProcessFinishedException(
                    "Taotlust saab tagasi lükata ainult IN_REVIEW staatuses. Praegune staatus: "
                            + application.getStatus());
        }
        application.setStatus("REJECTED");
        application.setRejectionReason(reason);
        return loanRepository.save(application);
    }

    private String getSettingValue(String key) {
        return settingsRepository.findById(key)
                .map(Settings::getValue)
                .orElseThrow(() -> new RuntimeException("Seadet " + key + " ei leitud andmebaasist!"));
    }

    @Transactional
    public LoanApplication updateAndRegenerate(UUID id, LoanApplication updatedData) {
        LoanApplication application = getApplication(id);

        // Kontrollime, et protsess poleks juba lõppenud
        if ("APPROVED".equals(application.getStatus()) || "REJECTED".equals(application.getStatus())) {
            throw new ProcessFinishedException("Lõppenud protsessi graafikut ei saa muuta.");
        }

        // Uuendame muudetavad väljad
        application.setLoanAmount(updatedData.getLoanAmount());
        application.setLoanPeriodMonths(updatedData.getLoanPeriodMonths());
        application.setInterestMargin(updatedData.getInterestMargin());

        // Värskendame ka Euribori (et oleks kõige uuem määr andmebaasist)
        String euriborValue = getSettingValue("EURIBOR_6M");
        application.setBaseInterestRate(new BigDecimal(euriborValue));

        // 1. Kustutame vana graafiku
        paymentScheduleRepository.deleteByLoanApplicationId(id);

        // 2. Genereerime uue graafiku
        List<PaymentSchedule> newSchedules = generatePaymentSchedule(application);
        paymentScheduleRepository.saveAll(newSchedules);

        return loanRepository.save(application);
    }
}
