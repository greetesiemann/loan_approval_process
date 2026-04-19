package ee.cooppank.loanapprovalprocess.exception;

public class ProcessFinishedException extends RuntimeException {
    public ProcessFinishedException(String message) {
        super(message);
    }
}
