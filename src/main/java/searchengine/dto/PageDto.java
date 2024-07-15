package searchengine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import searchengine.model.Page;
import searchengine.model.SiteEntity;

@Setter
@Getter
@Data
@AllArgsConstructor
public class PageDto implements Comparable<PageDto>  {

    private String path;
    private Integer code;
    private String content;
    private Long siteId;

    @Override
    public int compareTo(PageDto o) {
        return o.getPath().compareTo(this.getPath());
    }
}
