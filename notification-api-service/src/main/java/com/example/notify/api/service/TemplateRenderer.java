package com.example.notify.api.service;

import com.example.notify.api.domain.NotificationTemplate;
import com.example.notify.api.web.error.BadRequestException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Renders "{{variable}}" templates. Values are inserted as plain text (control characters stripped, subject
 * forced to one line). HTML escaping is applied at the output boundary that needs it (the worker escapes when
 * it builds the email HTML; the browser UI uses textContent), so values are never double-escaped.
 */
@Component
public class TemplateRenderer {

    public record Rendered(String subject, String body) { }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z][A-Za-z0-9_]{0,31})\\s*}}");
    private static final Pattern VAR_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,31}$");

    public List<String> variablesOf(NotificationTemplate t) {
        Set<String> names = new LinkedHashSet<>();
        collect(t.getSubjectTemplate(), names);
        collect(t.getBodyTemplate(), names);
        return List.copyOf(names);
    }

    public Rendered render(NotificationTemplate template, Map<String, String> variables) {
        Map<String, String> vars = variables == null ? Map.of() : variables;
        for (String name : vars.keySet()) {
            if (!VAR_NAME.matcher(name).matches()) {
                throw new BadRequestException("Invalid variable name: " + name);
            }
        }
        List<String> missing = new ArrayList<>();
        for (String required : variablesOf(template)) {
            String v = vars.get(required);
            if (v == null || v.isBlank()) {
                missing.add(required);
            }
        }
        if (!missing.isEmpty()) {
            throw new BadRequestException("Missing template variables: " + String.join(", ", missing));
        }
        String subject = substitute(template.getSubjectTemplate(), vars).replaceAll("[\\r\\n]+", " ").trim();
        String body = substitute(template.getBodyTemplate(), vars);
        return new Rendered(subject, body);
    }

    private static void collect(String text, Set<String> into) {
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            into.add(m.group(1));
        }
    }

    private static String substitute(String text, Map<String, String> vars) {
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = vars.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(stripControl(value)));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String stripControl(String value) {
        return value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");
    }
}
