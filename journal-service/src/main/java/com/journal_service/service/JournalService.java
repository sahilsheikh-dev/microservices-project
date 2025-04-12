package com.journal_service.service;

import com.journal_service.entity.Journal;
import com.journal_service.repository.JournalRepository;
import lombok.RequiredArgsConstructor;
import com.journal_service.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JournalService {

    private final JournalRepository journalRepository;

    public Journal createJournal(Journal journal) {
        journal.setCreatedAt(LocalDateTime.now());
        journal.setUpdatedAt(LocalDateTime.now());
        return journalRepository.save(journal);
    }

    public List<Journal> getAllJournals() {
        return journalRepository.findAll();
    }

    public List<Journal> getJournalsByUserId(String userId) {
        return journalRepository.findByUserId(userId);
    }

    public Journal getJournalById(String id) {
        return journalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Journal not found with id: " + id));
    }

    public Journal updateJournal(String id, Journal journalDetails) {
        Journal journal = getJournalById(id);

        journal.setUserName(journalDetails.getUserName());
        journal.setUserEmail(journalDetails.getUserEmail());
        journal.setUserRole(journalDetails.getUserRole());
        journal.setOperation(journalDetails.getOperation());
        journal.setUserId(journalDetails.getUserId());
        journal.setOperationTime(journalDetails.getOperationTime());
        journal.setUpdatedAt(LocalDateTime.now());
        return journalRepository.save(journal);
    }

    public void deleteJournal(String id) {
        if (!journalRepository.existsById(id)) {
            throw new ResourceNotFoundException("Journal not found with id: " + id);
        }
        journalRepository.deleteById(id);
    }

}
