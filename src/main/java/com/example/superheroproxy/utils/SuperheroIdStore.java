package com.example.superheroproxy.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.HashMap;
import java.util.Map;

@Component
public final class SuperheroIdStore {
    private static final Logger log = LoggerFactory.getLogger(SuperheroIdStore.class);

    private SuperheroIdStore() {
        // Private constructor to prevent instantiation
    }

    /**
     * Parses superhero IDs and names from HTML content
     * @param htmlContent The HTML content containing the superhero table
     * @return A map of superhero IDs to names
     */
    public static Map<String, String> getSuperheroIds(String htmlContent) {
        Map<String, String> superheroIds = new HashMap<>();
        
        try {
            Document doc = Jsoup.parse(htmlContent);
            Elements tables = doc.select("table.table-striped");

            for (Element table : tables) {

                Elements rows = table.select("tbody tr");
                for (Element row : rows) {
                    Elements cells = row.select("td");
                    if (cells.size() >= 2) {
                        try {
                            String id = cells.get(0).text();
                            String name = cells.get(1).text();

                            superheroIds.put(id, name);


                        } catch (NumberFormatException e) {
                            log.warn("Failed to parse ID from row: {}", row.text());
                        }
                    }
                }
            }
            
            log.info("Successfully parsed {} superhero IDs", superheroIds.size());
        } catch (Exception e) {
            log.error("Error parsing superhero IDs from HTML", e);
        }
        
        return superheroIds;
    }
} 