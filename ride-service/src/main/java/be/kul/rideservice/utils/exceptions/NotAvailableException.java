package be.kul.rideservice.utils.exceptions;

public class NotAvailableException extends RuntimeException{
    public NotAvailableException() {
        super("The resource is not available");
    }

    public NotAvailableException(String message) {
        super(message);
    }
}
