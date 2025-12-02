package fr.agile.exception;

public class JiraServiceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public JiraServiceException(String message) {
        super(message);
    }

    public JiraServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
