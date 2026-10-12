package com.example.notify.api.service;

import com.example.notify.api.domain.NotificationTemplateRepository;
import com.example.notify.api.web.dto.TemplateDto;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateService {

    private final NotificationTemplateRepository repository;
    private final TemplateRenderer renderer;

    public TemplateService(NotificationTemplateRepository repository, TemplateRenderer renderer) {
        this.repository = repository;
        this.renderer = renderer;
    }

    @Transactional(readOnly = true)
    public List<TemplateDto> list() {
        return repository.findAll().stream()
                .map(t -> new TemplateDto(t.getId(), t.getSubjectTemplate(), t.getBodyTemplate(), t.getCategory(),
                        t.isMandatory(), renderer.variablesOf(t)))
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .toList();
    }
}
