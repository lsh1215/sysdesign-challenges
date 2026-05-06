package com.crawler.frontier.application;

import com.crawler.frontier.domain.CrawlPriority;
import com.crawler.frontier.domain.Url;
import org.springframework.stereotype.Component;

/**
 * Refines or assigns a {@link CrawlPriority} for an inbound {@link Url}.
 *
 * <p>MVI heuristic: if the caller supplied an explicit hint, honour it. Otherwise default to
 * {@link CrawlPriority#MEDIUM}. The full SDD calls for seeds (depth=0) being elevated to
 * {@link CrawlPriority#HIGH}, but depth tracking is out of MVI scope (no depth field on
 * {@link Url}); operators can pass {@code HIGH} explicitly when posting seeds.
 */
@Component
public class Prioritizer {

    public CrawlPriority refine(Url url, CrawlPriority requestedHint) {
        if (requestedHint != null) {
            return requestedHint;
        }
        return CrawlPriority.MEDIUM;
    }
}
