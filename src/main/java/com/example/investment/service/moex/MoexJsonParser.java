package com.example.investment.service.moex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Утилита для разбора специфичного формата MOEX ISS.
 *
 * MOEX отдаёт JSON не в виде "массив объектов", а в виде:
 * <pre>
 *   "securities": {
 *     "columns": ["SECID", "SHORTNAME", "PREVPRICE"],
 *     "data":    [["SBER", "Сбербанк", 310.5], ["GAZP", "ГАЗПРОМ", 168.2]]
 *   }
 * </pre>
 *
 * Такой формат эффективен по размеру (имена колонок не повторяются), но
 * неудобен для работы. Этот класс превращает его в обычный
 * {@code List<Map<String, Object>>}.
 *
 * Для студентов: пример реальной задачи — «API отдаёт странный JSON, напиши адаптер».
 */
public class MoexJsonParser {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Парсит один блок из ответа MOEX.
     *
     * @param json      полное тело ответа
     * @param blockName имя блока ("securities", "marketdata", ...)
     * @return список строк-мап: ключ — имя колонки, значение — её содержимое
     */
    public List<Map<String, Object>> parseBlock(String json, String blockName) {
        try {
            JsonNode root = mapper.readTree(json);
            return parseBlock(root, blockName);
        } catch (Exception e) {
            throw new MoexClient.MoexApiException(
                    "Не удалось распарсить JSON блок '" + blockName + "': " + e.getMessage(), e);
        }
    }

    /** Версия для готового дерева (используется в тестах). */
    public List<Map<String, Object>> parseBlock(JsonNode root, String blockName) {
        JsonNode block = root.get(blockName);
        if (block == null || block.isNull()) {
            return List.of();
        }
        JsonNode columns = block.get("columns");
        JsonNode data    = block.get("data");
        if (columns == null || data == null || !columns.isArray() || !data.isArray()) {
            return List.of();
        }

        // Собираем имена колонок в массив — порядок важен
        String[] columnNames = new String[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            columnNames[i] = columns.get(i).asText();
        }

        // Каждая строка data — это массив значений в порядке columns
        List<Map<String, Object>> result = new ArrayList<>(data.size());
        for (JsonNode row : data) {
            // LinkedHashMap сохраняет порядок — удобно для отладки
            Map<String, Object> item = new LinkedHashMap<>();
            for (int i = 0; i < columnNames.length && i < row.size(); i++) {
                item.put(columnNames[i], unwrap(row.get(i)));
            }
            result.add(item);
        }
        return result;
    }

    /** Превращает {@link JsonNode} в простой Java-тип (String/Double/Long/Boolean/null). */
    private Object unwrap(JsonNode node) {
        if (node == null || node.isNull())  return null;
        if (node.isTextual())                return node.asText();
        if (node.isBoolean())                return node.asBoolean();
        if (node.isInt() || node.isLong())   return node.asLong();
        if (node.isDouble() || node.isFloat()) return node.asDouble();
        if (node.isNumber())                 return node.asDouble();
        return node.asText();
    }

    /** Утилита: безопасно достать Double из Map (поддерживает Long/Double/строки). */
    public static double asDouble(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) return 0;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return 0; }
    }

    /** Утилита: безопасно достать String из Map. */
    public static String asString(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? "" : v.toString();
    }
}
