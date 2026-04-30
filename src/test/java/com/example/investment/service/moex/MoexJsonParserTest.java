package com.example.investment.service.moex;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты парсера MOEX ISS.
 *
 * MOEX отдаёт блоки вида {@code {"columns": [...], "data": [[...], [...]]}}.
 * Наша задача — убедиться, что парсер превращает их в привычные Map-ы.
 */
class MoexJsonParserTest {

    private final MoexJsonParser parser = new MoexJsonParser();

    @Test
    void parsesSimpleBlock() {
        String json = """
            {
              "securities": {
                "columns": ["SECID", "SHORTNAME", "PREVPRICE"],
                "data": [
                  ["SBER", "Сбербанк", 310.5],
                  ["GAZP", "ГАЗПРОМ",  168.2]
                ]
              }
            }
            """;
        List<Map<String, Object>> rows = parser.parseBlock(json, "securities");
        assertEquals(2, rows.size());
        assertEquals("SBER",     rows.get(0).get("SECID"));
        assertEquals("Сбербанк", rows.get(0).get("SHORTNAME"));
        assertEquals(310.5,      MoexJsonParser.asDouble(rows.get(0), "PREVPRICE"));
        assertEquals("GAZP",     rows.get(1).get("SECID"));
    }

    @Test
    void missingBlockReturnsEmpty() {
        String json = """
            { "securities": { "columns": ["A"], "data": [["x"]] } }
            """;
        assertTrue(parser.parseBlock(json, "marketdata").isEmpty());
    }

    @Test
    void handlesNullsAndMixedTypes() {
        String json = """
            {
              "marketdata": {
                "columns": ["SECID", "LAST", "FLAG"],
                "data": [
                  ["SBER", 311.0, true],
                  ["YNDX", null,  false]
                ]
              }
            }
            """;
        List<Map<String, Object>> rows = parser.parseBlock(json, "marketdata");
        assertEquals(311.0, MoexJsonParser.asDouble(rows.get(0), "LAST"));
        assertEquals(0.0,   MoexJsonParser.asDouble(rows.get(1), "LAST"), "null → 0");
        assertEquals(true,  rows.get(0).get("FLAG"));
    }

    @Test
    void asStringHandlesNulls() {
        Map<String, Object> row = Map.of();
        assertEquals("", MoexJsonParser.asString(row, "NOPE"));
    }
}
