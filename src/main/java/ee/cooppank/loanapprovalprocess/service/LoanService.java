package ee.cooppank.loanapprovalprocess.service;

import ee.cooppank.loanapprovalprocess.entity.LoanApplication;
import org.springframework.stereotype.Service;

@Service
public class LoanService {

    public boolean isValidEstonianPersonalCode(String personalCode) {
        // Kontrollime, kas kood on olemas ja on täpselt 11 numbrit
        if (personalCode == null || !personalCode.matches("\\d{11}")) {
            return false;
        }

        // Kontrollime esimest numbrit (sugu/sajand) - peab olema 1 kuni 6
        int firstDigit = Character.getNumericValue(personalCode.charAt(0));
        if (firstDigit < 1 || firstDigit > 6) {
            return false;
        }

        // Kontrollnumbri arvutamine (see on koodi viimane ehk 11. number)
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
            // Viska erind, kui isikukood on vale
            throw new IllegalArgumentException("Vigane Eesti isikukood");
        }
        // Siia saad hiljem lisada ka muud kontrollid (nt maksehäired)
    }
}
