package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.*;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repository.*;
import searchengine.util.ThreadParseSite;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
    ConnectDbParameter connectDbParameter;
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
    public static final int threads = 2;
    Logger log = Logger.getLogger(ThreadParseSitesRunnerService.class.getName());

    public Object runIndexing(){
        try {

            createAppParam();
            clearAllData();

            for (SiteEntity siteEntity : siteEntityRepository.findAll()) {
                String baseUrlTemplate = siteEntity.getUrl().replaceAll(urlPattern, "");
                sitesEntityMap.put(baseUrlTemplate, siteEntity);
            }

            ThreadPoolExecutor service =(ThreadPoolExecutor) Executors.newFixedThreadPool(threads);
            service.setKeepAliveTime(1, TimeUnit.HOURS);
            service.setMaximumPoolSize(Integer.max(sitesList.getSites().size(), threads));

            for (Site site : sitesList.getSites()) {
                    String pathWithoutWWW = site.getUrl().replaceAll(urlPattern, "");

                    ThreadParseSite threadParseSite = new ThreadParseSite(
                              pathWithoutWWW
                            , site.getUrl()
                            , pageRepository
                            , siteEntityRepository
                            , lemmaRepository
                    );

                    service.submit(threadParseSite);

                    log.info("===== ЗАПУЩЕН ПОТОК site.getUrl()===== " + site.getUrl());
            }
        }   catch(Exception e){
            return new RequestResultError("Индексация сайта не запущена " + e.getMessage() );
        }
        return new RequestResultSuccess(true);
    }

    public void createAppParam(){
        List<Parameter> listParams = new ArrayList<>(parameterList.getParameters());
        for(Parameter parameter: listParams){
                appParam.put(parameter.getName(), parameter.getValue());
        }

        Class<?> ConnectDbParameter = connectDbParameter.getClass();

        List<String> fieldsNames = new ArrayList<>();
        List<Method> methods = new ArrayList<>();

        Arrays.stream(ConnectDbParameter.getDeclaredFields()).forEach(f -> fieldsNames.add(f.getName()));
        methods.addAll(Arrays.asList(ConnectDbParameter.getDeclaredMethods()));

        fieldsNames.forEach(f -> {
                    methods.forEach(m -> {
                        if (m.getName().toLowerCase(Locale.ROOT).contains(f.toLowerCase(Locale.ROOT))) {
                            try {
                                m.setAccessible(true);
                                appParam.put(f, m.invoke(connectDbParameter).toString());
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException(e);
                            } catch (InvocationTargetException e) {
                                throw new RuntimeException(e);
                            }
                        }

                    });
                }
        );
    }

    private void clearAllData(){
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
    }

    public ResponseEntity<?> indexPage(String path) throws IOException {
        createAppParam();
        return lemmaService.IndexOnePage(path);
    }

    public RequestResultSuccess stopIndexing(){
        ThreadParseSitesRunnerService.isInterrupted = true;
        return new RequestResultSuccess(true);
    }

}
