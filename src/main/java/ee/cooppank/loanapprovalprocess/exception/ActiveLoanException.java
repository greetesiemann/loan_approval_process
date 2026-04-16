package ee.cooppank.loanapprovalprocess.exception;

public class ActiveLoanException extends RuntimeException {
    public ActiveLoanException(String message) {
        super(message);
    }
}
