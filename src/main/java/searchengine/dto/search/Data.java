package searchengine.dto.search;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Data implements Comparable<Data> {
    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private float relevance;

    @Override
    public int compareTo(Data o) {
         if(this.relevance > o.relevance) {
             return -1;
         };
        if(this.relevance < o.relevance) {
            return 1;
        };
        return 0;
    }

    @Override
    public String toString() {
        return "Data{" +
                "site='" + site + '\'' +
                ", siteName='" + siteName + '\'' +
                ", uri='" + uri + '\'' +
                ", title='" + title + '\'' +
                ", snippet='" + snippet + '\'' +
                ", relevance=" + relevance +
                '}';
    }
}
