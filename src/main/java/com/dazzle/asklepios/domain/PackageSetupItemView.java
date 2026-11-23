package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Read-only view of the setup-owned {@code package_setup_item} table. */
@Entity
@Immutable
@Table(name = "package_setup_item")
@Getter
@NoArgsConstructor
public class PackageSetupItemView {

    @Id
    private Long id;

    @Column(name = "package_setup_id")
    private Long packageSetupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type")
    private BillingItemTypes itemType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "item_code")
    private String itemCode;

    @Column(name = "item_name")
    private String itemName;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "is_active")
    private Boolean isActive;
}
