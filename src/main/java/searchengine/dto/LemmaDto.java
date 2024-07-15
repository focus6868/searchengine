package searchengine.dto;

import lombok.*;
import org.springframework.beans.factory.annotation.Autowired;
import searchengine.repository.LemmaRepository;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Data
@NoArgsConstructor
public class LemmaDto implements Comparable<LemmaDto> {
    private Long id;
    private String lemma;
    private Integer frequency;
    private Long siteId;

    @Override
    public int compareTo(LemmaDto o) {
        return this.frequency - o.getFrequency();
    }

    public String toString(){
        return "Lemma : " + getLemma() + "\n" +
                "SiteId : " + siteId;
    }

}
