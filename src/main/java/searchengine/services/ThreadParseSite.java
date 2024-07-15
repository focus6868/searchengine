package searchengine.services;

import org.jsoup.Jsoup;
import org.slf4j.LoggerFactory;
import searchengine.dto.IndexPage;
import searchengine.dto.PageDto;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteEntityRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class ThreadParseSite extends Thread {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ThreadParseSite.class);
    private String pathWithoutWWW;
    private String pagePath;
    private PageRepository pageRepository;
    private SiteEntityRepository siteEntityRepository;
    private LemmaRepository lemmaRepository;
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
    }

    @Override
    public void run() {
        Long startMillis = System.currentTimeMillis();
        Logger log = Logger.getLogger(ThreadParseSite.class.getName());
        insertQuery = new StringBuilder();
        LinkChecker linkChecker = new LinkChecker();
        ConnectApp connectApp = new ConnectApp();


        SiteEntity site = ThreadParseSitesRunnerService.sitesEntityMap.get(pathWithoutWWW);
        long siteId = site.getId();
        log.info("siteURL ===== " + pathWithoutWWW);
        log.info("pagePath ===== " + pagePath);
        log.info("siteId ===== " + siteId);

      //  System.exit(0);

        TreeSet<PageDto> pagesList = new TreeSet<>(new ForkJoinPool().invoke(new SitePagesRecursTask(linkChecker.getUniqueLinks()
                , pathWithoutWWW
                , pagePath
                , siteId
        )));

        log.info( "\n\n ==== INSERTION TO PAGE ===== complete \n pagesList.size() " + pagesList.size() + "\n");

        log.info("&&&&&&&&&&&===== COMPLETED FOR " + (System.currentTimeMillis() - startMillis) + " миллисекунд");

        try {
            for (PageDto page : pagesList) {
                    insertQuery.append(insertQuery.isEmpty() ? "" : ",")
                            .append("('")
                            .append(page.getCode()).append("','")
                            .append(page.getContent()).append("','")
                            .append(page.getPath()).append("','")
                            .append(siteId)
                            .append("')");
                if (insertQuery.length() >= 1000000) {
                    connectApp.execSql("insert into pages(`code`, content, `path`, site_entity_id) values" + insertQuery);
                    insertQuery = new StringBuilder();
                }
            }
            if(!insertQuery.isEmpty()){
                connectApp.execSql("insert into pages(`code`, content, `path`, site_entity_id) values" + insertQuery);
            }
        } catch (Exception e){
            e.printStackTrace();
        }

        List<Page> pages = new ArrayList<>(pageRepository.findAllBySiteId(siteId));
        List<Page> pagesNoError = new ArrayList<>(pages.stream().filter(p -> p.getCode() < 300).toList());

        /** 1 - page PATH, 2 - page CONTENT*/
        TreeMap<String, FutureTask<List<String>>> taskMap = new TreeMap<>();
        ThreadPoolExecutor service = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
        service.setKeepAliveTime(1, TimeUnit.HOURS);
        service.setMaximumPoolSize(pages.size());

        TreeMap<String, List<String>> PagesLemmasMap = new TreeMap<>();

        pagesNoError.forEach(p -> {
                String pageText = Jsoup.parse(p.getContent()).body().text();
                LemmaCallableTask lemmaCallableTask = new LemmaCallableTask(pageText);
                FutureTask<List<String>> futureTask = (FutureTask<List<String>>) service.submit(lemmaCallableTask);
                taskMap.put(p.getPath(), futureTask);
        });

        for(Map.Entry<String, FutureTask<List<String>>> item: taskMap.entrySet()){
            List<String> words = new ArrayList<>();
            try {
                words.addAll(item.getValue().get());
                PagesLemmasMap.put(item.getKey(), words);
            } catch (InterruptedException e) {
                log.info( "======= ThreadParseSite ======== InterruptedException");
            } catch (ExecutionException e) {
                log.info( "======= ThreadParseSite WORDS.SIZE() ======== ExecutionException " + words.size());
            }
        }
        service.shutdown();

        insertQuery = new StringBuilder();

        /** Получение Map<String lemma, Integer Frequensy> Frequensy (number of page with certain lemma) data for insertion to lemmas */
        TreeMap<String, Integer> lemmaFrequency = new TreeMap<>();
        for(Map.Entry<String, List<String>> pageLemmas:  PagesLemmasMap.entrySet()){
            HashSet<String> pageLemmasUnique = new HashSet<>(pageLemmas.getValue());
            for(String lemma: pageLemmasUnique) {
                int frequency = lemmaFrequency.isEmpty() ? 1 : (lemmaFrequency.get(lemma)==null ? 1 : lemmaFrequency.get(lemma) + 1);
                lemmaFrequency.put(lemma, frequency);
            }
        }
        /** SQL for insertion into lemmas */
        for (Map.Entry<String, Integer> item : lemmaFrequency.entrySet()) {
            insertQuery.append(insertQuery.isEmpty() ? "" : ",")
                    .append("('")
                    .append(item.getKey()).append("','")
                    .append(item.getValue()).append("','")
                    .append(siteId)
                    .append("')");
            if (insertQuery.length() >= 1000000) {
                connectApp.execSql("insert into lemmas(lemma, frequency, site_entity_id) values" + insertQuery);
                insertQuery = new StringBuilder();
            }
        }

        if(!insertQuery.isEmpty()){
            connectApp.execSql("insert into lemmas(lemma, frequency, site_entity_id) values" + insertQuery);
        }
        log.info(" ==== INSERTION TO LEMMA ===== complete");


        List<IndexPage> indexPageList = new ArrayList<>();
        List<Lemma> lemmasDb = new ArrayList<>(lemmaRepository.findAllBySiteEntityId(siteId));

        log.info("========= LEMMA DB =========  : " + lemmasDb.size());

        insertQuery = new StringBuilder();
        HashSet<String> uniqueLemmas = new HashSet<>();
        for(Page page: pagesNoError){
            List<String> lemmasOnPage = new ArrayList<>(PagesLemmasMap.get(page.getPath()));
                for (String lemma : lemmasOnPage) {
                    long rank = lemmasOnPage.stream().filter(l -> l.equals(lemma)).count();
                    if (rank != 0 && uniqueLemmas.add(lemma + "_" + page.getId())) {
                        Long lemmaId = lemmasDb.stream().filter(l -> l.getLemma().equals(lemma)).findFirst().get().getId();
                        indexPageList.add(new IndexPage(page.getId(), lemmaId, rank));
                    }
                }
        }

        /** SQL for insertion into index */
        for (IndexPage item : indexPageList) {
            insertQuery.append(insertQuery.isEmpty() ? "" : ",")
                    .append("('")
                    .append(item.getLemmaId()).append("','")
                    .append(item.getPageId()).append("','")
                    .append(item.getRank())
                    .append("')");
            if (insertQuery.length() >= 1000000) {
                connectApp.execSql("insert into indices(lemma_id, page_id, rank) values" + insertQuery);
                insertQuery = new StringBuilder();
            }
        }

        if(!insertQuery.isEmpty()){
            connectApp.execSql("insert into indices(lemma_id, page_id, rank) values" + insertQuery);
        }

        site.setStatus(Status.INDEXED);
        site.setStatusTime(LocalDateTime.now());
        siteEntityRepository.save(site);

        log.info(" ==== INSERTION TO INDEX ===== complete for siteId : " + siteId);
        log.info(" ==== Пропарсил  " + pathWithoutWWW + "  ==== за =====  " + (System.currentTimeMillis()-startMillis) + "ms");
    }

}
