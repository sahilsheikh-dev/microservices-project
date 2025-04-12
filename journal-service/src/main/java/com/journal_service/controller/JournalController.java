package com.journal_service.controller;

import com.journal_service.entity.Journal;
import com.journal_service.service.JournalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/journals")
@RequiredArgsConstructor
public class JournalController {

    private final JournalService journalService;

    @PostMapping("")
    public ResponseEntity<Journal> createJournal(@RequestBody Journal journal) {
        return new ResponseEntity<>(journalService.createJournal(journal), HttpStatus.CREATED);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("")
    public List<Journal> getAllJournals() {
        return journalService.getAllJournals();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/user/{userId}")
    public List<Journal> getJournalsByUserId(@PathVariable String userId) {
        return journalService.getJournalsByUserId(userId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<Journal> getJournalById(@PathVariable String id) {
        return ResponseEntity.ok(journalService.getJournalById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<Journal> updateJournal(@PathVariable String id, @RequestBody Journal journal) {
        return ResponseEntity.ok(journalService.updateJournal(id, journal));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJournal(@PathVariable String id) {
        journalService.deleteJournal(id);
        return ResponseEntity.noContent().build();
    }

}
