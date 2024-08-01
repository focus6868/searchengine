package searchengine.services;

import java.io.IOException;
import java.util.*;
        import java.util.logging.Logger;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
        import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import searchengine.config.RequestResultError;
import searchengine.config.RequestResultSuccess;
        import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteEntityRepository;
import searchengine.util.ConnectionApp;

@Component
public class LemmaService {

    private static final List<String> particlesNamesRu = new ArrayList<>(List.of("МЕЖД", "ПРЕДЛ", "СОЮЗ"));
    private static final List<String> particlesNamesEn = new ArrayList<>(List.of("ARTICLE", "PN"));

    @Autowired
    private SiteEntityRepository siteEntityRepository;
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private IndexRepository indexRepository;
    @Autowired
    LemmaRepository lemmaRepository;
    @Autowired
    ConnectionApp connectionApp;
    LuceneMorphology luceneMorphRus;
    LuceneMorphology luceneMorphEng;

    private Logger log = Logger.getLogger(LemmaService.class.getName());

    public LemmaService() throws IOException {
        luceneMorphRus = new RussianLuceneMorphology();
        luceneMorphEng = new EnglishLuceneMorphology();
    }

    public ResponseEntity<?> IndexOnePage(String path) throws IOException {

        TreeMap<String, Integer> indexPageList = new TreeMap<>();
        String urlHead = path.replaceAll(connectionApp.getUrlPatternHead(),"");
        String urlTail = path.replaceAll(connectionApp.getUrlPatternTail(),"");
        SiteEntity siteEntity = null;
        Long pageId = 0L;
        Long siteId = 0L;

        List<SiteEntity> sites = siteEntityRepository.findByUrl(urlHead);
        List<Page> pages = pageRepository.findByPath(urlTail);

        if (!sites.isEmpty() && !pages.isEmpty()) {

            siteEntity = sites.get(0);
            siteId = siteEntity.getId();
            pageId = pages.get(0).getId();

            indexRepository.deleteByPageId(pageId);
            pageRepository.deleteById(pageId);

            List<Lemma> lemmaRepositoryAll = lemmaRepository.findAllBySiteEntityId(siteId);

            Page page = createPage(siteEntity, urlTail, path);
            String pageText = Jsoup.parse(page.getContent()).text();
            indexPageList.putAll(getLemmas(pageText));
            List<String> lemmaGotList = indexPageList.keySet().stream().toList();

                insertToLemmas(lemmaGotList, lemmaRepositoryAll);

                insertToIndex(indexPageList, lemmaGotList, siteId, pageId);

        } else{
            return ResponseEntity.ofNullable(new RequestResultError("Запрошенная страница находится за пределами доступных сайтов"));
        }
        return ResponseEntity.ok(new RequestResultSuccess(true));
    }

    private void insertToLemmas(List<String> lemmaGotList , List<Lemma> lemmaRepositoryAll) throws IOException {
        StringBuilder sqlString = new StringBuilder();
        String sql;

        lemmaRepositoryAll.stream().forEach(lr -> {
            if(lemmaGotList.contains(lr.getLemma())){
                lr.setFrequency(lr.getFrequency() - 1);
                lemmaRepository.save(lr);
            }
        });

        for(String gotLemma: lemmaGotList){
            if (lemmaRepositoryAll.stream().map(Lemma::getLemma).toList().contains(gotLemma)){
                List<Lemma> lemmaList = lemmaRepositoryAll.stream().filter(l -> l.getLemma().equals(gotLemma)).toList();
                Lemma lemma = lemmaList.stream().findFirst().get();
                lemma.setFrequency(lemma.getFrequency() + 1);
                lemmaRepository.save(lemma);
            } else {
                if(!sqlString.isEmpty()) {
                    sqlString.append(",");
                }
                sqlString.append("(").append(1).append(",\"").append(gotLemma).append("\")");
            }
        }
        if(!sqlString.isEmpty()){
            sql = "insert into lemmas (frequency, lemma) values" + sqlString;
            connectionApp.execSql(sql);
        }
    }

    private Page createPage(SiteEntity siteEntity, String urlTail, String path) throws IOException {

        Page page;
            Document doc = connectionApp.getDocument(path);
            int statusCode = connectionApp.statusCode(path);
            page = new Page();
            page.setContent(doc.html());
            page.setCode(statusCode);
            page.setPath(urlTail);
            page.setSiteEntity(siteEntity);
            pageRepository.save(page);

        return page;
    }

