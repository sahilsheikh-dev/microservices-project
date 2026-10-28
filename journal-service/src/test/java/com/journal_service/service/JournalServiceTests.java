package com.journal_service.service;

import com.journal_service.entity.Journal;
import com.journal_service.exception.ResourceNotFoundException;
import com.journal_service.repository.JournalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JournalServiceTests {
    @Mock
    private JournalRepository journalRepository;
    @InjectMocks
    private JournalService journalService;

    @Test
    void saveFromEvent_newJournal_setsAuditTimesAndPersists() {
        Journal journal = Journal.builder().operation("CREATE").build();
        when(journalRepository.save(journal)).thenReturn(journal);

        Journal saved = journalService.saveFromEvent(journal);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
        verify(journalRepository).save(journal);
    }

    @Test
    void getAllJournals_pageRequested_delegatesToRepository() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Journal> expected = new PageImpl<>(List.of(Journal.builder().id("1").build()));
        when(journalRepository.findAll(pageable)).thenReturn(expected);

        assertThat(journalService.getAllJournals(pageable)).isSameAs(expected);
    }

    @Test
    void getJournalsByUserId_userRequested_delegatesToRepository() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Journal> expected = new PageImpl<>(List.of());
        when(journalRepository.findByUserId(12L, pageable)).thenReturn(expected);

        assertThat(journalService.getJournalsByUserId(12L, pageable)).isSameAs(expected);
    }

    @Test
    void getJournalById_existingJournal_returnsJournal() {
        Journal journal = Journal.builder().id("abc").build();
        when(journalRepository.findById("abc")).thenReturn(Optional.of(journal));

        assertThat(journalService.getJournalById("abc")).isSameAs(journal);
    }

    @Test
    void getJournalById_missingJournal_throwsNotFound() {
        when(journalRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> journalService.getJournalById("missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Journal not found.");
    }

    @Test
    void deleteJournal_existingJournal_deletesById() {
        when(journalRepository.existsById("abc")).thenReturn(true);

        journalService.deleteJournal("abc");

        verify(journalRepository).deleteById("abc");
    }

    @Test
    void deleteJournal_missingJournal_throwsNotFoundWithoutDelete() {
        when(journalRepository.existsById("missing")).thenReturn(false);

        assertThatThrownBy(() -> journalService.deleteJournal("missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Journal not found.");
        verify(journalRepository, never()).deleteById(anyString());
    }
}
