package searchengine.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.search.SearchResult;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.SearchingByQueryService;
import searchengine.services.ThreadParseSitesRunnerService;
import searchengine.services.StatisticsServiceImpl;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsServiceImpl statisticsService;
    @Autowired
    private SearchingByQueryService searchingByQueryService;
    @Autowired
    private ThreadParseSitesRunnerService threadIndexingRunnerService;

    public ApiController(StatisticsServiceImpl statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @RequestMapping("/startIndexing")
    public ResponseEntity<?> startIndexing() {
        return ResponseEntity.ok(threadIndexingRunnerService.runIndexing());
    }

    @PostMapping("/indexPage")
    public ResponseEntity<?> getSitesList(@RequestParam String url) throws IOException {
        return threadIndexingRunnerService.indexPage(url);
    }

    @RequestMapping("/stopIndexing")
    public ResponseEntity<?> stopIndexing() {
        return ResponseEntity.ok(threadIndexingRunnerService.stopIndexing());
    }

    @GetMapping("/search")
    public SearchResult search(@RequestParam String query
            , @RequestParam(defaultValue = "0", required = false) int offset
            , @RequestParam(defaultValue = "20", required = false) int limit
            , @RequestParam(value = "site", required = false) String site) throws IOException {
        return searchingByQueryService.search(query, 5, 5, site);
    }
}
