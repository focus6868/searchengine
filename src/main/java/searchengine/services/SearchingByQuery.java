package searchengine.services;
import jakarta.persistence.criteria.CriteriaBuilder;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.*;
import lombok.Getter;
import lombok.Setter;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.LemmaDto;
import searchengine.dto.search.Data;
import searchengine.dto.search.SearchResult;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteEntityRepository;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Setter
@Getter
public class SearchingByQuery {
    private static final Logger log = LoggerFactory.getLogger(SearchingByQuery.class);
    // Сортировка в обратном порядке
    @Autowired
    LemmaService lemmaService;
    @Autowired
    SiteEntityRepository siteEntityRepository;
    @Autowired
    LemmaRepository lemmaRepository;
    @Autowired
    IndexRepository indexRepository;
    @Autowired
    PageRepository pageRepository;
    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    List<String> queryLemmas = new ArrayList<>();
    List<String> wordsQuery = new ArrayList<>();

    public SearchResult search(String query, int offset, int limit, String site) throws IOException {

        queryLemmas.clear();
        wordsQuery.clear();

        SearchResult searchResult = new SearchResult();
        List<Data> dataList = new ArrayList<>();
        List<String> wdRu = new ArrayList<>(List.of(query.replaceAll("[^а-яА-Я_-]+"," ").split("\\s+")));
        List<String> wdEn = new ArrayList<>(List.of(query.replaceAll("[^a-zA-Z_-]+"," ").split("\\s+")));

        wordsQuery.addAll(getWordsRu(wdRu));
        wordsQuery.addAll(getWordsEn(wdEn));

        try {
            queryLemmas.addAll(lemmaService.getLemmaRuList(getWordsRu(wdRu)));
            queryLemmas.addAll(lemmaService.getLemmaEnList(getWordsEn(wdEn)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if(site == null){
            List<SiteEntity> siteEntityList = siteEntityRepository.findAll();
            siteEntityList.stream().filter(ss -> ss.getStatus().equals(Status.INDEXED)).forEach(s -> {
                List<Data> dList = new ArrayList<>(searchOnes(offset, limit, s.getUrl()));
                if (!dList.isEmpty()) {dataList.addAll(dList);}
            });
        }else{
            dataList.addAll(searchOnes(offset, limit, site));
        }

        searchResult.setResult(!dataList.isEmpty());
        searchResult.setCount(dataList.size());
        searchResult.setData(dataList);

        return searchResult;
    }

    public List<Data> searchOnes(int offset, int limit, String site) {

        SiteEntity siteEntity = siteEntityRepository.findByUrl(site).stream().findFirst().get();
        long siteId = siteEntity.getId();
        List<Data> dataList = new ArrayList<>();

        List<LemmaDto> queryLemmasSorted = new ArrayList<>(getSortedLemmas(siteId));
        long start = System.currentTimeMillis();
        if (!queryLemmasSorted.isEmpty()) {
            start = System.currentTimeMillis();
            List<Index> indicesByQuery = new ArrayList<>(getIndicesByQuery(queryLemmasSorted, siteId));
            System.out.println(" ============  SET RELEVANCE 000000 ============ " + (System.currentTimeMillis() - start));

            List<Page> pageList = pageRepository.findByIdIn(indicesByQuery.stream().map(Index::getPageId).toList());
            List<Index> indexList = indexRepository.findByPageIdIn(pageList.stream().map(Page::getId).toList());
            Map<Long, Float> absRelevanceList = new HashMap<>();

            for (Page page : pageList) {

                List<Index> indices = indexList.stream().filter(ndx -> Objects.equals(ndx.getPageId(), page.getId())).toList();

                absRelevanceList.put(page.getId(), getAbsRelevance(indices, queryLemmasSorted));

                Document doc = Jsoup.parse(page.getContent());
                String snippet = getSnippet(doc);
                if(!snippet.isEmpty()){
                    Data data = new Data();
                    data.setUri(page.getPath());
                    data.setSite(siteEntity.getUrl());
                    data.setSiteName(siteEntity.getName());
                    data.setTitle(doc.title());
                    data.setSnippet(snippet);
                    dataList.add(data);
                }
            }

            if (!absRelevanceList.isEmpty()) {
                float maxRel = Collections.max(absRelevanceList.values());

                for (Map.Entry entry : absRelevanceList.entrySet()) {
                    float relevance = (float) entry.getValue() / maxRel;
                    try {
                        Page page = pageRepository.findById((Long) entry.getKey()).get();
                        Data data;
                        Optional<Data> dataOptional = dataList
                                .stream()
                                .filter(d -> d.getUri().equals(page.getPath())).findFirst();
                        if(dataOptional.isPresent()){
                            data = dataOptional.get();
                            data.setRelevance(relevance);
                        }
                    }catch(NoSuchElementException e){
                        e.printStackTrace();
                    }
                }
            }
        }

        Collections.sort(dataList);
        return dataList;
    }

    private List<LemmaDto> getSortedLemmas(Long siteId){
        long start = System.currentTimeMillis();
        LemmaCRUDService lemmaCRUDService = new LemmaCRUDService(lemmaRepository, siteEntityRepository);
        List<LemmaDto> lemmaDtoQueryList = new ArrayList<>();
        List<LemmaDto> lemmaDtoSite = new ArrayList<>(lemmaCRUDService.getBySiteId(siteId));

        /** Выбираем все совпадающие с запросом леммы и сортируем в обратном порядке по полю frequency */
        /** RANK - количество данной леммы на странице FREQUENCY - количество страниц с данной леммой */
        lemmaDtoQueryList.addAll(lemmaDtoSite
                .stream()
                .filter(lemma -> queryLemmas.contains(lemma.getLemma().trim()))
                .toList());

        return new ArrayList<>(lemmaDtoQueryList.stream().sorted().toList());
    }

    private List<Index> getIndicesByQuery(List<LemmaDto> queryLemmas, long siteId){
        List<Long> pagesIdList = new ArrayList<>();
        List<Index> result = new ArrayList<>();
        Long lemmaId = 0L;

        if (queryLemmas.size() == 1) {
            List<Index> indicesList = new ArrayList<>(indexRepository.findByLemmaId(queryLemmas.get(0).getId()));
            return indicesList;
        }

        for (int i = 0; i < queryLemmas.size(); i++) {

            lemmaId = queryLemmas.get(i).getId();
            if (i == 0) {
                pagesIdList.addAll(getPagesIdByLemmaIdSiteId(lemmaId, siteId));
            } else {

                result.addAll(getIndicesByPagesListByLemmaId(pagesIdList, lemmaId));
                pagesIdList.clear();

                pagesIdList.addAll(result.stream().map(Index::getPageId).toList());
                if (i < (queryLemmas.size() - 1)) {
                    result.clear();
                }
            }
        }

        return result;
    }

    private String getSnippet(Document doc){

        String pageText = doc.body().text().replaceAll("[^а-яА-Я]+"," ");

        List<String> pageWords = Arrays
                .stream(pageText.split("\\s+"))
                .toList()
                .stream()
                .map(item -> item.toLowerCase(Locale.ROOT))
                .toList();

        int startNdx = getStartIndex(pageWords);

        if(startNdx == pageWords.size()){
            return "";
        }

        int stopNdx = (pageWords.size() > startNdx + 30) ? startNdx + 30 : (Math.max(pageWords.size() - 1, startNdx));

        List<String> snippetList = new ArrayList<>(pageWords.subList(startNdx, stopNdx));
        List<String> snippetBold = new ArrayList<>(
                snippetList
                        .stream()
                        .map(w -> {
                            if(wordsQuery.contains(w)){
                                w = "<b>" + w + "</b>";
                            }
                            return  w;
                        })
                        .toList()
        );
        StringBuilder snippet = new StringBuilder();
        snippetBold.forEach(w -> snippet.append(w).append(" "));

        return snippet.toString();
    }

    private List<Long> getPagesIdByLemmaIdSiteId(long lemmaId, long siteId){
        List<Long> pageIdList = new ArrayList<>(getPagesBySite(siteId).stream().map(Page::getId).toList());

        List<Long> result = new ArrayList<>(indexRepository.findByLemmaId(lemmaId)
                .stream()
                .map(Index::getPageId)
                .filter(id -> pageIdList.contains(id))
                .toList());

        return result;
    }

    private List<Page> getPagesBySite(long siteId){
        return pageRepository.findAllBySiteId(siteId);
    }

    private List<Index> getIndicesByPagesListByLemmaId(List<Long> pagesList, Long lemmaId){

        List<Index> result = new ArrayList<>(indexRepository.findByLemmaId(lemmaId)
                .stream()
                .filter(index -> pagesList.contains(index.getPageId()))
                .toList());

        return result;
    }

    private float getAbsRelevance(List<Index> indexList, List<LemmaDto> queryWords){
        float absRelevance = 0f;
        List<Long> lemmasIdList = new ArrayList<>(queryWords.stream().map(LemmaDto::getId).toList());
        absRelevance =
                (float) indexList
                        .stream()
                        .filter(index -> lemmasIdList.contains(index.getLemmaId()))
                        .mapToDouble(Index::getRank).reduce(0, Double::sum);

        return absRelevance;
    }

    private int getStartIndex(List<String> pageWords){
        int result = pageWords.size();

        for(int i=0; i<wordsQuery.size(); i++){
            String word = wordsQuery.get(i);
            int curIndex = pageWords.indexOf(word);
            if(curIndex > -1 && curIndex < result){
                result = curIndex;
            }
        }
        return result;
    }

    private List<String> getWordsRu(List<String> wdRu) throws IOException {
        LuceneMorphology luceneMorphRu = new RussianLuceneMorphology();
        List<String> particlesNames = Arrays.stream((new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"})).toList();
        List<String> ListResult = new ArrayList<>();
        for(String w: wdRu){
            String word = w.toLowerCase(Locale.ROOT).trim();
            List<String> wordTypes = luceneMorphRu.getMorphInfo(word);
            Set<String> set = particlesNames.stream().filter(wordTypes::contains).collect(Collectors.toSet());

            if (word.length() > 3 && set.isEmpty()) {
                ListResult.add(word);
            }
        }
        return ListResult;
    }

    private List<String> getWordsEn(List<String> wdEn) throws IOException {
        LuceneMorphology luceneMorphEn = new EnglishLuceneMorphology();
        List<String> ListResult = new ArrayList<>();
        for(String w: wdEn){
            String word = w.toLowerCase(Locale.ROOT).trim();

            if (word.length() > 3) {
                ListResult.add(word);
            }
        }
        return ListResult;
    }

}
