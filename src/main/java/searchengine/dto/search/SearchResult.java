package searchengine.dto.search;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class SearchResult {
    private boolean result;
    private int count = 0;
    private List<Data> data;

    @Override
    public String toString() {
        return "SearchResult{" +
                "result=" + result +
                ", count=" + count +
                ", dataList=" + data +
                '}';
    }
}
