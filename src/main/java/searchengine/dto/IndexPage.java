package searchengine.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
public class IndexPage implements Comparable<String > {
    private String pageUrl;
    private final Long pageId;
    private final Long lemmaId;
    private final Long rank; // количество лемм на странице

    @Override
    public int compareTo(String pageUrl) {
        return pageUrl.compareTo(this.pageUrl);
    }
}
