package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SiteEntity;

import java.util.List;

@Repository
public interface SiteEntityRepository extends JpaRepository<SiteEntity,Long> {
    @Modifying
    @Transactional
    @Query(value = "delete from sites;", nativeQuery = true)
    void deleteAllSites();

    @Transactional
    @Modifying
    @Query(value = "call proc_tuncate_sites;", nativeQuery = true)
    void truncateSites();

    @Query(value = "select s.* from sites as s where s.url = ?1", nativeQuery = true)
    List<SiteEntity> findByUrl(String url);

}
