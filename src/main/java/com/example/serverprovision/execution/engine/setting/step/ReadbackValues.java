package com.example.serverprovision.execution.engine.setting.step;

import tools.jackson.databind.JsonNode;

/** readback 대조의 값 정규화 — BMC 는 JSON 노드로, 원장 목표는 원시값으로 오므로 둘을 같은 문자열 꼴로 맞춘다. */
final class ReadbackValues {

    private ReadbackValues() {
    }

    static String normalize(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.numberValue().toString();
        }
        if (node.isBoolean()) {
            return Boolean.toString(node.asBoolean());
        }
        return node.asString();
    }

    static String normalize(Object value) {
        return value == null ? null : value.toString();
    }

    static boolean same(JsonNode actual, Object expected) {
        String a = normalize(actual);
        String e = normalize(expected);
        return a != null && a.equals(e);
    }
}
