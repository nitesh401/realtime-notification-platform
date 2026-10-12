package com.example.notify.api.web.controller;

import com.example.notify.api.service.TemplateService;
import com.example.notify.api.web.dto.TemplateDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/templates")
public class TemplateController {

    private final TemplateService service;

    public TemplateController(TemplateService service) {
        this.service = service;
    }

    @GetMapping
    public List<TemplateDto> list() {
        return service.list();
    }
}
