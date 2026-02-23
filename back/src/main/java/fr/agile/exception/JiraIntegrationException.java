package fr.agile.exception;

public class JiraIntegrationException extends RuntimeException {

    public JiraIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
