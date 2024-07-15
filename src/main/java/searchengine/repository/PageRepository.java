package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;

import java.util.List;
@Repository
public interface PageRepository extends JpaRepository<Page,Long> {

    List<Page> findByIdIn(List<Long> ids);

    @Modifying
    @Transactional
    @Query(value = "delete pages from pages;", nativeQuery = true)
    void deleteAllPages();

    @Modifying
    @Transactional
    @Query(value = "TRUNCATE TABLE pages;", nativeQuery = true)
    void truncatePages();

    List<Page> findByPath(String path);

    //List<Page> findBySite(SiteEntity siteEntity);

    @Query(value = "select p.* from pages p where p.site_entity_id =:id", nativeQuery = true)
    List<Page> findAllBySiteId(long id);
}
