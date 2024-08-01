package searchengine.util;

import org.jsoup.Jsoup;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import searchengine.dto.IndexPage;
import searchengine.dto.PageDto;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteEntityRepository;
import searchengine.services.ThreadParseSitesRunnerService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

public class ThreadParseSite extends Thread {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ThreadParseSite.class);
    private final String pathWithoutWWW;
    private final String pagePath;
    private final PageRepository pageRepository;
    private final SiteEntityRepository siteEntityRepository;
    private final LemmaRepository lemmaRepository;
    ConnectionApp connectionApp;
    StringBuilder insertQuery;

    public ThreadParseSite(String pathWithoutWWW
            , String pagePath
            , PageRepository pageRepository
            , SiteEntityRepository entityRepository
            , LemmaRepository lemmaRepository
    ) {
        this.pathWithoutWWW = pathWithoutWWW;
        this.pagePath = pagePath;
        this.pageRepository = pageRepository;
        this.siteEntityRepository = entityRepository;
        this.lemmaRepository = lemmaRepository;
        this.connectionApp = new ConnectionApp();
    }

    @Override
    public void run() {

        SiteEntity site = ThreadParseSitesRunnerService.sitesEntityMap.get(pathWithoutWWW);

        long siteId = site.getId();

        TreeSet<PageDto> pagesList = new TreeSet<>(crawlingPages(siteId));

        log.info(" ======= Обход страниц START ===== " + siteId + "  ====== pagesList.size() : " + pagesList.size());

        insertToPage(pagesList, siteId);

        log.info(" ======= Обход страниц завершён ===== " + siteId + "  ====== pagesList.size() : " + pagesList.size());

        List<Page> pages = new ArrayList<>(pageRepository.findAllBySiteId(siteId).stream().filter(p -> p.getCode() < 300).toList());

        TreeMap<String, List<String>> pagesLemmasMap = new TreeMap<>();
        try {
            pagesLemmasMap.putAll(getPagesLemmas(pages));
            insertToLemmas(calcLemmaFrequency(pagesLemmasMap), siteId);

            List<IndexPage> indexPageList = new ArrayList<>(calcIndexRunk(pages, pagesLemmasMap, siteId));
            insertToIndex(indexPageList);

        } catch (InterruptedException | ExecutionException e) {
            site.setStatus(Status.FAILED);
            throw new RuntimeException(e);
        } finally {
            site.setStatusTime(LocalDateTime.now());
            site.setStatus(Status.INDEXED);
            siteEntityRepository.save(site);
        }
    }

    private Set<PageDto> crawlingPages(long siteId){
        LinkChecker linkChecker = new LinkChecker();
        Set<PageDto> pageDtoSet = new TreeSet<>();

        pageDtoSet.addAll(new ForkJoinPool()
                                .invoke(new SitePagesRecursTask(linkChecker.getUniqueLinks()
                                        , pathWithoutWWW
                                        , pagePath
                                        , siteId
                                ))
        );
        return pageDtoSet;
    };

    private void insertToPage(TreeSet<PageDto> pagesList, long siteId) {
        insertQuery = new StringBuilder();
            for (PageDto page : pagesList) {
                insertQuery.append(insertQuery.isEmpty() ? "" : ",")
                        .append("('")
                        .append(page.getCode()).append("','")
                        .append(page.getContent()).append("','")
                        .append(page.getPath()).append("','")
                        .append(siteId)
                        .append("')");
                if (insertQuery.length() >= 1000000) {
                    connectionApp.execSql("insert into pages(`code`, content, `path`, site_entity_id) values" + insertQuery);
                    insertQuery = new StringBuilder();
                }
            }
            if (!insertQuery.isEmpty()) {
                connectionApp.execSql("insert into pages(`code`, content, `path`, site_entity_id) values" + insertQuery);
            }
            log.info(" =========== INSERTION TO PAGE FOR SITE =========== " + siteId);
    }

    /** MAP ( 1 - page PATH, 2 - page CONTENT as words in html.text() ) */
    private TreeMap<String, List<String>> getPagesLemmas(List<Page> pages) throws InterruptedException, ExecutionException {

        TreeMap<String, List<String>> pageLemmas = new TreeMap<>();
        TreeMap<String, FutureTask<List<String>>> taskMap = new TreeMap<>();
        ThreadPoolExecutor service = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
        service.setKeepAliveTime(1, TimeUnit.HOURS);
        service.setMaximumPoolSize(pages.size());

        pages.forEach(p -> {
            String pageText = Jsoup.parse(p.getContent()).body().text();
            LemmaCallableTask lemmaCallableTask = new LemmaCallableTask(p.getPath(), pageText);
            FutureTask<List<String>> futureTask = (FutureTask<List<String>>) service.submit(lemmaCallableTask);
            taskMap.put(p.getPath(), futureTask);
        });

        for(Map.Entry<String, FutureTask<List<String>>> item: taskMap.entrySet()){
            List<String> words = new ArrayList<>();
                words.addAll(item.getValue().get());
                pageLemmas.put(item.getKey(), words);
        }
        service.shutdown();
        return pageLemmas;
    }

    private TreeMap<String, Integer> calcLemmaFrequency(Map<String, List<String>> pagesLemmasMap){
        /** Получение Map<String lemma, Integer Frequensy> Frequensy (number of page with certain lemma) data for insertion to lemmas */
        TreeMap<String, Integer> lemmaFrequency = new TreeMap<>();
        for(Map.Entry<String, List<String>> pageLemmas:  pagesLemmasMap.entrySet()){
            HashSet<String> pageLemmasUnique = new HashSet<>(pageLemmas.getValue());
            for(String lemma: pageLemmasUnique) {
                int frequency = lemmaFrequency.isEmpty() ? 1 : (lemmaFrequency.get(lemma)==null ? 1 : lemmaFrequency.get(lemma) + 1);
                lemmaFrequency.put(lemma, frequency);
            }
        }
        return lemmaFrequency;
    }

    private void insertToLemmas(TreeMap<String, Integer> lemmaFrequency, long siteId){
        insertQuery = new StringBuilder();
        for (Map.Entry<String, Integer> item : lemmaFrequency.entrySet()) {
            insertQuery.append(insertQuery.isEmpty() ? "" : ",")
                    .append("('")
                    .append(item.getKey()).append("','")
                    .append(item.getValue()).append("','")
                    .append(siteId)
                    .append("')");
            if (insertQuery.length() >= 1000000) {
                connectionApp.execSql("insert into lemmas(lemma, frequency, site_entity_id) values" + insertQuery);
                insertQuery = new StringBuilder();
            }
        }
        if(!insertQuery.isEmpty()){
            connectionApp.execSql("insert into lemmas(lemma, frequency, site_entity_id) values" + insertQuery);
        }
        log.info(" ==== INSERTION TO LEMMA FOR SITE ===== complete " + siteId);
    }

    private List<IndexPage> calcIndexRunk(List<Page> pages, TreeMap<String, List<String>> pagesLemmasMap, long siteId){
        List<IndexPage> indexPageList = new ArrayList<>();

        List<Lemma> lemmasDb = new ArrayList<>(lemmaRepository.findAllBySiteEntityId(siteId));

        HashSet<String> uniqueLemmas = new HashSet<>();
        for(Page page: pages){
            List<String> lemmasOnPage = new ArrayList<>(pagesLemmasMap.get(page.getPath()));
            for (String lemma : lemmasOnPage) {
                long rank = lemmasOnPage.stream().filter(l -> l.equals(lemma)).count();
                if (rank != 0 && uniqueLemmas.add(lemma + "_" + page.getId())) {
                    Long lemmaId = lemmasDb.stream().filter(l -> l.getLemma().equals(lemma)).findFirst().get().getId();
                    indexPageList.add(new IndexPage(page.getId(), lemmaId, rank));
                }
            }
        }
        return indexPageList;
    }

    private void insertToIndex(List<IndexPage> indexPageList){
        insertQuery = new StringBuilder();
        /** SQL for insertion into index */
        for (IndexPage item : indexPageList) {
            insertQuery.append(insertQuery.isEmpty() ? "" : ",")
                    .append("('")
                    .append(item.getLemmaId()).append("','")
                    .append(item.getPageId()).append("','")
                    .append(item.getRank())
                    .append("')");
            if (insertQuery.length() >= 1000000) {
                connectionApp.execSql("insert into indices(lemma_id, page_id, rank) values" + insertQuery);
                insertQuery = new StringBuilder();
            }
        }

        if(!insertQuery.isEmpty()){
            connectionApp.execSql("insert into indices(lemma_id, page_id, rank) values" + insertQuery);
        }
        log.info(" ==== INSERTION TO INDICES ===== complete");
    }
}
