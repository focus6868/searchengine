package searchengine.dto.statistics;

import lombok.Data;
import lombok.Setter;

@Data
@Setter
public class StatisticsResponse {
    private boolean result;
    private StatisticsData statistics;
}
