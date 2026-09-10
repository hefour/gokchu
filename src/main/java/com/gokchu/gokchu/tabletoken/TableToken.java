package com.gokchu.gokchu.tabletoken;

import com.gokchu.gokchu.store.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "table_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TableToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID token;

    @Column(nullable = false)
    private String label;

    @Builder
    public TableToken(Store store, String label) {
        this.store = store;
        this.label = label;
        this.token = UUID.randomUUID();
    }
}
