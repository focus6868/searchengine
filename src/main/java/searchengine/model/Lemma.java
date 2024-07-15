package searchengine.model;

import lombok.Getter;
import lombok.Setter;


import jakarta.persistence.*;

@Setter
@Getter
@Entity
@Table(name = "lemmas")
public class Lemma {
    @Id
    @Column(nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String lemma;

    @Column(nullable = false)
    private Integer frequency = 1;

    @ManyToOne
    private SiteEntity siteEntity;
}
