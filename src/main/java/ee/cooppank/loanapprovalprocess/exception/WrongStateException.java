package ee.cooppank.loanapprovalprocess.exception;

public class WrongStateException extends RuntimeException {
    public WrongStateException(String message) {
        super(message);
    }
}
