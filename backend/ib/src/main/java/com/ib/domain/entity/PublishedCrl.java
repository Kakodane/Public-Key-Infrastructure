package com.ib.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "published_crls")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class PublishedCrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ca_certificate_id", nullable = false, unique = true)
    @ToString.Exclude
    private Certificate caCertificate;

    @Column(name = "crl_number", nullable = false)
    @ToString.Include
    private long crlNumber;

    @Column(name = "this_update", nullable = false)
    private Instant thisUpdate;

    @Column(name = "next_update", nullable = false)
    private Instant nextUpdate;

    @Column(name = "data", nullable = false, columnDefinition = "bytea")
    @ToString.Exclude
    private byte[] data;
}
