package ee.cooppank.loanapprovalprocess.exception;

public class InvalidPersonalCodeException extends RuntimeException {
    public InvalidPersonalCodeException(String message) {
        super(message);
    }
}
