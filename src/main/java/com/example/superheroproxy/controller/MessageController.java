package com.example.superheroproxy.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.example.superheroproxy.service.MessageStorageService;
import com.example.superheroproxy.proto.HeroUpdate;
import java.util.List;

@Controller
@RequestMapping("/messages")
public class MessageController {

    @Autowired
    private MessageStorageService messageStorageService;

    @GetMapping
    public String getMessages(Model model) {
        List<HeroUpdate> messages = messageStorageService.getMessages();
        model.addAttribute("messages", messages);
        return "messages";
    }
} 