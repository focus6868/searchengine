package searchengine.services;

/*import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;*/

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
import org.springframework.stereotype.Service;
import searchengine.config.RequestResultSuccess;
        import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteEntityRepository;

@Service
public class LemmaService {

    private static final List<String> particlesNames = Arrays.stream((new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"})).toList();

    @Autowired
    private SiteEntityRepository siteEntityRepository;
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private IndexRepository indexRepository;
    @Autowired
    LemmaRepository lemmaRepository;
    LuceneMorphology luceneMorphRus;
    LuceneMorphology luceneMorphEng;

    private Logger log = Logger.getLogger(LemmaService.class.getName());

    public LemmaService() throws IOException {
        luceneMorphRus = new RussianLuceneMorphology();
        luceneMorphEng = new EnglishLuceneMorphology();
    }

    public ResponseEntity<?> IndexOnePage(String path){

        ConnectApp connectApp = new ConnectApp();
        TreeMap<String, Integer> indexPageList = new TreeMap<>();
        String urlHead = path.replaceAll(connectApp.getUrlPatternTail(),"");
        String urlTail = path.replaceAll(connectApp.getUrlPatternHead(),"");
        StringBuilder sqlString = new StringBuilder();
        SiteEntity siteEntity = null;
        String sql;
        Long pageId = 0L;
        Long siteId = 0L;
        int statusCode;

        List<SiteEntity> sites = siteEntityRepository.findByUrl(urlHead);

        Document doc;

        if (!sites.isEmpty()) {

            List<Page> pages = pageRepository.findByPath(urlTail);

            if (sites.stream().findFirst().isPresent()) {
                siteEntity = sites.stream().findFirst().get();
                siteId = siteEntity.getId();
            }
            if (pages.stream().findFirst().isPresent()) { pageId = pages.stream().findFirst().get().getId(); }

            List<Lemma> lemmaRepositoryAll = lemmaRepository.findAllBySiteEntityId(siteId);

            try {
                doc = connectApp.getDocument(path);
            } catch (IOException e) {
                return connectApp.throwException();
            }

                indexRepository.deleteByPageId(pageId);
                pageRepository.deleteById(pageId);

            statusCode = connectApp.statusCode(path);
            Page page = new Page();
            page.setContent(doc.html());
            page.setCode(statusCode);
            page.setPath(urlTail);
            page.setSiteEntity(siteEntity);
            pageRepository.save(page);

            try {
                log.info(" ======= GET_LEMMAS ======== : ");

                String pageText = Jsoup.parse(page.getContent()).text();

                indexPageList.putAll(getLemmas(pageText));

                List<String> lemmaGotList = indexPageList.keySet().stream().toList();
                List<Lemma> lemmaBdList = lemmaRepositoryAll.stream().filter(l -> {
                    return lemmaGotList.contains(l.getLemma());
                }).toList();

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
                        Integer rank = indexPageList.get(gotLemma);
                        sqlString.append("(").append(1).append(",\"").append(gotLemma).append("\")");
                    }
                }
                if(!sqlString.isEmpty()){
                    sql = "insert into lemmas (frequency, lemma) values" + sqlString;
                    log.info("====== S Q L ======= " + sql);
                    connectApp.execSql(sql);
                }

                StringBuilder newSqlString = new StringBuilder();
                lemmaRepository.findAll().forEach(lemma -> {
                    if(lemmaGotList.contains(lemma.getLemma())){
                        if(!newSqlString.isEmpty()) {
                            newSqlString.append(",");
                        }
                            int rank = indexPageList.get(lemma.getLemma());
                            newSqlString.append("(").append(lemma.getId()).append(",").append(page.getId()).append(",").append(rank).append(")");
                    }
                });

                sql = "insert into indices (lemma_id, page_id, rank) values" + newSqlString;
                connectApp.execSql(sql);

                log.info("======= ПРОВЕРЬ ВСТАВИЛОСЬ ЛИ В БД ========  : ");

            } catch(IOException e){
                e.printStackTrace();
            }

        } else{
            log.info("=== sites.isEmpty() = TRUE ===");
        }
        return ResponseEntity.ok(new RequestResultSuccess(true));
    }

    /** TODO HashMap<String, IndexPage>
     * 1 - ключ мэпа - Лемма, 2 - frequency ()
     */
    public TreeMap<String, Integer> getLemmas(String pageText) throws IOException {
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
                    try {
                        List<String> wordTypes = luceneMorphRus.getMorphInfo(word);
                        List<String> list = particlesNames.stream().filter(wordTypes::contains).toList();
                        if (word.length() > 3 && list.isEmpty()) {
                            lemmaRu.addAll(luceneMorphRus.getNormalForms(word));
                        }
                    }catch(org.apache.lucene.morphology.WrongCharaterException ignored){
                        System.out.println("============= GET_LEMMAS_RU_LIST ============= " + word);
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
                    List<String> wordTypes = luceneMorphEng.getMorphInfo(word);
                    List<String> list = particlesNames.stream().filter(wordTypes::contains).toList();

                    if (word.length() > 3 && list.isEmpty()) {
                        lemmaEn.addAll(luceneMorphEng.getNormalForms(word));
                    }
                });
        return lemmaEn;
    }

    public List<String> getWordsEn(String pageText){
        return new ArrayList<>(
                Arrays
                        .stream(pageText.replaceAll("[^a-zA-Z]+", " ").split("\\s+"))
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
                        .stream(pageText.replaceAll("[^а-яА-Я]+", " ").split("\\s+"))
                        .filter(w -> w.trim().length() > 3)
                        .map(word -> {
                            return word.toLowerCase(Locale.ROOT);
                        })
                        .toList()
        );
    }
}
