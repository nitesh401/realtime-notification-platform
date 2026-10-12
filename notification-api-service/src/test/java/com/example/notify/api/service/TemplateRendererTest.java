package com.example.notify.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.notify.api.domain.NotificationTemplate;
import com.example.notify.api.web.error.BadRequestException;
import com.example.notify.common.Category;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer();
    private final NotificationTemplate template = new NotificationTemplate("T", "Hi {{name}}",
            "Order {{ orderId }} for {{name}}", Category.INFO, false);

    @Test
    void rendersAllPlaceholders() {
        TemplateRenderer.Rendered r = renderer.render(template, Map.of("name", "Alice", "orderId", "A-1"));
        assertThat(r.subject()).isEqualTo("Hi Alice");
        assertThat(r.body()).isEqualTo("Order A-1 for Alice");
    }

    @Test
    void reportsEveryMissingVariable() {
        assertThatThrownBy(() -> renderer.render(template, Map.of()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("name")
                .hasMessageContaining("orderId");
    }

    @Test
    void blankValueCountsAsMissing() {
        assertThatThrownBy(() -> renderer.render(template, Map.of("name", " ", "orderId", "1")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void regexSpecialCharactersInValuesAreInsertedLiterally() {
        TemplateRenderer.Rendered r = renderer.render(template, Map.of("name", "$1\\n", "orderId", "x"));
        assertThat(r.subject()).isEqualTo("Hi $1\\n");
    }

    @Test
    void subjectCannotContainLineBreaksAndControlCharsAreStripped() {
        TemplateRenderer.Rendered r = renderer.render(template, Map.of("name", "Eve\r\nBcc: x", "orderId", "1\u0007"));
        assertThat(r.subject()).doesNotContain("\n").doesNotContain("\r");
        assertThat(r.body()).doesNotContain("\u0007");
    }

    @Test
    void invalidVariableNamesAreRejected() {
        assertThatThrownBy(() -> renderer.render(template, Map.of("name", "a", "orderId", "b", "bad name!", "c")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void exposesVariableNamesInOrder() {
        assertThat(renderer.variablesOf(template)).containsExactly("name", "orderId");
    }
}
