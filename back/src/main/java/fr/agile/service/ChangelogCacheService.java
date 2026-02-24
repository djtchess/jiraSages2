package fr.agile.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import fr.agile.IssueChangelogData;

@Service
public class ChangelogCacheService {

    @Value("${jira.changelog.cache.ttlMinutes:120}")
    private long changelogTtlMinutes;

    private final Map<String, IssueChangelogData> changelogCache = new ConcurrentHashMap<>();
    private final Map<String, Instant> changelogCacheTs = new ConcurrentHashMap<>();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong cacheLoads = new AtomicLong();
    private final AtomicLong cacheEvictions = new AtomicLong();

    public IssueChangelogData getOrLoad(String issueKey, Supplier<IssueChangelogData> loader) {
        Instant now = Instant.now();
        Instant ts = changelogCacheTs.get(issueKey);
        IssueChangelogData cached = changelogCache.get(issueKey);
        Duration ttl = Duration.ofMinutes(Math.max(1, changelogTtlMinutes));

        if (cached != null && ts != null && ts.plus(ttl).isAfter(now)) {
            cacheHits.incrementAndGet();
            return cached;
        }

        cacheMisses.incrementAndGet();

        IssueChangelogData fresh = loader.get();
        cacheLoads.incrementAndGet();
        changelogCache.put(issueKey, fresh);
        changelogCacheTs.put(issueKey, now);
        return fresh;
    }

    public void evict(String issueKey) {
        if (changelogCache.remove(issueKey) != null) {
            cacheEvictions.incrementAndGet();
        }
        changelogCacheTs.remove(issueKey);
    }

    public void prewarm(String issueKey, IssueChangelogData data) {
        if (issueKey == null || data == null) {
            return;
        }
        changelogCache.put(issueKey, data);
        changelogCacheTs.put(issueKey, Instant.now());
    }

    public CacheStats getStats() {
        return new CacheStats(
                cacheHits.get(),
                cacheMisses.get(),
                cacheLoads.get(),
                cacheEvictions.get(),
                changelogCache.size());
    }

    public record CacheStats(long hits, long misses, long loads, long evictions, int size) {
    }
}
