package searchengine.model;

import jakarta.persistence.*;
import jakarta.persistence.Index;
import lombok.*;

@Setter
@Getter
@Entity
@Table(name = "pages",
        indexes = {
@Index(name = "pathIdx", columnList = "path")
})
public class Page implements Comparable<Page> {
    @Id
    @Column(nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "path", columnDefinition = "TEXT", nullable = false)
    private String path;

    private Integer code;

    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;

    @ManyToOne
    @JoinColumn(name = "site_entity_id")
    private SiteEntity siteEntity;

    @Override
    public int compareTo(Page o) {
        return o.getPath().compareTo(this.getPath());
    }
}
