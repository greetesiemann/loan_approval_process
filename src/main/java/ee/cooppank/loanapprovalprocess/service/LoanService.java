package ee.cooppank.loanapprovalprocess.service;

import ee.cooppank.loanapprovalprocess.entity.LoanApplication;
import ee.cooppank.loanapprovalprocess.exception.ActiveLoanException;
import ee.cooppank.loanapprovalprocess.exception.InvalidPersonalCodeException;
import ee.cooppank.loanapprovalprocess.repository.LoanApplicationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.DateFormat;
import java.time.LocalDate;
import java.util.List;

@Service
public class LoanService {

    private final LoanApplicationRepository repository;
    @Value("${loan.validation.max-age}")
    private int maxAge;

    public LoanService(LoanApplicationRepository repository) {
        this.repository = repository;
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
        if (repository.existsByPersonalCodeAndStatusNotIn(application.getPersonalCode(), closedStatuses)) {
            throw new ActiveLoanException("Kliendil on juba aktiivne laenutaotlus.");
        }
    }

    public LoanApplication saveApplication(LoanApplication application) {
        // Kutsume kontrollid välja
        validateApplication(application);

        // Arvutame vanuse isikukoodist
        int age = personsAge(application.getPersonalCode());

        // Vanusekontroll
        if (age > maxAge) {
            application.setStatus("REJECTED");
            application.setRejectionReason("CUSTOMER_TOO_OLD");
        } else {
            // Kui vanus on sobiv, siis määrame algolekuks STARTED
            application.setStatus("STARTED");
        }
        return repository.save(application);
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
}
