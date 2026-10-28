package com.journal_service.service;

import com.journal_service.entity.Journal;
import com.journal_service.exception.ResourceNotFoundException;
import com.journal_service.repository.JournalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class JournalService {
    private final JournalRepository journalRepository;

    public Journal saveFromEvent(Journal journal) {
        Instant now = Instant.now();
        journal.setCreatedAt(now);
        journal.setUpdatedAt(now);
        return journalRepository.save(journal);
    }

    public Page<Journal> getAllJournals(Pageable pageable) {
        return journalRepository.findAll(pageable);
    }

    public Page<Journal> getJournalsByUserId(Long userId, Pageable pageable) {
        return journalRepository.findByUserId(userId, pageable);
    }

    public Journal getJournalById(String id) {
        return journalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Journal not found."));
    }

    public void deleteJournal(String id) {
        if (!journalRepository.existsById(id)) {
            throw new ResourceNotFoundException("Journal not found.");
        }
        journalRepository.deleteById(id);
    }
}
