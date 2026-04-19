package ee.cooppank.loanapprovalprocess.service;

import ee.cooppank.loanapprovalprocess.entity.LoanApplication;
import ee.cooppank.loanapprovalprocess.entity.PaymentSchedule;
import ee.cooppank.loanapprovalprocess.entity.Settings;
import ee.cooppank.loanapprovalprocess.exception.*;
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

    /**
     * Validates an Estonian personal code according to the national standard.
     * Checks the format, birth century, and the checksum.
     * @param personalCode The 11-digit personal code string.
     * @return true if the personal code is valid, false otherwise.
     */
    public boolean isValidEstonianPersonalCode(String personalCode) {
        if (personalCode == null || !personalCode.matches("\\d{11}")) {
            return false;
        }

        int firstDigit = Character.getNumericValue(personalCode.charAt(0));
        if (firstDigit < 1 || firstDigit > 6) {
            return false;
        }

        int lastDigit = Character.getNumericValue(personalCode.charAt(10));

        // I check
        int[] weights1 = {1, 2, 3, 4, 5, 6, 7, 8, 9, 1};
        int sum1 = 0;
        for (int i = 0; i < 10; i++) {
            sum1 += Character.getNumericValue(personalCode.charAt(i)) * weights1[i];
        }

        int remainder = sum1 % 11;


        if (remainder < 10) {
            return remainder == lastDigit;
        }

        // II check
        int[] weights2 = {3, 4, 5, 6, 7, 8, 9, 1, 2, 3};
        int sum2 = 0;
        for (int i = 0; i < 10; i++) {
            sum2 += Character.getNumericValue(personalCode.charAt(i)) * weights2[i];
        }

        remainder = sum2 % 11;

        if (remainder == 10) {
            return lastDigit == 0;
        } else {
            return remainder == lastDigit;
        }
    }

    /**
     * Performs business validation for a new loan application.
     * Validates the personal code and checks if the applicant has an active loan.
     * @param application The loan application to validate.
     * @throws InvalidPersonalCodeException If the personal code is mathematically invalid.
     * @throws ActiveLoanException If the applicant already has an ongoing loan process.
     */
    public void validateApplication(LoanApplication application) {
        if (!isValidEstonianPersonalCode(application.getPersonalCode())) {
            throw new InvalidPersonalCodeException("Vigane Eesti isikukood");
        }

        List<String> closedStatuses = List.of("APPROVED", "REJECTED");
        if (loanRepository.existsByPersonalCodeAndStatusNotIn(application.getPersonalCode(), closedStatuses)) {
            throw new ActiveLoanException("Kliendil on juba aktiivne laenutaotlus.");
        }
    }

    /**
     * Saves a new loan application to the database and sets its initial status to STARTED.
     * @param application The loan application data.
     * @return The saved loan application entity.
     */
    public LoanApplication saveApplication(LoanApplication application) {
        validateApplication(application);
        application.setStatus("STARTED");
        return loanRepository.save(application);
    }

    /**
     * Processes the loan application by determining the base interest rate,
     * checking eligibility based on age, and generating a payment schedule.
     * @param application The application to process.
     * @return The updated loan application entity.
     */
    @Transactional
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

    /**
     * Generates a monthly annuity payment schedule for a loan application.
     * @param application The application containing loan amount, period, and interest rates.
     * @return A list of payment schedule entries.
     */
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

    /**
     * Checks if the applicant's age is within the limits defined in settings.
     * Sets the application status to REJECTED if age is outside the bounds.
     * @param application The application to check.
     * @return true if age is valid, false if application is rejected.
     */
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

    /**
     * Calculates the age of a person based on their Estonian personal code.
     * @param personalCode The 11-digit personal code.
     * @return The current age in years.
     * @throws InvalidPersonalCodeException If the first digit of the code is unrecognized.
     */
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

        LocalDate birthDate = LocalDate.of(birthYear, birthMonth, birthDay);
        return (int) java.time.temporal.ChronoUnit.YEARS.between(birthDate, LocalDate.now());
    }

    /**
     * Retrieves a loan application by its ID.
     * @param id The UUID of the application.
     * @return The found application entity.
     * @throws LoanNotFoundException If no application with the given ID exists.
     */
    public LoanApplication getApplication(UUID id) {
        return loanRepository.findById(id)
                .orElseThrow(() -> new LoanNotFoundException("Taotlust ei leitud"));
    }

    /**
     * Finalizes and approves a loan application.
     * Only applications in IN_REVIEW status can be approved.
     * @param id The UUID of the application.
     * @return The updated application entity.
     * @throws WrongStateException If the application is not in the correct status.
     */
    public LoanApplication approveLoan(UUID id) {
        LoanApplication application = getApplication(id);
        if (!"IN_REVIEW".equals(application.getStatus())) {
            throw new WrongStateException(
                    "Taotlust saab kinnitada ainult IN_REVIEW staatuses. Praegune staatus: "
                            + application.getStatus());
        }
        application.setStatus("APPROVED");
        return loanRepository.save(application);
    }

    /**
     * Finalizes and rejects a loan application with a specific reason.
     * Only applications in IN_REVIEW status can be rejected.
     * @param id The UUID of the application.
     * @param reason The reason for rejection.
     * @return The updated application entity.
     * @throws WrongStateException If the application is not in the correct status.
     */
    public LoanApplication rejectLoan(UUID id, String reason) {
        LoanApplication application = getApplication(id);
        if (!"IN_REVIEW".equals(application.getStatus())) {
            throw new WrongStateException(
                    "Taotlust saab tagasi lükata ainult IN_REVIEW staatuses. Praegune staatus: "
                            + application.getStatus());
        }
        application.setStatus("REJECTED");
        application.setRejectionReason(reason);
        return loanRepository.save(application);
    }

    /**
     * Helper method to fetch a setting value from the database.
     * @param key The setting key (e.g., EURIBOR_6M).
     * @return The value of the setting as a string.
     * @throws RuntimeException If the setting is missing from the database.
     */
    private String getSettingValue(String key) {
        return settingsRepository.findById(key)
                .map(Settings::getValue)
                .orElseThrow(() -> new RuntimeException("Seadet " + key + " ei leitud andmebaasist!"));
    }

    /**
     * Updates loan parameters and regenerates the payment schedule.
     * Only possible if the process is not yet finished (Approved or Rejected).
     * @param id The UUID of the application.
     * @param updatedData Data containing new amount, period, or margin.
     * @return The updated loan application entity.
     * @throws ProcessFinishedException If the process is already completed.
     */
    @Transactional
    public LoanApplication updateAndRegenerate(UUID id, LoanApplication updatedData) {
        LoanApplication application = getApplication(id);

        if ("APPROVED".equals(application.getStatus()) || "REJECTED".equals(application.getStatus())) {
            throw new ProcessFinishedException("Lõppenud protsessi graafikut ei saa muuta.");
        }

        application.setLoanAmount(updatedData.getLoanAmount());
        application.setLoanPeriodMonths(updatedData.getLoanPeriodMonths());
        application.setInterestMargin(updatedData.getInterestMargin());

        String euriborValue = getSettingValue("EURIBOR_6M");
        application.setBaseInterestRate(new BigDecimal(euriborValue));

        paymentScheduleRepository.deleteByLoanApplicationId(id);

        List<PaymentSchedule> newSchedules = generatePaymentSchedule(application);
        paymentScheduleRepository.saveAll(newSchedules);

        return loanRepository.save(application);
    }
}
