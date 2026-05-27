package com.example.Capstone.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/reservations/call-providers/clawops/inbound")
public class ClawOpsInboundWebhookController {

    private static final String VOICE_RESPONSE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Response>
              <Say language="ko-KR">연결 확인되었습니다.</Say>
              <Hangup/>
            </Response>
            """;

    @GetMapping(produces = MediaType.TEXT_XML_VALUE)
    public ResponseEntity<String> receiveInboundCallByGet() {
        return voiceResponse();
    }

    @PostMapping(produces = MediaType.TEXT_XML_VALUE)
    public ResponseEntity<String> receiveInboundCallByPost() {
        return voiceResponse();
    }

    private ResponseEntity<String> voiceResponse() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_XML)
                .body(VOICE_RESPONSE);
    }
}
