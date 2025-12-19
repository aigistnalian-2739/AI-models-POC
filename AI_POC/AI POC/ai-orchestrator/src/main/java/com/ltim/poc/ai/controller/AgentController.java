package com.ltim.poc.ai.controller;

import com.ltim.poc.ai.service.CodeAnalystService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class AgentController {

    private final CodeAnalystService service;

    public AgentController(CodeAnalystService service) { this.service = service; }

    @GetMapping("/") public String index() { return "index"; }

    @PostMapping("/api/analyze")
    @ResponseBody
// Change @RequestParam String path to @RequestParam("path") String path
    public String analyze(@RequestParam("path") String path) {
        return service.runFullPipeline(path);
    }

    @PostMapping("/api/audit")
    @ResponseBody
// Change @RequestParam String path to @RequestParam("path") String path
    public String audit(@RequestParam("path") String path) {
        return service.runAudit(path);
    }
}