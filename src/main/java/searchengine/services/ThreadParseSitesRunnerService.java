package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.*;
import searchengine.model.Page;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repository.*;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Service
public class ThreadParseSitesRunnerService {
    @Autowired
    private SitesList sitesList;
    @Autowired
    private ParameterList parameterList;
    @Autowired
    private SiteEntityRepository siteEntityRepository;
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private LemmaRepository lemmaRepository;
    @Autowired
    private IndexRepository indexRepository;
    @Autowired
    private LemmaService lemmaService;

    public String urlPattern = "^[htps:/]+[w]{0,3}[\\.]{0,1}";
    public static TreeMap<String, SiteEntity> sitesEntityMap = new TreeMap<>();
    public static TreeMap<String, String> appParam = new TreeMap<>();
    public static volatile boolean isInterrupted = false;
    Logger log = Logger.getLogger(ThreadParseSitesRunnerService.class.getName());

    public Object runIndexing(){
        try {

            createAppParam();

            indexRepository.truncateIndices();
            lemmaRepository.truncateLemmas();
            pageRepository.truncatePages();
            siteEntityRepository.truncateSites();

            for (Site site : sitesList.getSites()) {
                SiteEntity siteEnt = new SiteEntity();

                siteEnt.setName(site.getName());
                siteEnt.setUrl(site.getUrl());
                siteEnt.setStatus(Status.INDEXING);
                siteEnt.setLastError("noting");
                siteEnt.setStatusTime(LocalDateTime.now());

                siteEntityRepository.save(siteEnt);
                siteEntityRepository.flush();
            }

            for (SiteEntity siteEntity : siteEntityRepository.findAll()) {
                String baseUrlTemplate = siteEntity.getUrl().replaceAll(urlPattern, "");
                sitesEntityMap.put(baseUrlTemplate, siteEntity);
            }

            ThreadPoolExecutor service =(ThreadPoolExecutor) Executors.newFixedThreadPool(2);
            service.setKeepAliveTime(1, TimeUnit.HOURS);
            service.setMaximumPoolSize(sitesList.getSites().size());

            for (Site site : sitesList.getSites()) {
                if (site.getUrl().contains("sendel.ru")
                        ||
                    site.getUrl().contains("svetlovka.ru")
                ) {
                    String pathWithoutWWW = site.getUrl().replaceAll(urlPattern, "");

                    ThreadParseSite threadParseSite = new ThreadParseSite(
                              pathWithoutWWW
                            , site.getUrl()
                            , pageRepository
                            , siteEntityRepository
                            , lemmaRepository
                    );

                    service.submit(threadParseSite);


                    log.info("-=-=-=-= ЗАПУЩЕН ПОТОК site.getUrl() =-=-=-=-= " + site.getUrl());
                }
            }
        }   catch(Exception e){
            return new RequestResultError("Индексация сайта не запущена " + e.getMessage() );
        }
        return new RequestResultSuccess(true);
    }

    public ResponseEntity<?> indexPage(String path){
        createAppParam();
        return lemmaService.IndexOnePage(path);
    }

    public RequestResultSuccess stopIndexing(){
        ThreadParseSitesRunnerService.isInterrupted = true;
        return new RequestResultSuccess(true);
    }

    public void createAppParam(){
        List<Parameter> listParams = new ArrayList<>(parameterList.getParameters());
        for(Parameter parameter: listParams){
                appParam.put(parameter.getName(), parameter.getValue());
        }
    }
}
