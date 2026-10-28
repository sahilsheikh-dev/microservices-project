package com.journal_service.controller;

import com.journal_service.dto.JournalResponse;
import com.journal_service.dto.PageResponse;
import com.journal_service.entity.Journal;
import com.journal_service.service.JournalService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/journals")
@RequiredArgsConstructor
public class JournalController {
    private final JournalService journalService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public PageResponse<JournalResponse> getAllJournals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "operationTime,desc") String sort) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), parseSort(sort));
        Page<Journal> journals = journalService.getAllJournals(pageable);
        return PageResponse.from(journals, JournalResponse::from);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/user/{userId}")
    public PageResponse<JournalResponse> getJournalsByUserId(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "operationTime,desc") String sort) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), parseSort(sort));
        return PageResponse.from(journalService.getJournalsByUserId(userId, pageable), JournalResponse::from);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<JournalResponse> getJournalById(@PathVariable String id) {
        return ResponseEntity.ok(JournalResponse.from(journalService.getJournalById(id)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJournal(@PathVariable String id) {
        journalService.deleteJournal(id);
        return ResponseEntity.noContent().build();
    }

    private Sort parseSort(String value) {
        String[] parts = value.split(",", 2);
        Sort.Direction direction = parts.length == 2 ? Sort.Direction.fromString(parts[1]) : Sort.Direction.ASC;
        return Sort.by(direction, parts[0]);
    }
}
