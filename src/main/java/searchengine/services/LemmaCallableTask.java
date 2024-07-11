package searchengine.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/** todo
 * Задача, получить такой (1-param = page PATH, 2-param = LEMMAS LIST) список лемм на странице:
 *  - чтобы посчитать количество страниц с данной леммой (LEMMA.FREQUENCY)
 *  - чтобы посчитать количество одной леммы на странице (INDEX.RANK)
 * На вход подаем (String pageContent)
 * На выходе получаем List<String Lemma> страницы
 *
 * */

public class LemmaCallableTask implements Callable<List<String>> {

    private static final Logger log = LoggerFactory.getLogger(LemmaCallableTask.class);
    String pageText;
    /** Количество лемм на сайте, а не на странице надо считать */
    public LemmaCallableTask(String pageText){
        this.pageText = pageText;
    }
    /** Метод возвращает список лемм для одной страницы */
    @Override
    public List<String> call() throws Exception {
        LemmaService ls = new LemmaService();

        List<String> lemmaList = new ArrayList<>(ls.getLemmaRuList(ls.getWordsRu(pageText)));
        lemmaList.addAll(ls.getLemmaEnList(ls.getWordsEn(pageText)));

       return lemmaList;
    }


}
