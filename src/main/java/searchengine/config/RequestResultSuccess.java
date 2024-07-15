package searchengine.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestResultSuccess {
    private boolean result;

    public RequestResultSuccess(boolean result) {
        this.result = result;
    }
}
