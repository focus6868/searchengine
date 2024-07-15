package searchengine.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
public class RequestResultError {
    private boolean result = false;
    private String error;

    public RequestResultError(String error) {
        this.error = error;
    }

}
