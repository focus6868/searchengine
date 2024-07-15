package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;

import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Long> {

    List<Lemma> findAllBySiteEntityId(long SiteEntityId);

    @Transactional
    @Modifying
    @Query(value = "TRUNCATE TABLE lemmas", nativeQuery = true)
    void truncateLemmas();
}
