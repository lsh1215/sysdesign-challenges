package com.crawler.frontier.application;

import com.crawler.frontier.domain.Url;
import com.crawler.frontier.domain.repository.FrontierRepository;
import com.crawler.frontier.domain.repository.UrlSeenIndex;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FrontierService {

    private final FrontierRepository frontierRepository;
    private final UrlSeenIndex urlSeenIndex;
    private final Prioritizer prioritizer;

    public EnqueueResult enqueue(Url url) {
        boolean newlyMarked = urlSeenIndex.markIfAbsent(url.getUrl());
        if (!newlyMarked) {
            return EnqueueResult.DUPLICATE;
        }
        Url prioritized = Url.newUrl(
                url.getUrl(),
                prioritizer.refine(url, url.getPriority()),
                url.getDiscoveredAt()
        );
        frontierRepository.enqueue(prioritized);
        return EnqueueResult.QUEUED;
    }

    public Optional<Url> pollNext() {
        return frontierRepository.dequeueNext();
    }
}
