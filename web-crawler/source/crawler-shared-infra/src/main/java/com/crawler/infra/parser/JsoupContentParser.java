package com.crawler.infra.parser;

import com.crawler.worker.domain.exception.ParseException;
import com.crawler.worker.domain.service.ContentParser;
import com.crawler.worker.domain.service.ParsedDoc;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class JsoupContentParser implements ContentParser {

    @Override
    public ParsedDoc parse(byte[] body, String sourceUrl) throws ParseException {
        if (body == null || body.length == 0) {
            throw new ParseException(sourceUrl, "empty_body", null);
        }
        String html = new String(body, StandardCharsets.UTF_8);
        Document doc = Jsoup.parse(html, sourceUrl);
        return new ParsedDoc(doc.outerHtml(), "text/html");
    }
}
