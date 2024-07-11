package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import searchengine.model.Page;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<Index,Long> {

    @Transactional
    @Modifying
    void deleteByPageId(Long pageId);

    @Transactional
    @Modifying
    @Query(value = "TRUNCATE TABLE indices", nativeQuery = true)
    void truncateIndices();

    List<Index> findByPageId(Long PageId);

    List<Index> findByLemmaId(Long LemmaId);

    List<Index> findByPageIdIn(List<Long> ids);


}
