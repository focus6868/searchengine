package searchengine.services;

import lombok.Getter;
import lombok.Setter;
import org.jsoup.Jsoup;
import org.jsoup.Connection.Response;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.RequestResultError;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Service
@Getter
@Setter
public class ConnectApp {

    private final String dbName = "search_engine";
    private final String bdUser = "skillbox";
    private final String bdPwd = "skillbox";

    private final String urlPatternHead = "^[htpsw.:/]+[a-zA-Z\\.]+\\.[a-z]+";
    private final String urlPatternTail = "\\/[_\\-\\/a-z0-9]+$";
    private final String urlPattern = "^[htps:/]+[w]{0,3}[\\.]{0,1}";

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/" + dbName +
                        "?user=" + bdUser +
                        "&password=" + bdPwd
        );
    }

    public void execSql(String sql){
        try {
            Connection connection = getConnection();
            connection.createStatement().execute(sql);
            connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Document getDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .userAgent(getParamByKey("agent"))
                .referrer(getParamByKey("referer"))
                .timeout(Integer.parseInt(getParamByKey("timeout")))
                .get();
    }

    public int statusCode(String path) {
        int statusCode = 403;
        try {
            if(!getDocument(path).title().isEmpty()){
                statusCode = 200;
            } else{
                Response response = Jsoup.connect(path)
                    .userAgent(getParamByKey("agent"))
                    .timeout(Integer.parseInt(getParamByKey("timeout")))
                        .execute();
                statusCode = response.statusCode();
            }
        } catch (IOException e) {
            System.out.println("io + "+e);
        }
        return statusCode;
    }

    public ResponseEntity<?> throwException(){
        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .header("")
                .body(new RequestResultError("Данная страница находится за пределами сайтов, указанных в конфигурационном файле"));
    }

    private String getParamByKey(String key){
        return ThreadParseSitesRunnerService.appParam.get(key);
    }
}
