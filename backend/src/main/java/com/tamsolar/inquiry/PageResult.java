package com.tamsolar.inquiry;

import java.util.List;
import java.util.Map;

public record PageResult(List<Map<String, Object>> items, int total, int page, int size) {
}