    /** TODO HashMap<String, IndexPage>
     * 1 - ключ мэпа - Лемма, 2 - frequency ()
     */
    private TreeMap<String, Integer> getLemmas(String pageText) throws IOException {
        TreeMap<String, Integer> pageIndex = new TreeMap<>();

        getAllLemmas(pageText)
                .stream()
                .filter(l -> l.length()>3)
                .forEach(item -> {
                    int frequency = 0;
                    if (!pageIndex.isEmpty()) {
                        frequency = pageIndex.get(item) == null ? 0 : pageIndex.get(item).intValue();
                    }
                    pageIndex.put(item, frequency + 1);
                });
        return pageIndex;
    }

    private List<String> getAllLemmas(String pageText) throws IOException {
        List<String> lemmas = new ArrayList<>();
        lemmas.addAll(getLemmaRuList(getWordsRu(pageText)));
        lemmas.addAll(getLemmaEnList(getWordsEn(pageText)));
        return lemmas;
    }

    public List<String> getLemmaRuList(List<String> words)  throws IOException {
        List<String> lemmaRu = new ArrayList<>();
            words
                    .stream()
                    .filter(w -> !w.isEmpty())
                    .forEach(word -> {
                        StringBuilder wTypeLine = new StringBuilder();
                        luceneMorphRus.getMorphInfo(word).forEach(wTypeLine::append);
                        String wordsTypesLine = wTypeLine.toString();
                        boolean isContains = false;

                        for(String pn: particlesNamesRu){
                            if(wordsTypesLine.contains(pn)){isContains = true; break;}
                        }

                        if (word.length() > 3 && !isContains) {
                            lemmaRu.addAll(luceneMorphRus.getNormalForms(word));
                        }
            });
        return lemmaRu;
    }

    public List<String> getLemmaEnList(List<String> words)  throws IOException {
        List<String> lemmaEn = new ArrayList<>();
            words
                .stream()
                .filter(w -> !w.isEmpty())
                .forEach(word -> {
                    StringBuilder wTypeLine = new StringBuilder();
                    luceneMorphEng.getMorphInfo(word).forEach(wt -> wTypeLine.append(wt));
                    String wordsTypesLine = wTypeLine.toString();
                    boolean isContains = false;

                    for(String pn: particlesNamesEn){
                        if(wordsTypesLine.contains(pn)){isContains = true; break;}
                    }

                    if (!isContains) {
                        lemmaEn.addAll(luceneMorphEng.getNormalForms(word));
                    }
                });
        return lemmaEn;
    }

    public List<String> getWordsEn(String pageText){
        return new ArrayList<>(
                Arrays
                        .stream(pageText.replaceAll("[^a-zA-ZёЁ]+", " ").split("\\s+"))
                        .filter(w -> w.trim().length() > 3)
                        .map(word -> {
                            return word.toLowerCase(Locale.ROOT);
                        })
                        .toList()
        );
    }

    public List<String> getWordsRu(String pageText){
        return new ArrayList<>(
                Arrays
                        .stream(pageText.replaceAll("[^а-яА-ЯёЁ]+", " ").split("\\s+"))
                        .filter(w -> w.trim().length() > 3)
                        .map(word -> {
                            return word.toLowerCase(Locale.ROOT);
                        })
                        .toList()
        );
    }

    private void insertToIndex(Map<String, Integer> indexPageList, List<String> lemmaGotList, long siteId, long pageId){
        StringBuilder sql = new StringBuilder("insert into indices (lemma_id, page_id, rank) values ");
        lemmaRepository.findAllBySiteEntityId(siteId).forEach(lemma -> {
            if(lemmaGotList.contains(lemma.getLemma())){
                if(!sql.isEmpty()) {
                    sql.append(",");
                }
                int rank = indexPageList.get(lemma.getLemma());
                sql.append("(").append(lemma.getId()).append(",").append(pageId).append(",").append(rank).append(")");
            }
        });
        connectionApp.execSql(sql.toString());

        log.info("======= ПРОВЕРЬ ВСТАВИЛОСЬ ЛИ В БД ========  : ");
    }

}
