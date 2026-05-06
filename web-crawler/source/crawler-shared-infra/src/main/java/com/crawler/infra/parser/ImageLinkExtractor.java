package com.crawler.infra.parser;

import com.crawler.worker.domain.service.LinkExtractor;
import com.crawler.worker.domain.service.ParsedDoc;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/** Extracts {@code <img src>} URLs. Demonstrates plug-in extensibility (G-5). */
@Component
public class ImageLinkExtractor implements LinkExtractor {

    @Override
    public List<String> extract(ParsedDoc doc, String sourceUrl) {
        Document parsed = Jsoup.parse(doc.html(), sourceUrl);
        URI base;
        try {
            base = new URI(sourceUrl);
        } catch (URISyntaxException e) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Element img : parsed.select("img[src]")) {
            String src = img.attr("src").trim();
            if (src.isEmpty()) {
                continue;
            }
            String resolved = resolve(base, src);
            if (resolved != null) {
                out.add(resolved);
            }
        }
        return out;
    }

    private String resolve(URI base, String src) {
        try {
            return base.resolve(src).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
