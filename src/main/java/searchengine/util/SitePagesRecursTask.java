package searchengine.util;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import searchengine.dto.PageDto;
import searchengine.services.ThreadParseSitesRunnerService;

import java.io.IOException;
import java.util.ArrayList;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.RecursiveTask;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SitePagesRecursTask extends RecursiveTask<List<PageDto>>{

    private final HashSet<String> UniqueLinks;
    private final String pathWithoutWWW;
    private final String path;
    private final Long siteID;
    private final Logger log = Logger.getLogger(SitePagesRecursTask.class.getName());
    private final ArrayList<PageDto> pagesList = new ArrayList<>();
    private final ArrayList<SitePagesRecursTask> taskList = new ArrayList<>();
    private final ConnectionApp connectionApp;

    public SitePagesRecursTask(HashSet<String> UniqueLinks, String pathWithoutWWW, String path, Long siteID) {
        this.UniqueLinks = UniqueLinks;
        this.pathWithoutWWW = pathWithoutWWW;
        this.path = path;
        this.siteID = siteID;
        connectionApp = new ConnectionApp();
    }

    @Override
    protected List<PageDto> compute() {

        Pattern pattern = Pattern.compile("(/[a-zA-Z0-9\\-_а-я?=%]+)+[\\.html]{0,5}");
        String pathClear = path.replaceAll("^[htps:/]+[a-zA-Z\\.]+\\.[a-z]+", "").replaceAll("//$", "");
        Matcher matcher;
        boolean isMatches;

        Document doc = null;
        try {
            doc = connectionApp.getDocument(path);
        } catch (IOException e) {
            log.info("ОШИБКА при вызове doc = getDocument(path) : " + path + " pathWithoutWWW " + pathWithoutWWW);
        }

        if(doc != null) {
            pagesList.add(getPage(
                                    pathClear.isEmpty() ? "/" : pathClear
                                    , connectionApp.statusCode(path)
                                    , doc.html().replaceAll("'", "\"")
                                    , siteID)
            );

            for (Element element : doc.select("a")) {

                String pathWithoutHttp = element.absUrl("href").replaceAll("^[htps:/w.]+[a-z]+\\.[a-z]{2,3}", "").replaceAll("/$", "");
                String absUrlClear = element.absUrl("href").replaceAll("^[htps]+", "http").replaceAll("/$", "");
                matcher = pattern.matcher(pathWithoutHttp);
                isMatches = matcher.matches();

                if (absUrlClear.contains(pathWithoutWWW)
                        && !pathWithoutHttp.contains(pathWithoutWWW)
                        && UniqueLinks.add(pathWithoutHttp)
                        && isMatches) {
                    createPageStartTask(taskList, absUrlClear, pathWithoutHttp);
                }
            }

            if (!ThreadParseSitesRunnerService.isInterrupted){
                for (SitePagesRecursTask task : taskList) {
                    pagesList.addAll(task.join());
                }
            }
        }
            return pagesList;
    }

    private void createPageStartTask(List<SitePagesRecursTask> taskList, String absUrlClear, String pathWithoutHttp){
        String content = "";
        int code = 200;
        try {
            Document document = connectionApp.getDocument(absUrlClear);
            content = document.html();
            content = content.replaceAll("'", "\"");

            if(content.isEmpty()){
                code = connectionApp.statusCode(absUrlClear);
            }

            pagesList.add(getPage(pathWithoutHttp, code, content, siteID));

        } catch (IOException e) {
            log.info("=========== !!!!!-------!!!! EXCEPTION on invoke absUrlClear ========== " + absUrlClear);
        }

        if (code < 300 && !content.isEmpty() && !ThreadParseSitesRunnerService.isInterrupted) {
            SitePagesRecursTask task = new SitePagesRecursTask(UniqueLinks, pathWithoutWWW, absUrlClear, siteID);
            task.fork();
            taskList.add(task);
        }
    }

    private PageDto getPage(String path, int code, String content, Long siteId) {
        return new PageDto(path
                , code
                , content
                , siteId);
    }
}

