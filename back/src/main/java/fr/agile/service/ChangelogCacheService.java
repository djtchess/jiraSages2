package fr.agile.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import fr.agile.IssueChangelogData;

@Service
public class ChangelogCacheService {

    private static final Duration CHANGELOG_TTL = Duration.ofHours(2);

    private final Map<String, IssueChangelogData> changelogCache = new ConcurrentHashMap<>();
    private final Map<String, Instant> changelogCacheTs = new ConcurrentHashMap<>();

    public IssueChangelogData getOrLoad(String issueKey, Supplier<IssueChangelogData> loader) {
        Instant now = Instant.now();
        Instant ts = changelogCacheTs.get(issueKey);
        IssueChangelogData cached = changelogCache.get(issueKey);

        if (cached != null && ts != null && ts.plus(CHANGELOG_TTL).isAfter(now)) {
            return cached;
        }

        IssueChangelogData fresh = loader.get();
        changelogCache.put(issueKey, fresh);
        changelogCacheTs.put(issueKey, now);
        return fresh;
    }

    public void evict(String issueKey) {
        changelogCache.remove(issueKey);
        changelogCacheTs.remove(issueKey);
    }
}
