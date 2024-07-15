package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.dto.LemmaDto;
import searchengine.model.Lemma;
import searchengine.repository.LemmaRepository;
import searchengine.repository.SiteEntityRepository;

import java.util.Collection;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LemmaCRUDService implements CRUDService<LemmaDto> {

    private final LemmaRepository lemmaRepository;
    private final SiteEntityRepository siteRepository;

    @Override
    public LemmaDto getById(Long id) {
        Lemma lemma = lemmaRepository.findById(id).get();
        return mapToDto(lemma);
    }

    public Collection<LemmaDto> getBySiteId(Long id){
        List<Lemma> lemmaList = lemmaRepository.findAllBySiteEntityId(id);
        return lemmaList.stream().map(this::mapToDto).toList();
    }

    @Override
    public Collection<LemmaDto> getAll() {
        List<Lemma> lemmaList = lemmaRepository.findAll();
        return lemmaList.stream().map(this::mapToDto).toList();
    }

    @Override
    public void create(LemmaDto item) {
        Lemma lemma = mapToEntity(item);
        lemmaRepository.save(lemma);
    }

    @Override
    public void updateById(LemmaDto item) {
        Lemma lemma = mapToEntity(item);
        lemmaRepository.save(lemma);
    }

    @Override
    public void deleteById(Long id) {
        lemmaRepository.deleteById(id);
    }

    public LemmaDto mapToDto(Lemma lemma){
        LemmaDto lemmaDto = new LemmaDto();
        lemmaDto.setId(lemma.getId());
        lemmaDto.setSiteId(lemma.getSiteEntity().getId());
        lemmaDto.setLemma(lemma.getLemma());
        lemmaDto.setFrequency(lemma.getFrequency());
        return lemmaDto;
    }

    public Lemma mapToEntity(LemmaDto lemmaDto){
        Lemma lemma = new Lemma();
        lemma.setId(lemmaDto.getId());
        lemma.setSiteEntity(siteRepository.findById(lemmaDto.getSiteId()).get());
        lemma.setFrequency(lemmaDto.getFrequency());
        lemma.setLemma(lemmaDto.getLemma());
        return lemma;
    }
}
