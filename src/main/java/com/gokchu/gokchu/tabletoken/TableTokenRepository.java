package com.gokchu.gokchu.tabletoken;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TableTokenRepository extends JpaRepository<TableToken, Long> {

    Optional<TableToken> findByToken(UUID token);

    List<TableToken> findByStoreId(Long storeId);
}
