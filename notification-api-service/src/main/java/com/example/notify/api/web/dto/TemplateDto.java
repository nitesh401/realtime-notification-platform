package com.example.notify.api.web.dto;

import com.example.notify.common.Category;
import java.util.List;

public record TemplateDto(String id, String subject, String body, Category category, boolean mandatory,
                          List<String> variables) { }
