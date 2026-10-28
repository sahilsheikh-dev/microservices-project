package com.journal_service.repository;

import com.journal_service.entity.Journal;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public interface JournalRepository extends MongoRepository<Journal, String> {
    Page<Journal> findByUserId(Long userId, Pageable pageable);
}
