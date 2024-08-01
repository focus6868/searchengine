package searchengine.util;

import lombok.Getter;

import java.util.HashSet;

@Getter
public class LinkChecker {
    private final HashSet<String> uniqueLinks;

    public LinkChecker() {
        this.uniqueLinks = new HashSet<>();
    }
}
