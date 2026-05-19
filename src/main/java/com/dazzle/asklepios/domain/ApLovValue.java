package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "ap_lov_values")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApLovValue implements Serializable {

    @Id
    @Column(name = "key", nullable = false)
    private String key;

    @Column(name = "lov_key")
    private String lovKey;

    @Column(name = "lov_code")
    private String lovCode;

    @Column(name = "value_code")
    private String valueCode;

    @Column(name = "lov_display_vale")
    private String lovDisplayVale;

    @Column(name = "love_custom_code")
    private String loveCustomCode;

    @Column(name = "value_description")
    private String valueDescription;

    @Column(name = "value_color")
    private String valueColor;

    @Column(name = "value_icon")
    private String valueIcon;

    @Column(name = "value_order")
    private BigDecimal valueOrder;

    @Column(name = "isdefault")
    private Boolean isDefault;

    @Column(name = "seeded_data")
    private Boolean seededData;

    @Column(name = "for_internal_user")
    private Boolean forInternalUser;

    @Column(name = "specific_for_screen_id")
    private String specificForScreenId;

    @Column(name = "parent_value_id")
    private String parentValueId;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "deleted_by")
    private String deletedBy;

    @Column(name = "created_at", precision = 16)
    private BigDecimal createdAt;

    @Column(name = "updated_at", precision = 16)
    private BigDecimal updatedAt;

    @Column(name = "deleted_at", precision = 16)
    private BigDecimal deletedAt;

    @Column(name = "is_valid")
    private Boolean isValid;

    @Column(name = "score")
    private BigDecimal score;
}