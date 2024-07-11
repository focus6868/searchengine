package searchengine.services;

import java.util.ArrayList;
import java.util.Collection;

public interface CRUDService<T> {
    T getById(Long id);
    Collection<T> getAll();
    void create(T item);
    void updateById(T item);
    void deleteById(Long id);
}
